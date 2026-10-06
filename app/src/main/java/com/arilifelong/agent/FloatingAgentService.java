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
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(intent==null)return;
            String d=intent.getStringExtra("description");
            if(d==null||d.trim().isEmpty())return;
            if(voice!=null) voice.speak("אני רואה: "+d.trim(),null);
        }
    };

    private WindowManager.LayoutParams overlayLp;
    private float downX,downY; private int startX,startY; private boolean dragging;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        voice=new VoiceEngine(getApplicationContext(),this);
        showBar();
        voice.speak("שלום, אני העוזר האישי שלך. מה תרצה שאעשה עבורך היום?", FloatingAgentService.this::startVoiceInput);
        try{ registerReceiver(screenReceiver,new IntentFilter("com.arilifelong.agent.SCREEN_CHANGED")); }catch(Exception ignored){}
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
        root.setPadding(14,8,10,8);
        GradientDrawable shell=bg(Color.WHITE,34);
        shell.setStroke(1,Color.rgb(232,234,242));
        root.setBackground(shell);
        root.setElevation(16f);

        TextView icon=label("✦",22,Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(bg(Color.rgb(103,87,217),28));
        root.addView(icon,new LinearLayout.LayoutParams(46,46));

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(12,0,8,0);
        TextView title=label("הסוכן שלי",15,Color.rgb(28,29,43));
        title.setTypeface(null,1);
        status=label("מוכן להקשיב",11,Color.rgb(112,114,128));
        info.addView(title,new LinearLayout.LayoutParams(-1,24));
        info.addView(status,new LinearLayout.LayoutParams(-1,20));
        root.addView(info,new LinearLayout.LayoutParams(0,46,1));

        talk=new Button(this);
        talk.setText("🎙  דבר");
        talk.setTextSize(13);
        talk.setTextColor(Color.WHITE);
        talk.setAllCaps(false);
        talk.setPadding(16,0,16,0);
        talk.setBackground(bg(Color.rgb(103,87,217),26));
        talk.setMinHeight(46);
        talk.setOnClickListener(v->startVoiceInput());
        root.addView(talk,new LinearLayout.LayoutParams(112,46));

        TextView close=label("×",25,Color.rgb(120,121,135));
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v->stopSelf());
        root.addView(close,new LinearLayout.LayoutParams(34,46));

        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
                -1,WindowManager.LayoutParams.WRAP_CONTENT,type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        lp.y=72;
        overlayLp=lp;

        View.OnTouchListener dragListener=(v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=e.getRawX(); downY=e.getRawY();
                    startX=overlayLp.x; startY=overlayLp.y; dragging=false; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getRawX()-downX, dy=e.getRawY()-downY;
                    if(Math.abs(dx)>8||Math.abs(dy)>8)dragging=true;
                    if(dragging){
                        overlayLp.x=startX+(int)dx;
                        overlayLp.y=Math.max(8,startY+(int)dy);
                        try{wm.updateViewLayout(bar,overlayLp);}catch(Exception ignored){}
                    }
                    return true;
                case MotionEvent.ACTION_UP:return true;
            }
            return false;
        };
        icon.setOnTouchListener(dragListener);
        info.setOnTouchListener(dragListener);
        wm.addView(root,lp);
        bar=root;
    }

    private void showErrorCopy(String error,String command){
        // Error-copy UI intentionally removed from the assistant interface.
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
        setMode("🎙  מדבר איתך","מקשיב לך");
        voice.startListening();
    }

    private JSONArray pendingActions;
    private boolean waitingForConfirmation=false;

    private boolean isYes(String t){
        String s=t==null?"":t.trim().toLowerCase();
        return s.equals("כן")||s.equals("אשר")||s.equals("אישור")||s.equals("בצע")||s.equals("תבצע")||s.equals("yes");
    }

    private boolean isNo(String t){
        String s=t==null?"":t.trim().toLowerCase();
        return s.equals("לא")||s.startsWith("לא,")||s.contains("תיקון")||s.contains("לא התכוונתי");
    }

    private String understood(JSONArray a){
        if(a==null||a.length()==0)return "שאין פעולה לביצוע";
        StringBuilder b=new StringBuilder();
        for(int i=0;i<a.length();i++){
            JSONObject x=a.optJSONObject(i);
            if(x==null)continue;
            if(i>0)b.append(", ואז ");
            String t=x.optString("type");
            if("open_app".equals(t))b.append("לפתוח אפליקציה");
            else if("open_url".equals(t))b.append("לפתוח אתר");
            else if("sms".equals(t))b.append("לשלוח הודעה");
            else if("call".equals(t)||"dial".equals(t))b.append("להתקשר");
            else if("settings".equals(t)||"app_settings".equals(t))b.append("לפתוח הגדרות");
            else if("move_overlay".equals(t)||"move_overlay_xy".equals(t))b.append("להזיז את החלון הצף");
            else if("scroll".equals(t)||"scroll_repeat".equals(t)||"scroll_until_text".equals(t))b.append("לגלול");
            else if("tap".equals(t)||"click_text".equals(t))b.append("ללחוץ");
            else if("back".equals(t))b.append("לחזור אחורה");
            else if("home".equals(t))b.append("לעבור למסך הבית");
            else b.append("לבצע ").append(t);
        }
        return b.toString();
    }

    @Override public void onText(String text){
        if(text==null||text.trim().isEmpty())return;
        if(waitingForConfirmation&&pendingActions!=null){
            if(isYes(text)){
                JSONArray a=pendingActions;
                pendingActions=null; waitingForConfirmation=false;
                setMode("⚙  מבצע…","מבצע את מה שאישרת");
                ActionResult ar=runActions(a);
                String reply=ar.failed==0?"":(ar.succeeded==0?"לא הצלחתי לבצע את הפעולה.":"לא הצלחתי לבצע את כל הפעולה.");
                if(ar.failed==0){ setMode("●  שומע…","מוכן לפקודה הבאה"); startVoiceInput(); }
                else { setMode("⚠  לא הצלחתי","אפשר לנסות שוב"); voice.speak("לא הצלחתי לבצע את זה. מה תרצה שאנסה עכשיו?",FloatingAgentService.this::startVoiceInput); }
                return;
            }
            if(!isNo(text)){
                voice.speak("אמור כן כדי לאשר, או לא כדי לתקן אותי.",FloatingAgentService.this::startVoiceInput);
                return;
            }
            pendingActions=null; waitingForConfirmation=false;
        }
        setMode("⚙  בודק…","בודק את הבקשה שלך");
        ApiClient.chat(text,new ApiClient.Callback(){
            @Override public void success(JSONObject result){
                JSONArray actions=result.optJSONArray("actions");
                if(actions==null||actions.length()==0){
                    voice.speak(result.optString("reply","לא זיהיתי פעולה לביצוע."),FloatingAgentService.this::startVoiceInput);
                    return;
                }
                pendingActions=actions; waitingForConfirmation=true;
                String msg="הבנתי שאתה רוצה "+understood(actions)+". אם זה נכון אמור כן. אם לא, אמור לא ואז תקן אותי.";
                setMode("⏳  ממתין לאישור","מחכה לאישור שלך");
                voice.speak(msg,FloatingAgentService.this::startVoiceInput);
            }
            @Override public void error(String message){
                setMode("⚠  לא הצלחתי","יש שגיאה — אפשר להעתיק את הלוג");
                showErrorCopy(message,text);
                voice.speak("לא הצלחתי לבצע את זה. מה תרצה שאעשה עכשיו?",FloatingAgentService.this::startVoiceInput);
            }
        });
    }

    @Override public void onState(String s){
        if(s==null)return;
        if(s.contains("מקשיב")||s.equals("מאזין..."))setMode("🎙  מקשיב","מקשיב לך");
        else if(s.contains("מעבד"))setMode("⚙  מבצע…","מעבד את הבקשה");
        else if(s.startsWith("שגיאת"))setMode("🎙  דבר",s);
    }

    private static class ActionResult { int total; int succeeded; int failed; }
    private ActionResult runActions(JSONArray actions){
        ActionResult result=new ActionResult();
        if(actions==null)return result;
        result.total=actions.length();
        for(int i=0;i<actions.length();i++)try{
            setMode("⚙ "+(i+1)+"/"+actions.length(),"מבצע שלב "+(i+1)+" מתוך "+actions.length());
            JSONObject x=actions.getJSONObject(i); String t=x.optString("type");
            if(t.equals("open_app")||t.equals("settings")||t.equals("app_settings")||t.equals("system_action")||t.equals("play_store_search")||t.equals("open_url")){
                try{Thread.sleep(900);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
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
                case "app_settings": ActionEngine.appSettings(this,x.optString("package"));break;
                case "play_store_search": ActionEngine.playStoreSearch(this,x.optString("query"));break;
                case "move_overlay": moveOverlay(x.optString("position","top"));break;
                case "long_click": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.longClick((float)x.optDouble("x",540),(float)x.optDouble("y",1000));break;}
                case "tap": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.tap((float)x.optDouble("x",540),(float)x.optDouble("y",1000));break;}
                case "swipe": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.swipe((float)x.optDouble("x1",540),(float)x.optDouble("y1",1500),(float)x.optDouble("x2",540),(float)x.optDouble("y2",500),(long)x.optDouble("duration",600));break;}
                case "scroll_repeat": {AgentAccessibilityService s=AgentAccessibilityService.getInstance(); if(s!=null){int n=Math.min(50,Math.max(1,x.optInt("count",10))); boolean fwd=!"back".equalsIgnoreCase(x.optString("direction")); for(int k=0;k<n;k++){if(!s.scroll(fwd))break; try{Thread.sleep(Math.min(800,Math.max(50,x.optInt("delay",250))));}catch(Exception ignored){}}}break;}
                case "back": ActionEngine.back();break;
                case "home": ActionEngine.home();break;
                case "recents": ActionEngine.recents();break;
                case "notifications": ActionEngine.notifications(this);break;
                case "quick_settings": ActionEngine.quickSettings(this);break;
                case "click_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("CLICK_TEXT",x.optString("text"),"");break;}
                case "click_content_description": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("CLICK_CONTENT_DESCRIPTION",x.optString("text",x.optString("target")),"");break;}
                case "click_role": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("CLICK_ROLE",x.optString("text",x.optString("role")),"");break;}
                case "long_click_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("LONG_CLICK_TEXT",x.optString("text",x.optString("target")),"");break;}
                case "type_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("TYPE_TEXT",x.optString("text"),"");break;}
                case "send_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("SEND_TEXT",x.optString("text"),"");break;}
                case "scroll": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("SCROLL","",x.optString("direction","down"));break;}
                case "swipe_direction": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("SWIPE","",x.optString("direction","up"));break;}
                case "like": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("LIKE","","");break;}
                case "follow": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("FOLLOW","","");break;}
                case "approve": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.performActionWithFallback("APPROVE","","");break;}
                case "open_chat_menu": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.openChatMenu();break;}
                case "pin": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.pinItem();break;}
                case "press_send": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.pressSend();break;}
                case "open_notifications": ActionEngine.notifications(this);break;
                case "uninstall_app": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.uninstallApp(x.optString("package"),x.optString("app"));break;}
                case "uninstall_current_app": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.uninstallApp("", "");break;}
                case "open_notifications_and_click": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.openNotificationsAndClick(x.optString("text",x.optString("target")),false);break;}
                case "long_click_notification": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.openNotificationsAndClick(x.optString("text",x.optString("target")),true);break;}
                case "click_quick_setting": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.openQuickSettingsAndClick(x.optString("text",x.optString("target")),false);break;}
                case "long_click_quick_setting": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.openQuickSettingsAndClick(x.optString("text",x.optString("target")),true);break;}
                case "screen_info": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)voice.speak(s.screenText(),FloatingAgentService.this::startVoiceInput);break;}
                case "click_repeat": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.clickRepeat(x.optString("text"),x.optInt("count",3),x.optLong("delay",250));break;}
                case "scroll_until_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.scrollUntilText(x.optString("text"),!"back".equalsIgnoreCase(x.optString("direction")),x.optInt("max",30),x.optLong("delay",250));break;}
                case "screenshot": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null)s.screenshot();break;}
                case "volume": ActionEngine.volume(this,x.optString("stream","music"),x.optString("direction","up"));break;
                case "brightness": ActionEngine.brightness(this,x.optInt("value",128));break;
                case "system_action": ActionEngine.systemAction(this,x.optString("action"));break;
                case "hide_overlay": if(bar!=null)bar.setVisibility(View.GONE);break;
                case "show_overlay": if(bar!=null)bar.setVisibility(View.VISIBLE);break;
                case "resize_overlay": if(overlayLp!=null&&wm!=null&&bar!=null){overlayLp.width=Math.max(260,Math.min(900,x.optInt("width",520)));try{wm.updateViewLayout(bar,overlayLp);}catch(Exception ignored){}}break;
                case "move_overlay_xy": if(overlayLp!=null&&wm!=null&&bar!=null){overlayLp.gravity=Gravity.TOP|Gravity.LEFT;overlayLp.x=x.optInt("x",0);overlayLp.y=Math.max(8,x.optInt("y",80));try{wm.updateViewLayout(bar,overlayLp);}catch(Exception ignored){}}break;
                case "save_routine": {String n=x.optString("name","routine"),data=x.optString("routine_json","[]");getSharedPreferences("routines",0).edit().putString(n,data).apply();break;}
                case "run_routine": {String n=x.optString("name","routine"),data=getSharedPreferences("routines",0).getString(n,"[]");try{runActions(new JSONArray(data));}catch(Exception ignored){}break;}
                case "chrome_new_tab": {AgentAccessibilityService svc=AgentAccessibilityService.getInstance();if(svc!=null)svc.chromeNewTab();break;}
                case "chrome_close_tab": {AgentAccessibilityService svc=AgentAccessibilityService.getInstance();if(svc!=null)svc.chromeCloseTab();break;}
                case "chrome_next_tab": {AgentAccessibilityService svc=AgentAccessibilityService.getInstance();if(svc!=null)svc.chromeNextTab();break;}
                case "chrome_previous_tab": {AgentAccessibilityService svc=AgentAccessibilityService.getInstance();if(svc!=null)svc.chromePreviousTab();break;}
                case "chrome_clear_search": {AgentAccessibilityService svc=AgentAccessibilityService.getInstance();if(svc!=null)svc.chromeClearSearch();break;}
                case "copy": {android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(android.content.ClipData.newPlainText("agent",x.optString("text")));break;}
            }
            // פעולה שלא מחזירה boolean מסומנת כהצלחה אם לא נזרקה חריגה.
            result.succeeded++;
        }catch(Exception ignored){result.failed++;}
        return result;
    }

    private void moveOverlay(String position){
        if(overlayLp==null||wm==null||bar==null)return;
        if("bottom".equalsIgnoreCase(position)||"למטה".equals(position)){ overlayLp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL; overlayLp.y=24; }
        else if("top".equalsIgnoreCase(position)||"למעלה".equals(position)){ overlayLp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; overlayLp.y=80; }
        else { overlayLp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL; overlayLp.y=Math.max(8,overlayLp.y); }
        try{wm.updateViewLayout(bar,overlayLp);}catch(Exception ignored){}
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
