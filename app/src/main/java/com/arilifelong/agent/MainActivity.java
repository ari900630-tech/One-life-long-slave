package com.arilifelong.agent;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity implements VoiceEngine.Listener {
    private static final int OVERLAY_REQUEST=1001, PERM_REQUEST=1002;
    private TextView status; private VoiceEngine voice;

    @Override public void onCreate(Bundle state){
        super.onCreate(state); setContentView(R.layout.activity_main);
        status=findViewById(R.id.status);
        voice=new VoiceEngine(this,this);

        findViewById(R.id.enable).setOnClickListener(v->enableOverlay());
        findViewById(R.id.enable).setOnLongClickListener(v->{openAccessibility();return true;});
        findViewById(R.id.accessibility).setOnClickListener(v->openAccessibility());
        findViewById(R.id.notifications).setOnClickListener(v->openNotificationSettings());
        findViewById(R.id.permissions).setOnClickListener(v->requestPermissions());
        findViewById(R.id.talk).setOnClickListener(v->{ if(!has(Manifest.permission.RECORD_AUDIO)){requestPermissions();return;} voice.startListening(); });
        updateStatus();
    }
    private boolean has(String p){return android.os.Build.VERSION.SDK_INT<23||checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    private void requestPermissions(){
        if(android.os.Build.VERSION.SDK_INT<23)return;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.CAMERA,Manifest.permission.READ_CONTACTS,Manifest.permission.CALL_PHONE,Manifest.permission.SEND_SMS},PERM_REQUEST);
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
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==OVERLAY_REQUEST&&Settings.canDrawOverlays(this))startFloating();}
    @Override protected void onDestroy(){if(voice!=null)voice.destroy();super.onDestroy();}
    @Override public void onText(String text){status.setText("הסוכן מבצע: "+text); ApiClient.chat(text,new ApiClient.Callback(){
        public void success(JSONObject result){runActions(result.optJSONArray("actions")); String reply=result.optString("reply","בוצע"); status.setText(reply); voice.speak(reply);}
        public void error(String message){status.setText(message); voice.speak(message);}
    });}
    @Override public void onState(String s){status.setText(s);}
    private void runActions(JSONArray a){
        if(a==null)return;
        for(int i=0;i<a.length();i++)try{
            JSONObject x=a.getJSONObject(i); String t=x.optString("type");
            boolean ok=false;
            switch(t){
                case "open_url": ok=ActionEngine.openUrl(this,x.optString("url"));break;
                case "open_app": ok=ActionEngine.openApp(this,x.optString("package"));break;
                case "dial": ok=ActionEngine.dial(this,x.optString("number"));break;
                case "call": ok=ActionEngine.call(this,x.optString("number"));break;
                case "sms": ok=ActionEngine.sms(this,x.optString("number"),x.optString("text"));break;
                case "email": ok=ActionEngine.email(this,x.optString("address"),x.optString("subject"),x.optString("body"));break;
                case "maps": ok=ActionEngine.maps(this,x.optString("query"));break;
                case "camera": ok=ActionEngine.camera(this);break;
                case "settings": ok=ActionEngine.settings(this);break;
                case "back": ok=ActionEngine.back();break;
                case "home": ok=ActionEngine.home();break;
                case "recents": ok=ActionEngine.recents();break;
                case "notifications": ok=ActionEngine.notifications(this);break;
                case "quick_settings": ok=ActionEngine.quickSettings(this);break;
                case "click_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.clickContains(x.optString("text"));break;}
                case "type_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.setText(x.optString("text"));break;}
                case "scroll": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();ok=s!=null&&s.scroll(!"back".equals(x.optString("direction")));break;}
                case "copy": {android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("agent",x.optString("text")));ok=true;break;}
            }
        }catch(Exception ignored){}
    }
}