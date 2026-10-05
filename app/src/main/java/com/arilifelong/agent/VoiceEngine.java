package com.arilifelong.agent;

import android.content.Context;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import java.util.ArrayList;
import java.util.Locale;
import android.speech.tts.UtteranceProgressListener;

public class VoiceEngine implements RecognitionListener, TextToSpeech.OnInitListener {
    public interface Listener { void onText(String text); void onState(String state); }

    private final Context context;
    private final Listener listener;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingSpeech;
    private Runnable pendingSpeechCallback;
    private final java.util.Map<String, Runnable> speechCallbacks = new java.util.HashMap<>();

    public VoiceEngine(Context c, Listener l) {
        context = c.getApplicationContext();
        listener = l;
        tts = new TextToSpeech(context, this);
        if (tts != null) tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { if (listener != null) listener.onState("הסוכן מדבר..."); }
            @Override public void onDone(String utteranceId) {
                if (listener != null) listener.onState("הסוכן סיים לדבר");
                Runnable r = speechCallbacks.remove(utteranceId);
                if (r != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
            }
            @Override public void onError(String utteranceId) { if (listener != null) listener.onState("שגיאה בהשמעה קולית"); speechCallbacks.remove(utteranceId); }
        });
    }

    public boolean startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return false;
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(this);

        android.content.Intent i = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "he-IL");
        i.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizer.startListening(i);
        if (listener != null) listener.onState("מאזין...");
        return true;
    }

    public void speak(String text) { speak(text, null); }

    public void speak(String text, final Runnable afterSpeech) {
        if (text == null || text.trim().isEmpty()) return;
        pendingSpeech = text.trim();
        pendingSpeechCallback = afterSpeech;
        if (!ttsReady || tts == null) return;
        speakNow(pendingSpeech, afterSpeech);
        pendingSpeech = null;
    }

    private void speakNow(String text) { speakNow(text, null); }

    private void speakNow(String text, final Runnable afterSpeech) {
        if (tts == null || text == null || text.isEmpty()) return;
        if (afterSpeech != null) speechCallbacks.put("agent-he", afterSpeech);
        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agent-he");
        if (result == TextToSpeech.ERROR && listener != null) {
            listener.onState("שגיאה בהשמעה קולית");
        }
    }

    public void destroy() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
    }

    @Override public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            ttsReady = false;
            if (listener != null) listener.onState("מנוע הדיבור לא זמין");
            return;
        }

        Locale hebrew = new Locale("he", "IL");
        int languageStatus = tts.setLanguage(hebrew);
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
            languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            languageStatus = tts.setLanguage(new Locale("he"));
        }

        tts.setSpeechRate(0.95f);
        tts.setPitch(1.0f);
        ttsReady = languageStatus != TextToSpeech.LANG_MISSING_DATA &&
                   languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;

        if (!ttsReady && listener != null) {
            listener.onState("אין קול עברי זמין במכשיר");
            return;
        }

        if (pendingSpeech != null) {
            String text = pendingSpeech;
            Runnable callback = pendingSpeechCallback;
            pendingSpeech = null;
            pendingSpeechCallback = null;
            speakNow(text, callback);
        }
    }

    @Override public void onResults(Bundle b) {
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (listener != null && r != null && !r.isEmpty()) {
            listener.onText(r.get(0));
        } else if (listener != null) {
            listener.onState("לא זוהה קול");
        }
    }

    @Override public void onError(int e) {
        if (listener != null) {
            listener.onState("שגיאת מיקרופון: " + errorName(e));
        }
    }

    private String errorName(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_AUDIO: return "בעיית שמע";
            case SpeechRecognizer.ERROR_CLIENT: return "שגיאת אפליקציה";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "אין הרשאת מיקרופון";
            case SpeechRecognizer.ERROR_NETWORK: return "בעיית רשת";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "פסק זמן רשת";
            case SpeechRecognizer.ERROR_NO_MATCH: return "לא זוהה דיבור";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "מנוע הקול עסוק";
            case SpeechRecognizer.ERROR_SERVER: return "שגיאת שרת קול";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "לא התחלת לדבר";
            default: return "קוד " + e;
        }
    }

    @Override public void onReadyForSpeech(Bundle b) {}
    @Override public void onBeginningOfSpeech() {
        if (listener != null) listener.onState("שומע אותך...");
    }

    @Override public void onRmsChanged(float v) {
        if (listener != null && v > 2.0f) listener.onState("קולט קול...");
    }

    @Override public void onBufferReceived(byte[] b) {}

    @Override public void onEndOfSpeech() {
        if (listener != null) listener.onState("מעבד את הדיבור...");
    }
    @Override public void onPartialResults(Bundle b) {}
    @Override public void onEvent(int t, Bundle b) {}
}