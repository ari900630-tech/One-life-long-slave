package com.arilifelong.agent;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

public class MainActivity extends Activity implements VoiceEngine.Listener {
    private static final int OVERLAY_REQUEST=1001, PERM_REQUEST=1002, VOICE_REQUEST=1003;
    private TextView status; private TextView micIndicator, heardIndicator, agentIndicator; private VoiceEngine voice; private JSONArray pendingSuggestions;

    @Override public void onCreate(Bundle state){
        super.onCreate(state); setContentView(R.layout.activity_main);
        status=findViewById(R.id.status); micIndicator=findViewById(R.id.mic_indicator); heardIndicator=findViewById(R.id.heard_indicator); agentIndicator=findViewById(R.id.agent_indicator);
        voice=new VoiceEngine(this,this);

        findViewById(R.id.enable).setOnClickListener(v->enableOverlay());
        findViewById(R.id.enable).setOnLongClickListener(v->{openAccessibility();return true;});
        findViewById(R.id.accessibility).setOnClickListener(v->openAccessibility());
        findViewById(R.id.notifications).setOnClickListener(v->openNotificationSettings());
        findViewById(R.id.permissions).setOnClickListener(v->requestPermissions());
        findViewById(R.id.talk).setOnClickListener(v->startVoiceInput());
        updateStatus();
        // השיחה מתבצעת מהחלונית הצפה; אין צורך לפתוח את האפליקציה הראשית.
    }

    private boolean has(String p){return android.os.Build.VERSION.SDK_INT<23||checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}

