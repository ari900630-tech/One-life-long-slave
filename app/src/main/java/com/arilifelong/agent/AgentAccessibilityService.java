package com.arilifelong.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

public class AgentAccessibilityService extends AccessibilityService {
    private static AgentAccessibilityService instance;
    public static AgentAccessibilityService getInstance(){ return instance; }

    @Override public void onServiceConnected(){ super.onServiceConnected(); instance=this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){ if(instance==this)instance=null; super.onDestroy(); }

    public boolean clickText(String text){
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return false;
        return clickRecursive(root,text,false);
    }
    public boolean clickContains(String text){
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return false;
        return clickRecursive(root,text,true);
    }
    private boolean clickRecursive(AccessibilityNodeInfo n,String text,boolean contains){
        if(n==null)return false;
        String q=text==null?"":text.toLowerCase(Locale.ROOT);
        CharSequence t=n.getText(), d=n.getContentDescription();
        String ts=t==null?"":t.toString().toLowerCase(Locale.ROOT);
        String ds=d==null?"":d.toString().toLowerCase(Locale.ROOT);
        boolean match=contains?(ts.contains(q)||ds.contains(q)):(ts.equals(q)||ds.equals(q));
        if(match){
            if(n.isClickable() && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            AccessibilityNodeInfo p=n.getParent();
            if(p!=null && p.isClickable() && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        for(int i=0;i<n.getChildCount();i++) if(clickRecursive(n.getChild(i),text,contains)) return true;
        return false;
    }
    public boolean setText(String text){
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return false;
        AccessibilityNodeInfo target=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT); if(target==null)return false;
        Bundle b=new Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);
    }
    public boolean scroll(boolean forward){
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return false;
        return scrollRecursive(root,forward);
    }
    private boolean scrollRecursive(AccessibilityNodeInfo n,boolean forward){
        if(n==null)return false;
        if(n.isScrollable()){
            int action=forward?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
            if(n.performAction(action))return true;
        }
        for(int i=0;i<n.getChildCount();i++) if(scrollRecursive(n.getChild(i),forward))return true;
        return false;
    }
    public boolean globalBack(){return performGlobalAction(GLOBAL_ACTION_BACK);}
    public boolean home(){return performGlobalAction(GLOBAL_ACTION_HOME);}
    public boolean recents(){return performGlobalAction(GLOBAL_ACTION_RECENTS);}
    public boolean notifications(){return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);}
    public boolean quickSettings(){return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);}
    public boolean longClick(float x,float y){
        if(android.os.Build.VERSION.SDK_INT<24)return false;
        Path p=new Path();p.moveTo(x,y);
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,650)).build(),null,null);
    }
    public boolean swipe(float x1,float y1,float x2,float y2,long duration){
        if(android.os.Build.VERSION.SDK_INT<24)return false;
        Path p=new Path();p.moveTo(x1,y1);p.lineTo(x2,y2);
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,Math.max(100,duration))).build(),null,null);
    }
    public boolean tap(float x,float y){
        if(android.os.Build.VERSION.SDK_INT<24)return false;
        Path p=new Path();p.moveTo(x,y);
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,50)).build(),null,null);
    }
}