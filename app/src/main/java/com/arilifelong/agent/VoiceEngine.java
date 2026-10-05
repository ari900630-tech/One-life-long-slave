package com.arilifelong.agent;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import java.util.ArrayList;
import java.util.Locale;

public class VoiceEngine implements RecognitionListener, TextToSpeech.OnInitListener {
    public interface Listener { void onText(String text); void onState(String state); }
    private final Context context;
    private final Listener listener;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;

    public VoiceEngine(Context c,Listener l){ context=c.getApplicationContext(); listener=l; tts=new TextToSpeech(context,this); }
    public boolean startListening(){
        if(!SpeechRecognizer.isRecognitionAvailable(context))return false;
        if(recognizer!=null)recognizer.destroy();
        recognizer=SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(this);
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"he-IL");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"he-IL");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        recognizer.startListening(i);
        if(listener!=null)listener.onState("מאזין...");
        return true;
    }
    public void speak(String text){
        if(tts!=null && text!=null)tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"agent-he");
    }
    public void destroy(){ if(recognizer!=null)recognizer.destroy(); if(tts!=null)tts.shutdown(); }
    @Override public void onInit(int status){ if(status==TextToSpeech.SUCCESS){ tts.setLanguage(new Locale("he","IL")); } }
    @Override public void onResults(Bundle b){ ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(listener!=null&&r!=null&&!r.isEmpty())listener.onText(r.get(0)); if(listener!=null)listener.onState("מוכן"); }
    @Override public void onError(int e){ if(listener!=null)listener.onState("לא זוהה קול"); }
    @Override public void onReadyForSpeech(Bundle b){} @Override public void onBeginningOfSpeech(){} @Override public void onRmsChanged(float v){}
    @Override public void onBufferReceived(byte[] b){} @Override public void onEndOfSpeech(){} @Override public void onPartialResults(Bundle b){}
    @Override public void onEvent(int t,Bundle b){}
}