    private void requestPermissions(){
        if(android.os.Build.VERSION.SDK_INT<23)return;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA,Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE,Manifest.permission.SEND_SMS},PERM_REQUEST);
    }

    private void startConversation(){
        voice.speak("שלום, אני הסוכן שלך. מה תרצה שאעשה עכשיו?", this::startVoiceInput);
    }

    private void startVoiceInput(){
        if(!has(Manifest.permission.RECORD_AUDIO)){
            setIndicators("נדרשת הרשאת מיקרופון","לא ניתן להאזין","פתח הרשאות");
            requestPermissions();
            return;
        }
        setIndicators("מיקרופון פעיל","מקשיב עכשיו...","ממתין");
        updateMicrophoneNotification("המיקרופון פועל — הסוכן מאזין");
        boolean started=voice.startListening();
        if(!started){
            updateMicrophoneNotification("המיקרופון אינו פעיל — בדוק הרשאת מיקרופון");
        }
    }

    private void enableOverlay(){
        if(!Settings.canDrawOverlays(this)){
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())),OVERLAY_REQUEST);
        }else startFloating();
    }

    private void startFloating(){
        Intent i=new Intent(this,FloatingAgentService.class);
        if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        status.setText("הסוכן הצף פעיל");
    }

    private void openAccessibility(){try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception ignored){}}
    private void openNotificationSettings(){try{startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));}catch(Exception ignored){}}

    private void updateStatus(){
        status.setText(Settings.canDrawOverlays(this)?"הסוכן מוכן. הפעל שליטה במסך והרשאות לפי הצורך.":"יש להפעיל הרשאת חלון צף.");
        if(Settings.canDrawOverlays(this))startFloating();
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==OVERLAY_REQUEST&&Settings.canDrawOverlays(this))startFloating();
        if(r==VOICE_REQUEST){
            if(c==RESULT_OK&&d!=null){
                ArrayList<String> results=d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if(results!=null&&!results.isEmpty()){
                    onText(results.get(0));
                }else{
                    setIndicators("מיקרופון פעיל","לא זוהה קול","לא נשלחה פקודה");
                }
            }else{
                setIndicators("מיקרופון מוכן","ההאזנה בוטלה","ממתין לפקודה");
            }
        }
    }

    private boolean handleSuggestionRequest(String text){
        String q=text==null?"":text.trim();
        String l=q.toLowerCase(java.util.Locale.ROOT);
        if(!(l.contains("מה אני יכול לעשות")||l.contains("מה אפשר לעשות")||l.contains("איזה אפשרויות")||l.contains("אפשרויות כאן")||l.contains("תציע לי")))return false;
        AgentAccessibilityService a=AgentAccessibilityService.getInstance();
        if(a==null){voice.speak("צריך להפעיל הרשאת שליטה במסך.",MainActivity.this::startVoiceInput);return true;}
        pendingSuggestions=a.suggestionActions();
        if(pendingSuggestions.length()==0){voice.speak("אני לא מזהה כרגע פעולות ברורות במסך.",MainActivity.this::startVoiceInput);return true;}
        StringBuilder b=new StringBuilder("האפשרויות שאני ממליץ עליהן הן: ");
        for(int i=0;i<pendingSuggestions.length();i++)try{b.append(i+1).append(". ").append(pendingSuggestions.getJSONObject(i).optString("label")).append(". ");}catch(Exception ignored){}
        voice.speak(b.toString(),MainActivity.this::startVoiceInput);
        return true;
    }

    private boolean handleSuggestionCommand(String text){
        if(pendingSuggestions==null||pendingSuggestions.length()==0)return false;
        String q=(text==null?"":text.trim()).toLowerCase(java.util.Locale.ROOT);
        int n=-1;
        if(q.contains("אחד")||q.contains("ראשונה")||q.equals("1"))n=1;
        else if(q.contains("שניים")||q.contains("שתיים")||q.contains("שנייה")||q.equals("2"))n=2;
        else if(q.contains("שלוש")||q.contains("שלושה")||q.contains("שלישית")||q.equals("3"))n=3;
        else if(q.contains("ארבע")||q.contains("ארבעה")||q.contains("רביעית")||q.equals("4"))n=4;
        else if(q.contains("חמש")||q.contains("חמישה")||q.contains("חמישית")||q.equals("5"))n=5;
        else if(q.contains("שש")||q.contains("שישית")||q.equals("6"))n=6;
        else if(q.contains("שבע")||q.contains("שבעה")||q.contains("שביעית")||q.equals("7"))n=7;
        if(n<1||n>pendingSuggestions.length())return false;
        try{
            JSONObject chosen=pendingSuggestions.getJSONObject(n-1);
            JSONArray one=new JSONArray(); one.put(chosen);
            pendingSuggestions=null;
            runActions(one);
            voice.speak("מבצע את אפשרות "+n+".",MainActivity.this::startVoiceInput);
        }catch(Exception e){voice.speak("לא הצלחתי לבצע את האפשרות.",MainActivity.this::startVoiceInput);}
        return true;
    }

    @Override protected void onDestroy(){if(voice!=null)voice.destroy();super.onDestroy();}

    @Override public void onText(String text){
        if(handleSuggestionCommand(text)) return;
        if(handleSuggestionRequest(text)) return;
        setIndicators("מיקרופון קלט קול","שמעתי: "+text,"מעבד עכשיו...");
        updateMicrophoneNotification("המיקרופון פעיל — מעבד את הדיבור");
        status.setText("הסוכן מבצע: "+text);
        ApiClient.chat(text,new ApiClient.Callback(){
            public void success(JSONObject result){runActions(result.optJSONArray("actions")); String reply=result.optString("reply","בוצע"); status.setText(reply); setIndicators("מיקרופון מוכן","הפקודה נקלטה","הסוכן משיב"); voice.speak(reply, MainActivity.this::startVoiceInput);}
            public void error(String message){status.setText(message); setIndicators("מיקרופון מוכן","הפקודה נקלטה","שגיאה: "+message); voice.speak(message, MainActivity.this::startVoiceInput);}
        });
    }

    @Override public void onState(String s){
        status.setText(s);
        if("מאזין...".equals(s)) { setIndicators("מיקרופון פעיל","מקשיב עכשיו...","ממתין לתשובה"); updateMicrophoneNotification("המיקרופון פועל — הסוכן מאזין"); }
        else if("שומע אותך...".equals(s)) { setIndicators("מיקרופון פעיל","שומע אותך עכשיו...","מקליט"); updateMicrophoneNotification("המיקרופון פועל — שומע אותך"); }
        else if("קולט קול...".equals(s)) { setIndicators("מיקרופון פעיל","קולט קול...","מקליט"); updateMicrophoneNotification("המיקרופון פועל — קולט קול"); }
        else if("מעבד את הדיבור...".equals(s)) { setIndicators("מיקרופון פעיל","מעבד את הדיבור...","שולח לתמלול"); updateMicrophoneNotification("המיקרופון סיים הקלטה — מתמלל"); }
        else if("לא זוהה קול".equals(s)) { setIndicators("מיקרופון מוכן","לא זוהה קול","לא נשלחה פקודה"); updateMicrophoneNotification("המיקרופון מוכן — לא נקלט דיבור"); }
        else if(s.contains("מנוע הדיבור")||s.contains("קול עברי")) { setIndicators("מיקרופון לא זמין","לא זוהה קול","בעיה במנוע הקולי"); updateMicrophoneNotification("הקול אינו זמין — בדוק מנוע TTS"); }
        else if(s.startsWith("שגיאת מיקרופון:")) { setIndicators("מיקרופון לא זמין",s,"ההאזנה נעצרה"); updateMicrophoneNotification(s); }
        else if("מוכן".equals(s)) { setIndicators("מיקרופון מוכן","ממתין לפקודה","מוכן"); updateMicrophoneNotification("המיקרופון מוכן — אינו מקליט עכשיו"); }
    }

    private void updateMicrophoneNotification(String text){
        try {
            Intent i=new Intent(this,FloatingAgentService.class);
            i.setAction(FloatingAgentService.ACTION_UPDATE_NOTIFICATION);
            i.putExtra("text",text);
            if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        } catch(Exception ignored) {}
    }

    private void setIndicators(String a,String b,String c){
        if(micIndicator!=null)micIndicator.setText("● "+a);
        if(heardIndicator!=null)heardIndicator.setText("● "+b);
        if(agentIndicator!=null)agentIndicator.setText("● "+c);
    }

    private void runActions(JSONArray a){
        if(a==null)return;
        for(int i=0;i<a.length();i++)try{
            JSONObject x=a.getJSONObject(i); String t=x.optString("type");
            boolean ok=false;
            switch(t){
                case "open_url": ok=ActionEngine.openUrl(this,x.optString("url"));break;
                case "instagram_action": {AgentAccessibilityService a=AgentAccessibilityService.getInstance();ok=a!=null&&a.instagramAction(x.optString("action"),x.optString("value"));break;}

                case "chrome_new_tab": {AgentAccessibilityService a=AgentAccessibilityService.getInstance();ok=a!=null&&a.chromeNewTab();break;}
                case "chrome_close_tab": {AgentAccessibilityService a=AgentAccessibilityService.getInstance();ok=a!=null&&a.chromeCloseTab();break;}
                case "chrome_next_tab": {AgentAccessibilityService a=AgentAccessibilityService.getInstance();ok=a!=null&&a.chromeNextTab();break;}
                case "chrome_previous_tab": {AgentAccessibilityService a=AgentAccessibilityService.getInstance();ok=a!=null&&a.chromePreviousTab();break;}
                case "chrome_clear_search": {AgentAccessibilityService a=AgentAccessibilityService.getInstance();ok=a!=null&&a.chromeClearSearch();break;}

                case "open_app": ok=ActionEngine.openApp(this,x.optString("package"));break;
                case "dial": ok=ActionEngine.dial(this,x.optString("number"));break;
                case "call": ok=ActionEngine.call(this,x.optString("number"));break;
                case "sms": ok=ActionEngine.sms(this,x.optString("number"),x.optString("text"));break;
                case "email": ok=ActionEngine.email(this,x.optString("address"),x.optString("subject"),x.optString("body"));break;
                case "maps": ok=ActionEngine.maps(this,x.optString("query"));break;
                case "camera": ok=ActionEngine.camera(this);break;
                case "settings": ok=ActionEngine.settings(this);break;
                case "app_settings": ok=ActionEngine.appSettings(this,x.optString("package"));break;
                case "play_store_search": ok=ActionEngine.playStoreSearch(this,x.optString("query"));break;
                case "long_click": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.longClick((float)x.optDouble("x",540),(float)x.optDouble("y",1000));break;}
                case "tap": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.tap((float)x.optDouble("x",540),(float)x.optDouble("y",1000));break;}
                case "swipe": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.swipe((float)x.optDouble("x1",540),(float)x.optDouble("y1",1500),(float)x.optDouble("x2",540),(float)x.optDouble("y2",500),(long)x.optDouble("duration",600));break;}
                case "scroll_repeat": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null; if(ok){int n=Math.min(50,Math.max(1,x.optInt("count",10)));boolean fwd=!"back".equalsIgnoreCase(x.optString("direction"));for(int k=0;k<n;k++){if(!s.scroll(fwd))break;try{Thread.sleep(Math.min(800,Math.max(50,x.optInt("delay",250))));}catch(Exception ignored){}}}break;}
                case "back": ok=ActionEngine.back();break;
                case "home": ok=ActionEngine.home();break;
                case "recents": ok=ActionEngine.recents();break;
                case "notifications": ok=ActionEngine.notifications(this);break;
                case "quick_settings": ok=ActionEngine.quickSettings(this);break;
                case "click_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.performActionWithFallback("CLICK_TEXT",x.optString("text"),"");break;}
                case "click_content_description": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.performActionWithFallback("CLICK_CONTENT_DESCRIPTION",x.optString("text",x.optString("target")),"");break;}
                case "click_role": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.performActionWithFallback("CLICK_ROLE",x.optString("text",x.optString("role")),"");break;}
                case "long_click_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.performActionWithFallback("LONG_CLICK_TEXT",x.optString("text",x.optString("target")),"");break;}
                case "send_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.performActionWithFallback("SEND_TEXT",x.optString("text"),"");break;}
                case "swipe_direction": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.performActionWithFallback("SWIPE","",x.optString("direction","up"));break;}
                case "like": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.performActionWithFallback("LIKE","","");break;}
                case "follow": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.performActionWithFallback("FOLLOW","","");break;}
                case "approve": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.performActionWithFallback("APPROVE","","");break;}
                case "open_chat_menu": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.openChatMenu();break;}
                case "pin": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.pinItem();break;}
                case "press_send": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.pressSend();break;}
                case "open_notifications": ok=ActionEngine.notifications(this);break;
                case "open_notifications_and_click": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.openNotificationsAndClick(x.optString("text",x.optString("target")),false);break;}
                case "long_click_notification": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.openNotificationsAndClick(x.optString("text",x.optString("target")),true);break;}
                case "click_quick_setting": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.openQuickSettingsAndClick(x.optString("text",x.optString("target")),false);break;}
                case "long_click_quick_setting": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.openQuickSettingsAndClick(x.optString("text",x.optString("target")),true);break;}
                case "screen_info": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null; if(ok) status.setText(s.screenText()); break;}
                case "click_repeat": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.clickRepeat(x.optString("text"),x.optInt("count",3),x.optLong("delay",250))>0;break;}
                case "scroll_until_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.scrollUntilText(x.optString("text"),!"back".equalsIgnoreCase(x.optString("direction")),x.optInt("max",30),x.optLong("delay",250));break;}
                case "screenshot": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.screenshot();break;}
                case "volume": ok=ActionEngine.volume(this,x.optString("stream","music"),x.optString("direction","up"));break;
                case "brightness": ok=ActionEngine.brightness(this,x.optInt("value",128));break;
                case "system_action": ok=ActionEngine.systemAction(this,x.optString("action"));break;
                case "copy": {android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("agent",x.optString("text")));ok=true;break;}
            }
        }catch(Exception ignored){}
    }
}