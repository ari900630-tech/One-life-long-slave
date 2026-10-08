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
    private static volatile FloatingAgentService activeInstance;
    private WindowManager wm;
    private View bar;
    private TextView status;
    private Button talk;
    private Button chatToggle;
    private LinearLayout chatPanel;
    private WindowManager.LayoutParams chatLp;
    private EditText chatInput;
    private TextView chatMessage;
    private TextView suggestionsTitle;
    private LinearLayout suggestionsList;
    private LinearLayout chatConfirm;
    private VoiceEngine voice;
    private static final String CHANNEL="agent_floating";
    private static final int NOTIFICATION_ID=7;
    public static final String ACTION_UPDATE_NOTIFICATION="com.arilifelong.agent.UPDATE_NOTIFICATION";
    private String notificationText="הסוכן הצף פעיל";
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(intent==null||capabilityAnnounced)return;
            String s=intent.getStringExtra("suggestions");
            if(s==null||s.trim().isEmpty())return;
            if(voice==null||voice.isRecording())return;
            try{
                JSONArray a=new JSONArray(s);
                if(a.length()==0)return;
                capabilityAnnounced=true;
                // ללא דיבור אוטומטי
            }catch(Exception e){
                capabilityAnnounced=false;
                RuntimeLogger.log(FloatingAgentService.this,"SCREEN_CAPABILITY_ERROR",e.toString());
            }
        }
    };
    private String lastCapabilityKey="";
    private boolean capabilityAnnounced=false;

    private WindowManager.LayoutParams overlayLp;
    private float downX,downY; private int startX,startY; private boolean dragging;

    @Override public void onCreate() {
        super.onCreate();
        RuntimeLogger.init(this,"floating_service_onCreate");
        RuntimeLogger.log(this,"APP","package="+getPackageName()+" android="+Build.VERSION.RELEASE+" sdk="+Build.VERSION.SDK_INT);
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        voice=new VoiceEngine(getApplicationContext(),this);
        createChatPanel();
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
        // הוסר: הממשק היחיד של השירות הוא חלון הצ׳אט הצף.
        createChatPanel();
    }

    private void createChatPanel(){
        if(wm==null)wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(wm==null||!Settings.canDrawOverlays(this))return;

        chatPanel=new LinearLayout(this);
        chatPanel.setOrientation(LinearLayout.VERTICAL);
        chatPanel.setGravity(Gravity.CENTER_VERTICAL);
        chatPanel.setPadding(8,7,8,7);
        GradientDrawable shell=bg(Color.WHITE,30);
        shell.setStroke(1,Color.rgb(225,226,235));
        chatPanel.setBackground(shell);
        chatPanel.setElevation(18f);

        chatInput=new EditText(this);
        chatInput.setSingleLine(true);
        chatInput.setHint("כתוב מה לעשות...");
        chatInput.setTextSize(15);
        chatInput.setTextColor(Color.rgb(28,29,43));
        chatInput.setHintTextColor(Color.rgb(145,146,158));
        chatInput.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        chatInput.setPadding(10,0,10,0);

        Button send=new Button(this);
        send.setText("שלח");
        send.setTextSize(12);
        send.setTextColor(Color.WHITE);
        send.setAllCaps(false);
        send.setBackground(bg(Color.rgb(103,87,217),22));
        send.setMinHeight(42);
        send.setOnClickListener(v->sendChatText());
        chatInput.setOnEditorActionListener((v,id,event)->{sendChatText();return true;});

        chatMessage=label("",14,Color.rgb(55,56,70));
        chatMessage.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        chatMessage.setPadding(12,4,12,4);
        chatMessage.setVisibility(View.GONE);

        chatConfirm=new LinearLayout(this);
        chatConfirm.setOrientation(LinearLayout.HORIZONTAL);
        chatConfirm.setGravity(Gravity.CENTER_VERTICAL);
        chatConfirm.setVisibility(View.GONE);

        Button approve=new Button(this);
        approve.setText("אישור");
        approve.setTextSize(12);
        approve.setAllCaps(false);
        approve.setTextColor(Color.WHITE);
        approve.setBackground(bg(Color.rgb(54,145,91),22));
        approve.setOnClickListener(v->confirmPendingActions());

        Button correct=new Button(this);
        correct.setText("תיקון");
        correct.setTextSize(12);
        correct.setAllCaps(false);
        correct.setOnClickListener(v->cancelPendingConfirmation());

        chatConfirm.addView(approve,new LinearLayout.LayoutParams(76,42));
        chatConfirm.addView(correct,new LinearLayout.LayoutParams(76,42));

        TextView close=label("×",27,Color.rgb(80,81,95));
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v->closeChatPanel());

        TextView dragHandle=label("⋮⋮",18,Color.rgb(145,146,158));
        dragHandle.setGravity(Gravity.CENTER);
        LinearLayout topRow=new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        topRow.addView(dragHandle,new LinearLayout.LayoutParams(28,48));
        topRow.addView(chatInput,new LinearLayout.LayoutParams(0,48,1));
        topRow.addView(send,new LinearLayout.LayoutParams(68,44));
        topRow.addView(close,new LinearLayout.LayoutParams(42,48));
        chatPanel.addView(topRow,new LinearLayout.LayoutParams(-1,48));

        LinearLayout.LayoutParams messageLp=new LinearLayout.LayoutParams(-1,58);
        chatPanel.addView(chatMessage,messageLp);

        suggestionsTitle=label("אפשר לבקש גם:",13,Color.rgb(90,91,105));
        suggestionsTitle.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        suggestionsTitle.setPadding(10,4,10,2);
        suggestionsTitle.setVisibility(View.GONE);
        chatPanel.addView(suggestionsTitle,new LinearLayout.LayoutParams(-1,32));

        ScrollView suggestionsScroll=new ScrollView(this);
        suggestionsScroll.setFillViewport(true);
        suggestionsScroll.setVerticalScrollBarEnabled(false);
        suggestionsList=new LinearLayout(this);
        suggestionsList.setOrientation(LinearLayout.VERTICAL);
        suggestionsList.setPadding(4,0,4,2);
        suggestionsScroll.addView(suggestionsList,new ScrollView.LayoutParams(-1,-2));
        suggestionsScroll.setVisibility(View.GONE);
        LinearLayout.LayoutParams suggestionsLp=new LinearLayout.LayoutParams(-1,170);
        chatPanel.addView(suggestionsScroll,suggestionsLp);

        LinearLayout.LayoutParams confirmLp=new LinearLayout.LayoutParams(-1,48);
        chatPanel.addView(chatConfirm,confirmLp);

        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        int screenWidthDp=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        int chatWidthDp=Math.min(420,Math.max(340,screenWidthDp-10));
        int chatWidthPx=(int)(chatWidthDp*getResources().getDisplayMetrics().density);
        chatLp=new WindowManager.LayoutParams(
                chatWidthPx,WindowManager.LayoutParams.WRAP_CONTENT,type,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        chatLp.gravity=Gravity.BOTTOM|Gravity.LEFT;
        chatLp.x=0;
        chatLp.y=12;

        final float[] down=new float[2];
        final int[] origin=new int[2];
        final boolean[] moved=new boolean[1];
        dragHandle.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    down[0]=e.getRawX(); down[1]=e.getRawY();
                    origin[0]=chatLp.x; origin[1]=chatLp.y; moved[0]=false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getRawX()-down[0], dy=e.getRawY()-down[1];
                    if(Math.abs(dx)>4||Math.abs(dy)>4)moved[0]=true;
                    if(moved[0]){
                        chatLp.gravity=Gravity.TOP|Gravity.LEFT;
                        chatLp.x=Math.max(0,origin[0]+(int)dx);
                        chatLp.y=Math.max(8,origin[1]+(int)dy);
                        try{wm.updateViewLayout(chatPanel,chatLp);}catch(Exception ignored){}
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        });

        try{
            wm.addView(chatPanel,chatLp);
            chatPanel.setVisibility(View.VISIBLE);
        }catch(Exception e){RuntimeLogger.log(this,"CHAT_ERROR","add_panel="+e);}
    }

    private void closeChatPanel(){
        try{
            if(wm!=null && chatPanel!=null){ wm.removeView(chatPanel); }
        }catch(Exception ignored){}
        chatPanel=null;
        chatInput=null;
        chatMessage=null;
        suggestionsList=null;
        suggestionsTitle=null;
        chatConfirm=null;
        stopSelf();
    }

    private void handleSuggestion(String command){
        if(command==null)return;
        String q=command.trim();
        JSONArray a=new JSONArray();
        try{
            JSONObject x=new JSONObject();
            x.put("type","instagram_action");
            if(q.contains("לייק")) x.put("action","like");
            else if(q.contains("הודעות")) x.put("action","messages");
            else if(q.contains("רילס")) x.put("action","reels");
            else if(q.contains("פוסט הבא")) x.put("action","next");
            else if(q.contains("שמור")) x.put("action","save");
            else if(q.contains("שתף")) x.put("action","share");
            else if(q.contains("פרופיל")) x.put("action","profile");
            else if(q.contains("חפש")) x.put("action","search");
            else { onText(q); return; }
            a.put(x);
            pendingActions=a;
            waitingForConfirmation=true;
            String summary=understood(a);
            hidePlan();
            showPlan("הצעה שנבחרה: "+summary+".\
\
לחץ על אישור כדי שאבצע. תיקון כדי לבטל.");
            setMode("✓  ממתין לאישור","בדוק את הפעולה לפני ביצוע");
        }catch(Exception e){ onText(q); }
    }

    private void toggleChat(){
        if(chatPanel==null)return;
        boolean show=chatPanel.getVisibility()!=View.VISIBLE;
        chatPanel.setVisibility(show?View.VISIBLE:View.GONE);
        if(chatToggle!=null)chatToggle.setText(show?"סגור":"צ׳אט");
        if(wm!=null && chatPanel!=null){
            try{
                WindowManager.LayoutParams cp=(WindowManager.LayoutParams)chatPanel.getLayoutParams();
                cp.flags=show?WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS:WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                wm.updateViewLayout(chatPanel,cp);
            }catch(Exception e){RuntimeLogger.log(this,"CHAT_ERROR","focus_update="+e);}
        }
        if(show&&chatInput!=null){
            chatInput.postDelayed(()->{
                chatInput.requestFocus();
                android.view.inputmethod.InputMethodManager imm=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if(imm!=null)imm.showSoftInput(chatInput,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            },150);
        }
    }

    private void sendChatText(){
        if(chatInput==null)return;
        String text=chatInput.getText().toString().trim();
        if(text.isEmpty())return;
        chatInput.setText("");
        RuntimeLogger.log(this,"CHAT_COMMAND","text="+text);
        onText(text);
    }

    private void showPlan(String plan){
        if(chatMessage==null)return;
        chatMessage.setText(plan==null||plan.trim().isEmpty()?"הבנתי את הבקשה. בדוק את הפעולה ואשר לביצוע.":plan);
        chatMessage.setVisibility(View.VISIBLE);
        if(chatConfirm!=null)chatConfirm.setVisibility(View.VISIBLE);
    }

    private JSONArray buildSuggestions(JSONArray actions){
        JSONArray a=new JSONArray();
        boolean instagram=false;
        for(int i=0;i<(actions==null?0:actions.length());i++){
            JSONObject x=actions.optJSONObject(i);
            if(x!=null && "instagram_action".equals(x.optString("type")))instagram=true;
        }
        String[] list=instagram
                ? new String[]{"עשה לייק לפוסט הזה","פתח את ההודעות","עבור לרילס","חפש באינסטגרם","עבור לפוסט הבא","שמור את הפוסט הזה","שתף את הפוסט הזה","עבור לפרופיל"}
                : new String[]{"פתח את ההגדרות","פתח את Chrome","חזור אחורה","עבור למסך הבית","פתח את ההתראות","העלה את עוצמת הקול","גלול למטה","פתח את המצלמה"};
        for(String s:list)a.put(s);
        return a;
    }

    private void showSuggestions(JSONArray suggestions){
        if(suggestionsTitle==null||suggestionsList==null)return;
        suggestionsList.removeAllViews();
        if(suggestions==null||suggestions.length()==0){
            suggestionsTitle.setVisibility(View.GONE);
            suggestionsList.getParent();
            if(suggestionsList.getParent() instanceof View)((View)suggestionsList.getParent()).setVisibility(View.GONE);
            return;
        }
        suggestionsTitle.setVisibility(View.VISIBLE);
        if(suggestionsList.getParent() instanceof View)((View)suggestionsList.getParent()).setVisibility(View.VISIBLE);
        for(int i=0;i<suggestions.length();i++){
            String text=suggestions.optString(i,"").trim();
            if(text.isEmpty())continue;
            Button b=new Button(this);
            b.setText(text);
            b.setTextSize(12);
            b.setAllCaps(false);
            b.setTextColor(Color.rgb(55,56,75));
            b.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            b.setPadding(14,0,14,0);
            b.setBackground(bg(Color.rgb(245,245,250),18));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,42);
            lp.setMargins(3,2,3,2);
            suggestionsList.addView(b,lp);
            b.setOnClickListener(v->{
                String command=((Button)v).getText().toString();
                if(waitingForConfirmation){pendingActions=null;waitingForConfirmation=false;hidePlan();}
                onText(command);
            });
        }
    }

    private void hidePlan(){
        if(chatMessage!=null)chatMessage.setVisibility(View.GONE);
        if(chatConfirm!=null)chatConfirm.setVisibility(View.GONE);
    }

    private void confirmPendingActions(){
        if(!waitingForConfirmation||pendingActions==null)return;
        JSONArray actions=pendingActions;
        pendingActions=null;
        waitingForConfirmation=false;
        hidePlan();
        setMode("⚙  מבצע…","מבצע רק לאחר האישור שלך");
        ActionResult ar=runActions(actions);
        if(ar.failed==0){
            setMode("●  מוכן","בוצע. אפשר לתת פקודה נוספת");
            // ללא דיבור אוטומטי
        }else{
            setMode("⚠  חלקי","חלק מהפעולות לא בוצעו");
            // ללא דיבור אוטומטי
        }
    }

    private void cancelPendingConfirmation(){
        pendingActions=null;
        waitingForConfirmation=false;
        hidePlan();
        setMode("●  מוכן","לא בוצע דבר");
        // ללא דיבור אוטומטי
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
        RuntimeLogger.log(this,"MIC","start requested");
        if(voice==null){RuntimeLogger.log(this,"MIC","FAIL voice=null");return;}
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            setMode("🎙  דבר","נדרשת הרשאת מיקרופון");
            Intent i=new Intent(this,MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            return;
        }
        setMode("🎙  מקשיב","תגיד לי מה לעשות");
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
        RuntimeLogger.log(this,"COMMAND_RECEIVED","text="+(text==null?"<null>":text));
        if(text==null||text.trim().isEmpty()){RuntimeLogger.log(this,"COMMAND_REJECTED","empty transcript");return;}
        String normalized=text.trim().toLowerCase(java.util.Locale.ROOT);
        boolean asksToCloseApp=normalized.matches(".*(תסגור|סגור|סגר|סגרות|סגורת|סגורו|לסגור|תסגר|סגור את|close|quit|exit).*") &&
                normalized.matches(".*(אפליקציה|אפליקצייה|אפליקציה|אפליקצ|app|application|תוכנה).*");
        if(asksToCloseApp){
            RuntimeLogger.log(this,"FAST_PATH","close_current_app text="+text);
            setMode("⚙  סוגר…","סוגר את האפליקציה הנוכחית");
            boolean ok=ActionEngine.closeCurrentApp();
            RuntimeLogger.log(this,"FAST_PATH_RESULT","close_current_app="+ok);
            // ללא דיבור אוטומטי
            return;
        }
        if(waitingForConfirmation){
            if(isYes(text)){ confirmPendingActions(); return; }
            if(isNo(text)){ cancelPendingConfirmation(); return; }
            pendingActions=null;
            waitingForConfirmation=false;
            hidePlan();
        }
        setMode("⚙  מנתח…","בודק מה הבנתי ומה חסר");
        ApiClient.chat(text,new ApiClient.Callback(){
            @Override public void success(JSONObject result){
                JSONArray actions=result.optJSONArray("actions");
                if(actions==null||actions.length()==0){
                    // ללא דיבור אוטומטי
                    return;
                }
                pendingActions=actions;
                waitingForConfirmation=true;
                String reply=result.optString("reply","");
                String summary=understood(actions);
                String unclear=result.optString("unclear","");
                showSuggestions(buildSuggestions(actions));
                StringBuilder plan=new StringBuilder("הבנתי: ").append(summary).append(".");
                if(!unclear.trim().isEmpty())plan.append("
לא הבנתי: ").append(unclear.trim()).append(".");
                if(!reply.trim().isEmpty() && !reply.equals("בסדר, מבצע את זה עכשיו."))plan.append("
").append(reply.trim());
                plan.append("

לחץ על אישור כדי שאבצע. תיקון כדי לתקן.");
                showPlan(plan.toString());
                setMode("✓  ממתין לאישור","בדוק את מה שהבנתי לפני ביצוע");
                RuntimeLogger.log(FloatingAgentService.this,"WAITING_CONFIRMATION","actions="+actions.length()+" command="+text);
            }
            @Override public void error(String message){
                setMode("⚠  לא הצלחתי","הפעולה נכשלה — ממשיך להקשיב");
                showErrorCopy(message,text);
                // ללא דיבור אוטומטי
            }
        });
    }

    @Override public void onState(String s){
        RuntimeLogger.log(this,"VOICE_STATE",String.valueOf(s));
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
                case "open_url": if(!ActionEngine.openUrl(this,x.optString("url")))throw new IllegalStateException("open_url failed");break;
                case "open_app": if(!ActionEngine.openApp(this,x.optString("package")))throw new IllegalStateException("open_app failed: "+x.optString("package"));break;
                case "instagram_action": {
                    AgentAccessibilityService s=AgentAccessibilityService.getInstance();
                    if(s==null)throw new IllegalStateException("accessibility unavailable");
                    String ia=x.optString("action","").toLowerCase(java.util.Locale.ROOT);
                    String val=x.optString("value","");
                    boolean ok=true;
                    if("like".equals(ia))ok=s.performActionWithFallback("LIKE","","");
                    else if("follow".equals(ia))ok=s.performActionWithFallback("FOLLOW","","");
                    else if("comment".equals(ia)||"type_comment".equals(ia)){ok=s.performActionWithFallback("TYPE_TEXT",val,"");}
                    else if("send".equals(ia))ok=s.performActionWithFallback("SEND_TEXT",val,"");
                    else if("next".equals(ia))ok=s.performActionWithFallback("SWIPE","","up");
                    else if("previous".equals(ia))ok=s.performActionWithFallback("SWIPE","","down");
                    else if("back".equals(ia))ok=ActionEngine.back();
                    else if("home".equals(ia))ok=s.performActionWithFallback("CLICK_TEXT","Home|בית","");
                    else if("reels".equals(ia))ok=s.performActionWithFallback("CLICK_TEXT","Reels|רילס","");
                    else if("stories".equals(ia))ok=s.performActionWithFallback("CLICK_TEXT","Stories|סטורי|סיפורים","");
                    else if("messages".equals(ia))ok=s.performActionWithFallback("CLICK_TEXT","Messages|הודעות","");
                    else if("profile".equals(ia))ok=s.performActionWithFallback("CLICK_TEXT","Profile|פרופיל","");
                    else if("search".equals(ia))ok=s.performActionWithFallback("CLICK_TEXT","Search|חיפוש","");
                    else if("type_search".equals(ia))ok=s.performActionWithFallback("TYPE_TEXT",val,"");
                    else if("save".equals(ia))ok=s.performActionWithFallback("CLICK_CONTENT_DESCRIPTION","Save|שמירה|שמור","");
                    else if("share".equals(ia))ok=s.performActionWithFallback("CLICK_CONTENT_DESCRIPTION","Share|שיתוף|שתף","");
                    else if("unfollow".equals(ia))ok=s.performActionWithFallback("CLICK_TEXT","Following|עוקב|נעקבים","");
                    else if("open_result".equals(ia))ok=s.performActionWithFallback("CLICK_TEXT",val,"");
                    else if("new_post".equals(ia))ok=s.performActionWithFallback("CLICK_CONTENT_DESCRIPTION","New post|פוסט חדש|יצירה","");
                    if(!ok)throw new IllegalStateException("instagram action failed: "+ia);
                    break;
                }
                case "close_app": if(!ActionEngine.closeCurrentApp())throw new IllegalStateException("close_app failed");break;
                case "close_current_app": if(!ActionEngine.closeCurrentApp())throw new IllegalStateException("close_current_app failed");break;
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
                case "screen_info": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s!=null){ /* מידע נשאר בצ׳אט ללא הקראה */ }break;}
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
        if("bottom".equalsIgnoreCase(position)||"למטה".equals(position)){ overlayLp.gravity=Gravity.BOTTOM|Gravity.LEFT; overlayLp.x=0; overlayLp.y=24; }
        else if("top".equalsIgnoreCase(position)||"למעלה".equals(position)){ overlayLp.gravity=Gravity.TOP|Gravity.LEFT; overlayLp.x=0; overlayLp.y=80; }
        else { overlayLp.gravity=Gravity.TOP|Gravity.LEFT; overlayLp.y=Math.max(8,overlayLp.y); }
        try{wm.updateViewLayout(bar,overlayLp);}catch(Exception ignored){}
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&ACTION_UPDATE_NOTIFICATION.equals(i.getAction()))updateNotification(i.getStringExtra("text"));
        return START_NOT_STICKY;
    }

    @Override public void onDestroy(){
        RuntimeLogger.log(this,"APP","floating_service_onDestroy");
        if(voice!=null){voice.destroy();voice=null;}
        if(wm!=null&&bar!=null){try{wm.removeView(bar);}catch(Exception ignored){}}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i){return null;}
}
