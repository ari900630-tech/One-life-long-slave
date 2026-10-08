package com.arilifelong.agent;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.Locale;

public class VoiceEngine implements TextToSpeech.OnInitListener {
    public interface Listener { void onText(String text); void onState(String state); }

    private final Context context;
    private final Listener listener;
    private TextToSpeech tts;
    private boolean ttsReady=false;
    private String pendingSpeech;
    private Runnable pendingSpeechCallback;
    private final java.util.Map<String,Runnable> speechCallbacks=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong speechSequence=new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean recording=false;
    private static final java.util.concurrent.atomic.AtomicBoolean MIC_IN_USE=new java.util.concurrent.atomic.AtomicBoolean(false);
    private AudioRecord recorder;
    private Thread recordThread;

    private static final int SAMPLE_RATE=16000;
    private static final int CHANNEL=AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING=AudioFormat.ENCODING_PCM_16BIT;
    private static final int MAX_RECORD_MS=15000;
    private static final int INITIAL_SILENCE_MS=12000;
    private static final int END_SILENCE_MS=900;
    private static final int VAD_CALIBRATION_MS=450;
    private static final int MIN_SPEECH_MS=220;
    private static final double MIN_SPEECH_RMS=650.0;

    public VoiceEngine(Context c, Listener l){
        context=c.getApplicationContext();
        RuntimeLogger.log(context,"VOICE_ENGINE","created");
        listener=l;
        tts=new TextToSpeech(context,this);
        if(tts!=null)tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){RuntimeLogger.log(context,"TTS","started id="+id);if(listener!=null)listener.onState("הסוכן מדבר...");}
            @Override public void onDone(String id){RuntimeLogger.log(context,"TTS","done id="+id);
                if(listener!=null)listener.onState("הסוכן סיים לדבר");
                Runnable r=speechCallbacks.remove(id);
                if(r!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
            }
            @Override public void onError(String id){RuntimeLogger.log(context,"TTS_ERROR","id="+id);
                if(listener!=null)listener.onState("שגיאה בהשמעה קולית");
                speechCallbacks.remove(id);
            }
        });
    }

    public boolean isRecording(){ return recording; }

    public boolean startListening(){
        RuntimeLogger.log(context,"MIC","startListening");
        if(recording)return true;
        if(android.os.Build.VERSION.SDK_INT>=23 &&
           context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            if(listener!=null)listener.onState("שגיאת מיקרופון: אין הרשאת מיקרופון");
            return false;
        }
        int min=AudioRecord.getMinBufferSize(SAMPLE_RATE,CHANNEL,ENCODING);
        if(min<=0){
            if(listener!=null)listener.onState("שגיאת מיקרופון: המכשיר לא תומך בהקלטה");
            return false;
        }
        int buffer=Math.max(min,SAMPLE_RATE/2);
        if(!MIC_IN_USE.compareAndSet(false,true)){
            RuntimeLogger.log(context,"MIC_BUSY","another VoiceEngine is already recording");
            if(listener!=null)listener.onState("המיקרופון כבר בשימוש. מנסה שוב...");
            return false;
        }
        int[] sources;
        if(android.os.Build.VERSION.SDK_INT>=21){
            sources=new int[]{MediaRecorder.AudioSource.VOICE_RECOGNITION,MediaRecorder.AudioSource.VOICE_COMMUNICATION,MediaRecorder.AudioSource.DEFAULT,MediaRecorder.AudioSource.MIC};
        }else{
            sources=new int[]{MediaRecorder.AudioSource.DEFAULT,MediaRecorder.AudioSource.MIC};
        }
        try{
            recorder=null;
            for(int source:sources){
                try{
                    RuntimeLogger.log(context,"MIC_OPEN_ATTEMPT","source="+source+" buffer="+buffer);
                    AudioRecord candidate=new AudioRecord(source,SAMPLE_RATE,CHANNEL,ENCODING,buffer);
                    if(candidate.getState()==AudioRecord.STATE_INITIALIZED){
                        recorder=candidate;
                        RuntimeLogger.log(context,"MIC_OPEN_OK","source="+source);
                        break;
                    }
                    RuntimeLogger.log(context,"MIC_OPEN_FAIL","source="+source+" state="+candidate.getState());
                    try{candidate.release();}catch(Exception ignored){}
                }catch(Exception e){
                    RuntimeLogger.log(context,"MIC_OPEN_EXCEPTION","source="+source+" error="+e);
                }
            }
            if(recorder==null){
                MIC_IN_USE.set(false);
                if(listener!=null)listener.onState("שגיאת מיקרופון: לא ניתן לפתוח את המיקרופון. ייתכן שהמיקרופון בשימוש באפליקציה אחרת.");
                return false;
            }
            try{
                recorder.startRecording();
                if(recorder.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING){
                    RuntimeLogger.log(context,"MIC_START_FAIL","recordingState="+recorder.getRecordingState());
                    try{recorder.release();}catch(Exception ignored){}
                    recorder=null;
                    MIC_IN_USE.set(false);
                    if(listener!=null)listener.onState("שגיאת מיקרופון: ההקלטה לא התחילה");
                    return false;
                }
            }catch(Exception e){
                RuntimeLogger.log(context,"MIC_START_EXCEPTION","error="+e);
                try{recorder.release();}catch(Exception ignored){}
                recorder=null;
                MIC_IN_USE.set(false);
                if(listener!=null)listener.onState("שגיאת מיקרופון: "+e.getClass().getSimpleName());
                return false;
            }
            recording=true;
            if(listener!=null)listener.onState("מאזין...");
            RuntimeLogger.log(context,"MIC","recording_started");
            recordThread=new Thread(()->recordLoop(buffer),"AgentAudioRecorder");
            recordThread.start();
            return true;
        }catch(Exception e){
            recording=false;
            if(recorder!=null){try{recorder.release();}catch(Exception ignored){} recorder=null;}
            MIC_IN_USE.set(false);
            RuntimeLogger.log(context,"MIC_OPEN_FATAL","error="+e);
            if(listener!=null)listener.onState("שגיאת מיקרופון: "+e.getClass().getSimpleName());
            return false;
        }
    }

    private void recordLoop(int bufferSize){
        ByteArrayOutputStream pcm=new ByteArrayOutputStream();
        short[] samples=new short[bufferSize/2];
        long started=System.currentTimeMillis();
        long speechAt=0;
        long lastSpeech=0;
        double noiseSum=0;
        int noiseSamples=0;
        double speechThreshold=MIN_SPEECH_RMS;
        try{
            while(recording && System.currentTimeMillis()-started<MAX_RECORD_MS){
                int n=recorder.read(samples,0,samples.length);
                if(n<=0){RuntimeLogger.log(context,"MIC_READ","read="+n);continue;}
                byte[] bytes=new byte[n*2];
                double sum=0;
                for(int i=0;i<n;i++){
                    short sample=samples[i];
                    sum+=(double)sample*sample;
                    bytes[i*2]=(byte)(sample&0xff);
                    bytes[i*2+1]=(byte)((sample>>8)&0xff);
                }
                pcm.write(bytes,0,bytes.length);
                double rms=Math.sqrt(sum/n);
                long now=System.currentTimeMillis();

                if(now-started<=VAD_CALIBRATION_MS && speechAt==0){
                    noiseSum+=rms;
                    noiseSamples++;
                    double noiseFloor=noiseSamples>0?noiseSum/noiseSamples:0;
                    speechThreshold=Math.max(MIN_SPEECH_RMS,Math.min(2200.0,noiseFloor*2.0));
                    RuntimeLogger.log(context,"MIC_CALIBRATION",
                            "rms="+String.format(Locale.US,"%.1f",rms)+
                            " noise="+String.format(Locale.US,"%.1f",noiseFloor)+
                            " threshold="+String.format(Locale.US,"%.1f",speechThreshold));
                    continue;
                }

                if(rms>=speechThreshold){
                    if(speechAt==0){
                        speechAt=now;
                        RuntimeLogger.log(context,"MIC_SPEECH_START",
                                "rms="+String.format(Locale.US,"%.1f",rms)+
                                " threshold="+String.format(Locale.US,"%.1f",speechThreshold));
                    }
                    lastSpeech=now;
                    if(listener!=null)listener.onState("מקשיב...");
                }else if(speechAt>0 && now-lastSpeech>=END_SILENCE_MS){
                    if(now-speechAt>=MIN_SPEECH_MS)break;
                    RuntimeLogger.log(context,"MIC_FALSE_START",
                            "speech duration="+(now-speechAt)+"ms below minimum");
                    speechAt=0;
                    lastSpeech=0;
                }else if(speechAt==0 && now-started>=INITIAL_SILENCE_MS){
                    RuntimeLogger.log(context,"MIC_NO_SPEECH",
                            "no speech detected after "+INITIAL_SILENCE_MS+
                            "ms; threshold="+String.format(Locale.US,"%.1f",speechThreshold)+
                            " pcmBytes="+pcm.size());
                    if(listener!=null)listener.onState("לא שמעתי דיבור. נסה שוב.");
                    return;
                }
            }
            if(speechAt==0){
                RuntimeLogger.log(context,"MIC_NO_SPEECH",
                        "recording ended without speech; pcmBytes="+pcm.size());
                if(listener!=null)listener.onState("לא שמעתי דיבור. נסה שוב.");
                return;
            }
            if(listener!=null)listener.onState("מעבד את הדיבור...");
            RuntimeLogger.log(context,"MIC","speech_detected; sending_transcription");
            File wav=new File(context.getCacheDir(),"agent_speech_"+System.currentTimeMillis()+".wav");
            writeWav(wav,pcm.toByteArray());
            ApiClient.transcribe(wav,new ApiClient.Callback(){
                @Override public void success(org.json.JSONObject result){
                    String text=result.optString("text","").trim();
                    RuntimeLogger.log(context,"STT_RESULT","text="+text);
                    if(text.isEmpty()){
                        RuntimeLogger.log(context,"STT_EMPTY","transcription returned empty");
                        if(listener!=null)listener.onState("לא הצלחתי להבין מה נאמר. נסה שוב.");
                    } else if(listener!=null)listener.onText(text);
                }
                @Override public void error(String message){
                    RuntimeLogger.log(context,"STT_ERROR",message);
                    if(listener!=null)listener.onState("לא הצלחתי לתמלל את הדיבור: "+message);
                }
            });
        }catch(Exception e){
            RuntimeLogger.log(context,"MIC_EXCEPTION","error="+e);
            if(listener!=null)listener.onState("שגיאת מיקרופון: "+e.getClass().getSimpleName());
        }finally{
            stopRecorder();
        }
    }

    private void writeWav(File file,byte[] pcm)throws Exception{
        try(FileOutputStream out=new FileOutputStream(file)){
            int dataLen=pcm.length;
            int totalLen=dataLen+36;
            out.write(new byte[]{'R','I','F','F'});
            writeInt(out,totalLen);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            writeInt(out,16); writeShort(out,(short)1); writeShort(out,(short)1);
            writeInt(out,SAMPLE_RATE); writeInt(out,SAMPLE_RATE*2);
            writeShort(out,(short)2); writeShort(out,(short)16);
            out.write(new byte[]{'d','a','t','a'}); writeInt(out,dataLen);
            out.write(pcm);
        }
    }
    private void writeInt(FileOutputStream o,int v)throws Exception{ o.write(v&255);o.write((v>>8)&255);o.write((v>>16)&255);o.write((v>>24)&255); }
    private void writeShort(FileOutputStream o,short v)throws Exception{ o.write(v&255);o.write((v>>8)&255); }

    private void stopRecorder(){
        recording=false;
        if(recorder!=null){
            try{if(recorder.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)recorder.stop();}catch(Exception ignored){}
            try{recorder.release();}catch(Exception ignored){}
            recorder=null;
        }
        MIC_IN_USE.set(false);
    }

    public void speak(String text){speak(text,null);}
    public void speak(String text,Runnable afterSpeech){
        RuntimeLogger.log(context,"TTS_REQUEST","text="+(text==null?"":text));
        if(text==null||text.trim().isEmpty())return;
        pendingSpeech=text.trim();
        pendingSpeechCallback=afterSpeech;
        if(!ttsReady||tts==null)return;
        speakNow(pendingSpeech,afterSpeech);
        pendingSpeech=null; pendingSpeechCallback=null;
    }

    private void speakNow(String text,Runnable afterSpeech){
        if(tts==null||text==null||text.isEmpty())return;
        String utteranceId="agent-he-"+speechSequence.incrementAndGet();
        speechCallbacks.clear();
        if(afterSpeech!=null)speechCallbacks.put(utteranceId,afterSpeech);
        int result=tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,utteranceId);
        RuntimeLogger.log(context,"TTS_SUBMIT","result="+result+" id="+utteranceId);
        if(result==TextToSpeech.ERROR&&listener!=null)listener.onState("שגיאה בהשמעה קולית");
    }

    public void destroy(){
        stopRecorder();
        if(tts!=null){tts.stop();tts.shutdown();tts=null;}
        ttsReady=false;
    }

    @Override public void onInit(int status){
        if(status!=TextToSpeech.SUCCESS||tts==null){
            ttsReady=false;if(listener!=null)listener.onState("מנוע הדיבור לא זמין");return;
        }
        Locale hebrew=new Locale("he","IL");
        int languageStatus=tts.setLanguage(hebrew);
        if(languageStatus==TextToSpeech.LANG_MISSING_DATA||languageStatus==TextToSpeech.LANG_NOT_SUPPORTED)
            languageStatus=tts.setLanguage(new Locale("he"));
        tts.setSpeechRate(0.92f);tts.setPitch(1.0f);
        ttsReady=languageStatus!=TextToSpeech.LANG_MISSING_DATA&&languageStatus!=TextToSpeech.LANG_NOT_SUPPORTED;
        RuntimeLogger.log(context,"TTS_INIT","status="+status+" languageStatus="+languageStatus+" ready="+ttsReady);
        if(!ttsReady){
            int fallback=tts.setLanguage(Locale.getDefault());
            ttsReady=fallback!=TextToSpeech.LANG_MISSING_DATA&&fallback!=TextToSpeech.LANG_NOT_SUPPORTED;
            if(!ttsReady){
                if(listener!=null)listener.onState("מנוע הדיבור לא זמין");
                return;
            }
        }
        if(pendingSpeech!=null){
            String text=pendingSpeech;Runnable cb=pendingSpeechCallback;
            pendingSpeech=null;pendingSpeechCallback=null;speakNow(text,cb);
        }
    }
}
