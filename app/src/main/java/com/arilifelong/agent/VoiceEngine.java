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
    private AudioRecord recorder;
    private Thread recordThread;

    private static final int SAMPLE_RATE=16000;
    private static final int CHANNEL=AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING=AudioFormat.ENCODING_PCM_16BIT;
    private static final int MAX_RECORD_MS=15000;
    private static final int INITIAL_SILENCE_MS=8000;
    private static final int END_SILENCE_MS=1200;
    private static final double SPEECH_RMS=450.0;

    public VoiceEngine(Context c, Listener l){
        context=c.getApplicationContext();
        listener=l;
        tts=new TextToSpeech(context,this);
        if(tts!=null)tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){if(listener!=null)listener.onState("הסוכן מדבר...");}
            @Override public void onDone(String id){
                if(listener!=null)listener.onState("הסוכן סיים לדבר");
                Runnable r=speechCallbacks.remove(id);
                if(r!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
            }
            @Override public void onError(String id){
                if(listener!=null)listener.onState("שגיאה בהשמעה קולית");
                speechCallbacks.remove(id);
            }
        });
    }

    public boolean startListening(){
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
        try{
            recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,SAMPLE_RATE,CHANNEL,ENCODING,buffer);
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){
                recorder.release();
                recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,SAMPLE_RATE,CHANNEL,ENCODING,buffer);
            }
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){
                recorder.release(); recorder=null;
                if(listener!=null)listener.onState("שגיאת מיקרופון: לא ניתן לפתוח את המיקרופון");
                return false;
            }
            recording=true;
            recorder.startRecording();
            if(listener!=null)listener.onState("מאזין...");
            recordThread=new Thread(()->recordLoop(buffer),"AgentAudioRecorder");
            recordThread.start();
            return true;
        }catch(Exception e){
            recording=false;
            if(recorder!=null){try{recorder.release();}catch(Exception ignored){} recorder=null;}
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
        try{
            while(recording && System.currentTimeMillis()-started<MAX_RECORD_MS){
                int n=recorder.read(samples,0,samples.length);
                if(n<=0)continue;
                byte[] bytes=new byte[n*2];
                double sum=0;
                for(int i=0;i<n;i++){
                    short s=samples[i];
                    sum+=(double)s*s;
                    bytes[i*2]=(byte)(s&0xff);
                    bytes[i*2+1]=(byte)((s>>8)&0xff);
                }
                pcm.write(bytes,0,bytes.length);
                double rms=Math.sqrt(sum/n);
                long now=System.currentTimeMillis();
                if(rms>=SPEECH_RMS){
                    if(speechAt==0)speechAt=now;
                    lastSpeech=now;
                    if(listener!=null)listener.onState("מקשיב...");
                }else if(speechAt>0 && now-lastSpeech>=END_SILENCE_MS){
                    break;
                }else if(speechAt==0 && now-started>=INITIAL_SILENCE_MS){
                    if(listener!=null)listener.onState("שגיאת מיקרופון: לא התחלת לדבר");
                    return;
                }
            }
            if(speechAt==0){
                if(listener!=null)listener.onState("שגיאת מיקרופון: לא זוהה דיבור");
                return;
            }
            if(listener!=null)listener.onState("מעבד את הדיבור...");
            File wav=new File(context.getCacheDir(),"agent_speech_"+System.currentTimeMillis()+".wav");
            writeWav(wav,pcm.toByteArray());
            ApiClient.transcribe(wav,new ApiClient.Callback(){
                @Override public void success(org.json.JSONObject result){
                    String text=result.optString("text","").trim();
                    if(text.isEmpty()){if(listener!=null)listener.onState("שגיאת מיקרופון: לא זוהה דיבור");}
                    else if(listener!=null)listener.onText(text);
                }
                @Override public void error(String message){if(listener!=null)listener.onState("שגיאת מיקרופון: "+message);}
            });
        }catch(Exception e){
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
    }

    public void speak(String text){speak(text,null);}
    public void speak(String text,Runnable afterSpeech){
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
        if(afterSpeech!=null)speechCallbacks.put(utteranceId,afterSpeech);
        int result=tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,utteranceId);
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
