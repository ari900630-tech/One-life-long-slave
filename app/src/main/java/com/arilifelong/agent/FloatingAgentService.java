package com.arilifelong.agent;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class FloatingAgentService extends Service implements VoiceEngine.Listener {
    private WindowManager wm;
    private View bar;
    private TextView status;
    private Button talk;
    private VoiceEngine voice;
    private static final String CHANNEL="agent_floating";
    private static final int NOTIFICATION_ID=7;
    public static final String ACTION_UPDATE_NOTIFICATION="com.arilifelong.agent.UPDATE_NOTIFICATION";
    private String notificationText="הסוכן הצף פעיל";

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        voice=new VoiceEngine(getApplicationContext(),this);
        showBar();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c=new NotificationChannel(CHANNEL,"הסוכן הצף ומיקרופון",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("מציג את מצב הסוכן והמיקרופון במסך ההתראות");
            c.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notification() {
        Intent i=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL)
                .setContentTitle("הסוכן שלי")
                .setContentText(notificationText)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).build();
    }

    private void updateNotification(String text) {
        if(text==null||text.trim().isEmpty())return;
        notificationText=text.trim();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(nm!=null)nm.notify(NOTIFICATION_ID,notification());
    }

    private GradientDrawable bg(int color,float radius){
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); return g;
    }

    private TextView label(String text,float size,int color){
        TextView v=new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }

    private void showBar() {
        if(!Settings.canDrawOverlays(this))return;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(18,10,12,10);
        root.setBackground(bg(Color.WHITE,38));

        LinearLayout.LayoutParams rootLp=new LinearLayout.LayoutParams(-1,WRAP_CONTENT);
        root.setElevation(12f);

        TextView icon=label("✦",22,Color.rgb(70,55,160));
        icon.setGravity(Gravity.CENTER);
        root.addView(icon,new LinearLayout.LayoutParams(42,52));

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("הסוכן שלי",15,Color.rgb(35,35,45));
        title.setTypeface(null,1);
        status=label("מוכן",11,Color.rgb(105,105,115));
        info.addView(title,new LinearLayout.LayoutParams(-1,28));
        info.addView(status,new LinearLayout.LayoutParams(-1,22));
        root.addView(info,new LinearLayout.LayoutParams(0,52,1));

        talk=new Button(this);
        talk.setText("🎙  דבר");
        talk.setTextSize(14);
        talk.setTextColor(Color.WHITE);
        talk.setAllCaps(false);
        talk.setPadding(22,0,22,0);
        talk.setBackground(bg(Color.rgb(78,64,170),50));
        talk.setMinHeight(52);
        talk.setOnClickListener(v->startVoiceInput());
        root.addView(talk,new LinearLayout.LayoutParams(125,52));

        TextView close=label("×",26,Color.rgb(110,110,120));
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v->stopSelf());
        root.addView(close,new LinearLayout.LayoutParams(38,52));

        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                -1,WindowManager.LayoutParams.WRAP_CONTENT,type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
        lp.y=18;
        wm.addView(root,lp);
        bar=root;
    }

    private void setMode(String button,String state){
        if(talk!=null){
            talk.setText(button);
            talk.setEnabled(true);
        }
        if(status!=null)status.setText(state);
        updateNotification("הסוכן: "+state);
    }

    private void startVoiceInput(){
        if(voice==null)return;
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            setMode("🎙  דבר","נדרשת הרשאת מיקרופון");
            Intent i=new Intent(this,MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            return;
        }
        setMode("●  שומע…","מקשיב לך");
        voice.startListening();
    }

    @Override public void onText(String text){
        setMode("⚙  מבצע…","מבצע: "+text);
        ApiClient.chat(text,new ApiClient.Callback(){
            @Override public void success(JSONObject result){
                runActions(result.optJSONArray("actions"));
                String reply=result.optString("reply","בוצע");
                setMode("✓  בוצע","בוצע");
                voice.speak(reply);
            }
            @Override public void error(String message){
                setMode("⚠  שגיאה","שגיאה");
                voice.speak(message);
            }
        });
    }

    @Override public void onState(String s){
        if(s==null)return;
        if(s.contains("שומע אותך")||s.equals("מאזין..."))setMode("●  שומע…","שומע אותך");
        else if(s.contains("מעבד"))setMode("⚙  מבצע…","מעבד את הבקשה");
        else if(s.startsWith("שגיאת"))setMode("🎙  דבר",s);
    }

    private void runActions(JSONArray a){
        if(a==null)return;
        for(int i=0;i<a.length();i++)try{
            JSONObject x=a.getJSONObject(i); String t=x.optString("type");
            switch(t){
                case "open_url": ActionEngine.openUrl(this,x.optString("url"));break;
                case "open_app": ActionEngine.openApp(this,x.optString("package"));break;
                case "dial": ActionEngine.dial(this,x.optString("number"));break;
                case "call": ActionEngine.call(this,x.optString("number"));break;
                case "sms": ActionEngine.sms(this,x.optString("number"),x.optString("text"));break;
                case "email": ActionEngine.email(this,x.optString("address"),x.optString("subject"),x.optString("body"));break;
                case "maps": ActionEngine.maps(this,x.optString("query"));break;
                case "camera": ActionEngine.camera(this);break;
                case "settings": ActionEngine.settings(this);break;
                case "back": ActionEngine.back();break;
                case "home": ActionEngine.home();break;
                case "recents": ActionEngine.recents();break;
                case "notifications": ActionEngine.notifications(this);break;
                case "quick_settings": ActionEngine.quickSettings(this);break;
                case "click_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.clickContains(x.optString("text"));break;}
                case "type_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.setText(x.optString("text"));break;}
                case "scroll": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.scroll(!"back".equals(x.optString("direction")));break;}
                case "copy": {android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("agent",x.optString("text")));break;}
            }
        }catch(Exception ignored){}
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&ACTION_UPDATE_NOTIFICATION.equals(i.getAction()))updateNotification(i.getStringExtra("text"));
        return START_STICKY;
    }

    @Override public void onDestroy(){
        if(voice!=null){voice.destroy();voice=null;}
        if(wm!=null&&bar!=null){try{wm.removeView(bar);}catch(Exception ignored){}}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i){return null;}
}
