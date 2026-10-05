package com.arilifelong.agent;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class AgentNotificationListener extends NotificationListenerService {
    private static AgentNotificationListener instance;
    private static volatile String latest="";
    public static AgentNotificationListener getInstance(){ return instance; }
    public static String getLatest(){ return latest; }

    @Override public void onListenerConnected(){ instance=this; }
    @Override public void onListenerDisconnected(){ if(instance==this)instance=null; }
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(sbn==null||sbn.getNotification()==null)return;
        CharSequence title=sbn.getNotification().extras.getCharSequence("android.title");
        CharSequence text=sbn.getNotification().extras.getCharSequence("android.text");
        latest=(title==null?"":title)+" — "+(text==null?"":text);
    }
}