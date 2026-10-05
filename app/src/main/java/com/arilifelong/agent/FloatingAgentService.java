package com.arilifelong.agent;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class FloatingAgentService extends Service {
    private WindowManager wm;
    private View bar;
    private static final String CHANNEL="agent_floating";
    private static final int NOTIFICATION_ID=7;
    public static final String ACTION_UPDATE_NOTIFICATION="com.arilifelong.agent.UPDATE_NOTIFICATION";
    private String notificationText="הסוכן הצף פעיל";

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
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
                .setStyle(new Notification.BigTextStyle().bigText(notificationText))
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String text) {
        if (text == null || text.trim().isEmpty()) return;
        notificationText=text.trim();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID,notification());
    }

    private void showBar() {
        if (!Settings.canDrawOverlays(this)) return;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(14,8,10,8);
        root.setBackgroundColor(Color.WHITE);

        TextView title=new TextView(this);
        title.setText("✦  הסוכן שלי");
        title.setTextColor(Color.DKGRAY);
        title.setTextSize(15);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title,new LinearLayout.LayoutParams(0,60,1));

        Button talk=new Button(this);
        talk.setText("🎙️ דבר");
        talk.setOnClickListener(v->openAgent());
        root.addView(talk,new LinearLayout.LayoutParams(-2,56));

        Button close=new Button(this);
        close.setText("×");
        close.setOnClickListener(v->stopSelf());
        root.addView(close,new LinearLayout.LayoutParams(60,56));

        int type=Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,WindowManager.LayoutParams.WRAP_CONTENT,type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
        p.y=0;
        wm.addView(root,p);
        bar=root;
    }

    private void openAgent() {
        Intent i=new Intent(this,MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null && ACTION_UPDATE_NOTIFICATION.equals(i.getAction())){
            updateNotification(i.getStringExtra("text"));
        }
        return START_STICKY;
    }

    @Override public void onDestroy(){
        if(wm!=null&&bar!=null){try{wm.removeView(bar);}catch(Exception ignored){}}
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent i){return null;}
}
