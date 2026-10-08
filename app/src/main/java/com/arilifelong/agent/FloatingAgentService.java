package com.arilifelong.agent;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class FloatingAgentService extends Service implements VoiceEngine.Listener {
    private static volatile FloatingAgentService activeInstance;
    private volatile boolean actionExecutionActive=false;
    private volatile boolean instagramWasReached=false;
    private volatile boolean cancelRequested=false;
    private volatile String activeCommand="";
    private volatile long commandGeneration=0;
    private WindowManager wm;
    private View bar;
    private TextView status;
    private Button talk;
    private Button chatToggle;
    private LinearLayout chatPanel;
    private WindowManager.LayoutParams chatLp;
    private EditText chatInput;
    private TextView chatMessage;
    private TextView historyView;
    private ScrollView historyScroll;
    private Button historyButton;
    private Button copyHistoryButton;
    private final java.util.ArrayList<String> conversationHistory=new java.util.ArrayList<>();
    private static final String HISTORY_PREF="agent_conversation_history";
    private TextView suggestionsTitle;
    private LinearLayout suggestionsList;
    private ScrollView suggestionsScroll;
    private LinearLayout chatConfirm;
    private VoiceEngine voice;
    private static final String CHANNEL="agent_floating";
    private static final int NOTIFICATION_ID=7;
    public static final String ACTION_UPDATE_NOTIFICATION="com.arilifelong.agent.UPDATE_NOTIFICATION";
    private String notificationText="הסוכן פעיל";
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
        activeInstance=this;
        RuntimeLogger.init(this,"floating_service_onCreate");
        RuntimeLogger.log(this,"APP","package="+getPackageName()+" android="+Build.VERSION.RELEASE+" sdk="+Build.VERSION.SDK_INT);
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        loadConversationHistory();
        voice=new VoiceEngine(getApplicationContext(),this);
        createChatPanel();
ApiClient.startRemotePolling(getApplicationContext(), cmd -> { if(cmd!=null&&!cmd.trim().isEmpty()) onText(cmd); });
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
}

    private void loadConversationHistory(){
        try{
            String raw=getSharedPreferences(HISTORY_PREF,0).getString("items","[]");
            JSONArray a=new JSONArray(raw);
            conversationHistory.clear();
            for(int i=0;i<a.length();i++) conversationHistory.add(a.optString(i,""));
        }catch(Exception e){ conversationHistory.clear(); }
    }

    private void saveConversationHistory(){
        try{
            JSONArray a=new JSONArray();
            int start=Math.max(0,conversationHistory.size()-500);
            for(int i=start;i<conversationHistory.size();i++) a.put(conversationHistory.get(i));
            getSharedPreferences(HISTORY_PREF,0).edit().putString("items",a.toString()).apply();
        }catch(Exception ignored){}
    }

    private void addConversation(String speaker,String text){
        if(text==null||text.trim().isEmpty())return;
        String clean=text.trim();
        if(clean.length()>4000)clean=clean.substring(0,4000)+"…";
        synchronized(conversationHistory){
            conversationHistory.add(speaker+": "+clean);
            while(conversationHistory.size()>500)conversationHistory.remove(0);
        }
        saveConversationHistory();
        refreshHistoryView();
    }

    private String allConversationText(){
        synchronized(conversationHistory){
            if(conversationHistory.isEmpty())return "אין עדיין שיחה."; 
            StringBuilder b=new StringBuilder();
            for(String line:conversationHistory)b.append(line).append("\\n\\n");
            return b.toString().trim();
        }
    }

    private void refreshHistoryView(){
        if(historyView==null)return;
        historyView.setText(allConversationText());
        historyView.post(()->{if(historyScroll!=null)historyScroll.fullScroll(View.FOCUS_DOWN);});
    }

    private void toggleHistory(){
        if(historyScroll==null)return;
        boolean show=historyScroll.getVisibility()!=View.VISIBLE;
        historyScroll.setVisibility(show?View.VISIBLE:View.GONE);
        if(copyHistoryButton!=null)copyHistoryButton.setVisibility(show?View.VISIBLE:View.GONE);
        if(suggestionsTitle!=null&&show)suggestionsTitle.setVisibility(View.GONE);
        if(suggestionsScroll!=null&&show)suggestionsScroll.setVisibility(View.GONE);
        if(historyButton!=null)historyButton.setText(show?"שיחה ▲":"שיחה");
        if(show)refreshHistoryView();
    }

    private void copyConversation(){
        android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm!=null)cm.setPrimaryClip(android.content.ClipData.newPlainText("היסטוריית שיחה",allConversationText()));
        if(historyButton!=null)historyButton.setText("הועתק ✓");
        new Handler(Looper.getMainLooper()).postDelayed(()->{if(historyButton!=null)historyButton.setText("שיחה ▲");},1200);
    }

    private void createChatPanel(){
        if(wm==null)wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        if(wm==null||!Settings.canDrawOverlays(this))return;

        chatPanel=new LinearLayout(this);
        chatPanel.setOrientation(LinearLayout.VERTICAL);
        chatPanel.setGravity(Gravity.CENTER_VERTICAL);
        chatPanel.setPadding(14,12,14,12);
        chatPanel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable shell=bg(Color.rgb(247,248,252),30);
        shell.setStroke(1,Color.rgb(225,226,235));
        chatPanel.setBackground(shell);
        chatPanel.setElevation(18f);

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title=label("הסוכן שלי",20,Color.rgb(23,24,39));
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        TextView subtitle=label("עוזר אישי שמבצע פעולות בטלפון",11,Color.rgb(119,121,138));
        LinearLayout titleBox=new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(title,new LinearLayout.LayoutParams(-1,30));
        titleBox.addView(subtitle,new LinearLayout.LayoutParams(-1,22));
        header.addView(titleBox,new LinearLayout.LayoutParams(0,56,1));
        TextView logo=label("✦",22,Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(bg(Color.rgb(103,87,217),18));
        header.addView(logo,new LinearLayout.LayoutParams(52,52));

        LinearLayout statusCard=new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(12,8,12,8);
        statusCard.setBackground(bg(Color.WHITE,18));
        TextView statusCaption=label("מצב הסוכן",11,Color.rgb(122,124,140));
        status=label("מוכן להקשיב",15,Color.rgb(32,33,51));
        status.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        statusCard.addView(statusCaption,new LinearLayout.LayoutParams(-1,20));
        statusCard.addView(status,new LinearLayout.LayoutParams(-1,28));

        // Voice-only interface: no text input or send button.
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
        Button mic=new Button(this);
        mic.setText("🎙");
        mic.setTextSize(15);
        mic.setAllCaps(false);
        mic.setTextColor(Color.rgb(55,56,75));
        mic.setBackground(bg(Color.rgb(245,245,250),18));
        mic.setOnClickListener(v->startVoiceInput());
        talk=mic;

        historyButton=new Button(this);
        historyButton.setText("שיחה");
        historyButton.setTextSize(11);
        historyButton.setAllCaps(false);
        historyButton.setTextColor(Color.rgb(55,56,75));
        historyButton.setBackground(bg(Color.rgb(245,245,250),18));
        historyButton.setOnClickListener(v->toggleHistory());

        copyHistoryButton=new Button(this);
        copyHistoryButton.setText("העתק הכול");
        copyHistoryButton.setTextSize(11);
        copyHistoryButton.setAllCaps(false);
        copyHistoryButton.setTextColor(Color.WHITE);
        copyHistoryButton.setBackground(bg(Color.rgb(103,87,217),18));
        copyHistoryButton.setOnClickListener(v->copyConversation());
        copyHistoryButton.setVisibility(View.GONE);

        LinearLayout topRow=new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        topRow.addView(dragHandle,new LinearLayout.LayoutParams(26,48));
        topRow.addView(mic,new LinearLayout.LayoutParams(0,44,1));
        topRow.addView(historyButton,new LinearLayout.LayoutParams(58,44));
        topRow.addView(close,new LinearLayout.LayoutParams(38,48));

        historyScroll=new ScrollView(this);
        historyScroll.setFillViewport(true);
        historyScroll.setVerticalScrollBarEnabled(true);
        historyView=label("",13,Color.rgb(45,46,60));
        historyView.setGravity(Gravity.RIGHT|Gravity.TOP);
        historyView.setTextIsSelectable(true);
        historyView.setPadding(10,8,10,8);
        historyScroll.addView(historyView,new ScrollView.LayoutParams(-1,-2));
        historyScroll.setVisibility(View.GONE);
        // Build the suggestions views before adding them to the panel.
        suggestionsTitle=label("אפשר לבקש גם:",13,Color.rgb(90,91,105));
        suggestionsTitle.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        suggestionsTitle.setPadding(10,4,10,2);
        suggestionsTitle.setVisibility(View.GONE);

        suggestionsScroll=new ScrollView(this);
        suggestionsScroll.setFillViewport(true);
        suggestionsScroll.setVerticalScrollBarEnabled(false);
        suggestionsList=new LinearLayout(this);
        suggestionsList.setOrientation(LinearLayout.VERTICAL);
        suggestionsList.setPadding(4,0,4,2);
        suggestionsScroll.addView(suggestionsList,new ScrollView.LayoutParams(-1,-2));
        suggestionsScroll.setVisibility(View.GONE);

        // Same visual hierarchy as the main app: header, status card, then controls.
        chatPanel.addView(header,new LinearLayout.LayoutParams(-1,58));
        chatPanel.addView(statusCard,new LinearLayout.LayoutParams(-1,56));
        chatPanel.addView(suggestionsTitle,new LinearLayout.LayoutParams(-1,32));
        chatPanel.addView(suggestionsScroll,new LinearLayout.LayoutParams(-1,170));
        chatPanel.addView(topRow,new LinearLayout.LayoutParams(-1,48));
        chatPanel.addView(historyScroll,new LinearLayout.LayoutParams(-1,210));
        chatPanel.addView(copyHistoryButton,new LinearLayout.LayoutParams(-1,42));

        LinearLayout.LayoutParams messageLp=new LinearLayout.LayoutParams(-1,58);
        chatPanel.addView(chatMessage,messageLp);

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
            refreshHistoryView();
        }catch(Exception e){RuntimeLogger.log(this,"CHAT_ERROR","add_panel="+e);}
    }

    private void closeChatPanel(){
        try{
            if(wm!=null && chatPanel!=null){ wm.removeView(chatPanel); }
        }catch(Exception ignored){}
        chatPanel=null;
        chatInput=null;
        chatMessage=null;
        historyView=null;
        historyScroll=null;
        historyButton=null;
        copyHistoryButton=null;
        talk=null;
        suggestionsList=null;
        suggestionsTitle=null;
        suggestionsScroll=null;
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
            waitingForConfirmation=false;
            hidePlan();
            setMode("⚙  מבצע…","מבצע את ההצעה שנבחרה");
            ActionResult ar=runActions(a);
            setMode(ar.failed==0?"●  מוכן":"⚠  חלקי",ar.failed==0?"בוצע. אפשר לתת פקודה נוספת":"חלק מהפעולות לא בוצעו");
        }catch(Exception e){ onText(q); }
    }

    private void executeSelectedSuggestion(String command){
        if(command==null||command.trim().isEmpty())return;
        final String q=command.trim();
        RuntimeLogger.log(this,"SUGGESTION_SELECTED","command="+q);
        setMode("⚙  מבצע…","מבצע את ההצעה שנבחרה");
        ApiClient.chat(q,new ApiClient.Callback(){
            @Override public void success(JSONObject result){
                JSONArray actions=result.optJSONArray("actions");
                if(actions==null||actions.length()==0){
                    setMode("⚠  לא הצלחתי","לא נמצאה פעולה לביצוע");
                    return;
                }
                hidePlan();
                showSuggestions(new JSONArray());
                ActionResult ar=runActions(actions);
                String resultText=ar.failed==0?"סיימתי. אפשר לבקש ממני משהו נוסף.":"חלק מהפעולות לא הצליחו. אפשר לנסות שוב.";
                addConversation("הסוכן",resultText);
                if(voice!=null)voice.speak(resultText);
                setMode(ar.failed==0?"●  מוכן":"⚠  חלקי",resultText);
                RuntimeLogger.log(FloatingAgentService.this,"SUGGESTION_EXECUTED","actions="+actions.length()+" command="+q+" failed="+ar.failed);
            }
            @Override public void error(String message){
                String errText="לא הצלחתי לבצע את זה. אפשר לנסות שוב.";
                addConversation("הסוכן",errText);
                if(voice!=null)voice.speak(errText);
                setMode("⚠  לא הצלחתי",errText);
                RuntimeLogger.log(FloatingAgentService.this,"SUGGESTION_ERROR",String.valueOf(message));
            }
        });
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

    private void updateLiveSuggestions(String input){
        if(suggestionsTitle==null||suggestionsList==null||suggestionsScroll==null)return;
        String q=input==null?"":input.trim().toLowerCase(java.util.Locale.ROOT);
        if(q.isEmpty()){ suggestionsList.removeAllViews(); suggestionsTitle.setVisibility(View.GONE); suggestionsScroll.setVisibility(View.GONE); return; }
        java.util.ArrayList<String> all=new java.util.ArrayList<>();
        if(q.contains("אינסט")||q.contains("ינסט")||q.contains("ינסט")||q.contains("instagram")){
            java.util.Collections.addAll(all,"פתח את אינסטגרם","פתח את ההודעות באינסטגרם","עבור לרילס באינסטגרם","עבור לפוסט הבא","עשה לייק לפוסט הזה","שמור את הפוסט הזה","שתף את הפוסט הזה","עבור לפרופיל","חפש באינסטגרם","עקוב אחרי הפרופיל הזה","הפסק לעקוב מהפרופיל הזה","פתח את הסטורי","חזור באינסטגרם","עבור לדף הבית באינסטגרם","כתוב תגובה לפוסט הזה","פתח את הפוסט הבא ושמור אותו","עשה לייק לפוסט ועבור לפוסט הבא","פתח את ההודעות ושלח הודעה","חפש את המשתמש הזה באינסטגרם","פתח את תוצאות החיפוש באינסטגרם");
        } else if(q.contains("פתח")||q.contains("פת")||q.contains("open")){
            java.util.Collections.addAll(all,"פתח את אינסטגרם","פתח את Chrome","פתח את ההגדרות","פתח את המצלמה","פתח את ההודעות","פתח את ההתראות","פתח את חנות Play","פתח את מסך הבית","פתח את האפליקציה האחרונה","פתח את ההגדרות של האפליקציה הנוכחית","פתח את מנהל האפליקציות","פתח את אנשי הקשר","פתח את הטלפון","פתח את המפות","פתח את חיפוש Google","פתח את חלון ההתראות","פתח את ההגדרות המהירות");
        } else if(q.contains("חפש")||q.contains("חיפוש")||q.contains("search")){
            java.util.Collections.addAll(all,"חפש באינסטגרם","חפש ב-Google","חפש ב-Chrome","פתח את תוצאות החיפוש","נקה את החיפוש","חפש את המשתמש הזה באינסטגרם","חפש את האפליקציה הזו בחנות Play","חפש את האתר הזה");
        } else {
            java.util.Collections.addAll(all,"פתח את אינסטגרם","פתח את Chrome","פתח את ההגדרות","עבור למסך הבית","חזור אחורה","גלול למטה","גלול למעלה","פתח את ההתראות","פתח את המצלמה","העלה את עוצמת הקול","הורד את עוצמת הקול","צלם מסך","פתח את ההודעות","פתח את חנות Play","עבור לאפליקציה האחרונה","סגור את האפליקציה הנוכחית","פתח את המפות","פתח את אנשי הקשר","פתח את הטלפון","הפעל מצב טיסה");
        }
        final String[] tokens=q.split("\\s+");
        java.util.Collections.sort(all,(a,b)->{
            int sa=0,sb=0; String la=a.toLowerCase(java.util.Locale.ROOT),lb=b.toLowerCase(java.util.Locale.ROOT);
            for(String t:tokens){ if(t.length()<2)continue; if(la.contains(t))sa+=10; if(lb.contains(t))sb+=10; }
            return Integer.compare(sb,sa);
        });
        JSONArray result=new JSONArray();
        for(String s:all)result.put(s);
        showSuggestions(result);
        suggestionsTitle.setText("הצעות בזמן אמת • "+all.size());
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
                executeSelectedSuggestion(command);
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
            String doneText="סיימתי. אפשר לבקש ממני משהו נוסף.";
            addConversation("הסוכן",doneText);
            if(voice!=null)voice.speak(doneText);
            setMode("●  מוכן",doneText);
        }else{
            String partialText="חלק מהפעולות לא הצליחו. אפשר לנסות שוב.";
            addConversation("הסוכן",partialText);
            if(voice!=null)voice.speak(partialText);
            setMode("⚠  חלקי",partialText);
        }
    }

    private void cancelPendingConfirmation(){
        pendingActions=null;
        waitingForConfirmation=false;
        hidePlan();
        String cancelText="בסדר, לא ביצעתי את הפעולה.";
        addConversation("הסוכן",cancelText);
        if(voice!=null)voice.speak(cancelText);
        setMode("●  מוכן",cancelText);
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
        activeCommand=text==null?"":text.trim();
        final long requestGeneration=++commandGeneration;
        RuntimeLogger.log(this,"COMMAND_RECEIVED","text="+(text==null?"<null>":text)+"|generation="+requestGeneration);
        if(text==null||text.trim().isEmpty()){RuntimeLogger.log(this,"COMMAND_REJECTED","empty transcript");return;}
        addConversation("אתה",text);
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
                    String unclearText="לא הצלחתי להבין מה לבצע. תסביר לי קצת אחרת.";
                    addConversation("הסוכן",unclearText);
                    if(voice!=null)voice.speak(unclearText);
                    return;
                }
                pendingActions=null;
                waitingForConfirmation=false;
                hidePlan();
                showSuggestions(new JSONArray());
                ActionResult ar=runActions(actions);
                String resultText=ar.failed==0?"בוצע.":"חלק מהפעולות לא הצליחו.";
                addConversation("הסוכן",resultText);
                if(voice!=null)voice.speak(resultText);
                setMode(ar.failed==0?"●  מוכן":"⚠  חלקי",resultText);
                RuntimeLogger.log(FloatingAgentService.this,"ACTIONS_EXECUTED_DIRECTLY","actions="+actions.length()+" command="+text+" failed="+ar.failed);
            }
            @Override public void error(String message){
                if(requestGeneration!=commandGeneration){
                    RuntimeLogger.log(FloatingAgentService.this,"COMMAND_STALE","ignored error generation="+requestGeneration+" current="+commandGeneration+" text="+text);
                    return;
                }
                String errorText="נתקלתי בבעיה בביצוע הבקשה. אפשר לנסות שוב.";
                addConversation("הסוכן",errorText+" ["+String.valueOf(message)+"]");
                if(voice!=null)voice.speak(errorText);
                setMode("⚠  לא הצלחתי",errorText);
                showErrorCopy(message,text);
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
    private boolean instagramOnlyAllowed(String type, JSONObject x){
        if(type==null)return false;
        if("open_app".equals(type)) return "com.instagram.android".equals(x.optString("package",""));
        return "instagram_action".equals(type) || "click_text".equals(type) || "click_content_description".equals(type)
                || "click_role".equals(type) || "type_text".equals(type) || "send_text".equals(type)
                || "long_click_text".equals(type) || "tap".equals(type) || "long_click".equals(type)
                || "swipe".equals(type) || "swipe_direction".equals(type) || "scroll".equals(type)
                || "scroll_repeat".equals(type) || "scroll_until_text".equals(type) || "click_repeat".equals(type)
                || "screen_info".equals(type) || "screenshot".equals(type) || "back".equals(type)
                || "like".equals(type) || "follow".equals(type) || "approve".equals(type)
                || "open_chat_menu".equals(type) || "pin".equals(type) || "press_send".equals(type);
    }

    private boolean instagramUiReady(){
        AgentAccessibilityService s=AgentAccessibilityService.getInstance();
        return s!=null && s.isInstagramActive();
    }

    public static void cancelIfInstagramExited(){
        FloatingAgentService s=activeInstance;
        if(s!=null && s.actionExecutionActive && s.instagramWasReached){
            s.cancelRequested=true;
            RuntimeLogger.log(s,"ACTION_CANCELLED","reason=user_left_instagram|command="+s.activeCommand);
            s.setMode("⏹  נעצר","הפעולה נעצרה כי יצאת מאינסטגרם");
        }
    }

    private long estimateActionMs(JSONObject x){
        String t=x==null?"":x.optString("type","");
        if("open_app".equals(t))return 900;
        if("instagram_action".equals(t)){
            String a=x.optString("action","");
            if("search".equals(a)||"submit_search".equals(a))return 900;
            if("send_text".equals(a))return 850;
            return 650;
        }
        if("type_text".equals(t)||"send_text".equals(t))return 650;
        if(t.startsWith("click")||t.startsWith("long_click"))return 600;
        if(t.startsWith("scroll")||t.startsWith("swipe"))return 700;
        return 600;
    }

    private String formatTime(long ms){
        double sec=ms/1000.0;
        if(sec<1)return "פחות משנייה";
        if(sec<60)return String.format(java.util.Locale.ROOT,"%.1f שנ׳",sec);
        return String.format(java.util.Locale.ROOT,"%d דק׳ %02d שנ׳",(long)(sec/60),((long)sec)%60);
    }

    private ActionResult runActions(JSONArray actions){
        ActionResult result=new ActionResult();
        if(actions==null)return result;
        result.total=actions.length();
        actionExecutionActive=true;
        cancelRequested=false;
        instagramWasReached=false;
        long executionStart=System.currentTimeMillis();
        long[] estimates=new long[actions.length()];
        long totalEstimate=0;
        for(int z=0;z<actions.length();z++){ estimates[z]=estimateActionMs(actions.optJSONObject(z)); totalEstimate+=estimates[z]; }
        try{
            for(int i=0;i<actions.length();i++){
                if(cancelRequested){
                    result.failed += actions.length()-i;
                    break;
                }
                long stepStart=System.currentTimeMillis();
                long remainingEstimate=0;
                for(int z=i;z<estimates.length;z++)remainingEstimate+=estimates[z];
                setMode("⚙ "+(i+1)+"/"+actions.length(),"שלב "+(i+1)+" מתוך "+actions.length()+" • זמן משוער עד סיום: "+formatTime(remainingEstimate));
                RuntimeLogger.log(this,"ACTION_STEP","START|"+(i+1)+"/"+actions.length()+"|eta="+remainingEstimate+"ms");

                JSONObject x=actions.getJSONObject(i);
                String t=x.optString("type");
                if(!instagramOnlyAllowed(t,x)){
                    result.failed++;
                    RuntimeLogger.log(this,"INSTAGRAM_ONLY_BLOCK","blocked action="+t+" package="+x.optString("package",""));
                    RuntimeLogger.log(this,"ACTION_STEP","FAILURE|"+(i+1)+"/"+actions.length()+"|reason=blocked");
                    continue;
                }

                if(!"open_app".equals(t) && !instagramUiReady()){
                    long waitUntil=System.currentTimeMillis()+2200;
                    while(!instagramUiReady() && System.currentTimeMillis()<waitUntil && !cancelRequested){
                        try{Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                    }
                    if(!instagramUiReady()){
                        result.failed++;
                        RuntimeLogger.log(this,"INSTAGRAM_ONLY_BLOCK","Instagram not active for action="+t);
                        RuntimeLogger.log(this,"ACTION_STEP","FAILURE|"+(i+1)+"/"+actions.length()+"|reason=instagram_not_active");
                        continue;
                    }
                }

                boolean transitionAction="open_app".equals(t);
                switch(t){
                    case "open_url": if(!ActionEngine.openUrl(this,x.optString("url")))throw new IllegalStateException("open_url failed");break;
                    case "open_app":
                        if(!ActionEngine.openApp(this,x.optString("package")))throw new IllegalStateException("open_app failed: "+x.optString("package"));
                        if("com.instagram.android".equals(x.optString("package",""))){
                            long waitUntil=System.currentTimeMillis()+4500;
                            while(!instagramUiReady() && System.currentTimeMillis()<waitUntil && !cancelRequested){
                                try{Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                            }
                            if(!instagramUiReady())throw new IllegalStateException("Instagram did not become active");
                            instagramWasReached=true;
                        }
                        break;
                    case "instagram_action": {
                        AgentAccessibilityService s=AgentAccessibilityService.getInstance();
                        if(s==null)throw new IllegalStateException("accessibility unavailable");
                        String ia=x.optString("action","").toLowerCase(java.util.Locale.ROOT);
                        String val=x.optString("value","");
                        if(!s.instagramAction(ia,val))throw new IllegalStateException("instagram action failed: "+ia);
                        break;
                    }
                    case "close_app": case "close_current_app": case "dial": case "call": case "sms": case "email": case "maps": case "camera": case "settings": case "settings_action": case "app_settings": case "play_store_search": case "home": case "recents": case "notifications": case "quick_settings": case "open_notifications": case "volume": case "brightness": case "system_action": case "chrome_new_tab": case "chrome_close_tab": case "chrome_next_tab": case "chrome_previous_tab": case "chrome_clear_search": case "uninstall_app": case "uninstall_current_app": case "open_notifications_and_click": case "long_click_notification": case "click_quick_setting": case "long_click_quick_setting":
                        throw new IllegalStateException("blocked_non_instagram_action");
                    case "move_overlay": moveOverlay(x.optString("position","top"));break;
                    case "long_click": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.longClick((float)x.optDouble("x",540),(float)x.optDouble("y",1000)))throw new IllegalStateException("long_click failed");break;}
                    case "tap": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.tap((float)x.optDouble("x",540),(float)x.optDouble("y",1000)))throw new IllegalStateException("tap failed");break;}
                    case "swipe": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.swipe((float)x.optDouble("x1",540),(float)x.optDouble("y1",1500),(float)x.optDouble("x2",540),(float)x.optDouble("y2",500),(long)x.optDouble("duration",600)))throw new IllegalStateException("swipe failed");break;}
                    case "scroll_repeat": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null)throw new IllegalStateException("accessibility unavailable");int n=Math.min(20,Math.max(1,x.optInt("count",3)));boolean fwd=!"back".equalsIgnoreCase(x.optString("direction"));for(int k=0;k<n;k++){if(!s.scroll(fwd))throw new IllegalStateException("scroll failed");try{Thread.sleep(Math.min(400,Math.max(50,x.optInt("delay",150))));}catch(Exception ignored){}}break;}
                    case "back": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.globalBack())throw new IllegalStateException("back failed");break;}
                    case "click_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("CLICK_TEXT",x.optString("text"),""))throw new IllegalStateException("click_text failed");break;}
                    case "click_content_description": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("CLICK_CONTENT_DESCRIPTION",x.optString("text",x.optString("target")),""))throw new IllegalStateException("click_content_description failed");break;}
                    case "click_role": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("CLICK_ROLE",x.optString("text",x.optString("role")),""))throw new IllegalStateException("click_role failed");break;}
                    case "long_click_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("LONG_CLICK_TEXT",x.optString("text",x.optString("target")),""))throw new IllegalStateException("long_click_text failed");break;}
                    case "type_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("TYPE_TEXT",x.optString("text"),""))throw new IllegalStateException("type_text failed");break;}
                    case "send_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("SEND_TEXT",x.optString("text"),""))throw new IllegalStateException("send_text failed");break;}
                    case "scroll": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("SCROLL","",x.optString("direction","down")))throw new IllegalStateException("scroll failed");break;}
                    case "swipe_direction": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("SWIPE","",x.optString("direction","up")))throw new IllegalStateException("swipe failed");break;}
                    case "like": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("LIKE","",""))throw new IllegalStateException("like failed");break;}
                    case "follow": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("FOLLOW","",""))throw new IllegalStateException("follow failed");break;}
                    case "approve": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.performActionWithFallback("APPROVE","",""))throw new IllegalStateException("approve failed");break;}
                    case "open_chat_menu": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.openChatMenu())throw new IllegalStateException("open_chat_menu failed");break;}
                    case "pin": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.pinItem())throw new IllegalStateException("pin failed");break;}
                    case "press_send": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.pressSend())throw new IllegalStateException("press_send failed");break;}
                    case "screen_info": case "screenshot": { AgentAccessibilityService s=AgentAccessibilityService.getInstance(); if(s==null)throw new IllegalStateException("accessibility unavailable"); if("screenshot".equals(t)&&!s.screenshot())throw new IllegalStateException("screenshot failed"); break; }
                    case "click_repeat": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||s.clickRepeat(x.optString("text"),Math.min(5,x.optInt("count",2)),x.optLong("delay",150))<=0)throw new IllegalStateException("click_repeat failed");break;}
                    case "scroll_until_text": {AgentAccessibilityService s=AgentAccessibilityService.getInstance();if(s==null||!s.scrollUntilText(x.optString("text"),!"back".equalsIgnoreCase(x.optString("direction")),Math.min(15,x.optInt("max",10)),Math.min(500,x.optLong("delay",150))))throw new IllegalStateException("scroll_until_text failed");break;}
                    default: throw new IllegalStateException("unsupported_action="+t);
                }

                result.succeeded++;
                long elapsed=System.currentTimeMillis()-stepStart;
                long overall=System.currentTimeMillis()-executionStart;
                long dynamicRemaining=Math.max(0,totalEstimate-overall);
                RuntimeLogger.log(this,"ACTION_STEP","SUCCESS|"+(i+1)+"/"+actions.length()+"|elapsed="+elapsed+"ms|eta="+dynamicRemaining+"ms");
                setMode("✓ "+(i+1)+"/"+actions.length(),"הצליחו "+result.succeeded+" • נכשלו "+result.failed+" • נשארו "+formatTime(dynamicRemaining));
            }
        }catch(Exception fatal){
            RuntimeLogger.log(this,"ACTION_RUN_ERROR",String.valueOf(fatal));
            result.failed++;
        }finally{
            actionExecutionActive=false;
            String summary="הצליחו "+result.succeeded+" מתוך "+result.total+" • נכשלו "+result.failed+" • זמן "+formatTime(System.currentTimeMillis()-executionStart);
            RuntimeLogger.log(this,"ACTION_SUMMARY","success="+result.succeeded+"|failed="+result.failed+"|total="+result.total+"|elapsed="+(System.currentTimeMillis()-executionStart)+"ms|command="+activeCommand);
            if(cancelRequested)setMode("⏹  נעצר",summary+" • הפעולה הופסקה");
            else setMode(result.failed==0?"●  מוכן":"⚠  חלקי",summary);
        }
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
        return START_STICKY;
    }

    @Override public void onDestroy(){
        activeInstance=null;
        actionExecutionActive=false;
        cancelRequested=true;
        RuntimeLogger.log(this,"APP","floating_service_onDestroy");
        ApiClient.stopRemotePolling();
        if(voice!=null){voice.destroy();voice=null;}
        if(wm!=null&&bar!=null){try{wm.removeView(bar);}catch(Exception ignored){}}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i){return null;}
}