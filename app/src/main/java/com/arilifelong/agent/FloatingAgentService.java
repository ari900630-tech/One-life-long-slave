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