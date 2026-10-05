package com.arilifelong.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class AgentAccessibilityService extends AccessibilityService {
    private static AgentAccessibilityService instance;
    public static AgentAccessibilityService getInstance(){ return instance; }

    @Override public void onServiceConnected(){ super.onServiceConnected(); instance=this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}

    public boolean clickText(String text){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return false;
        return clickRecursive(root,text);
    }
    private boolean clickRecursive(AccessibilityNodeInfo n,String text){
        if(n==null)return false;
        CharSequence t=n.getText();
        CharSequence d=n.getContentDescription();
        if((t!=null&&text.equalsIgnoreCase(t.toString()))||(d!=null&&text.equalsIgnoreCase(d.toString()))){
            if(n.isClickable()) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        for(int i=0;i<n.getChildCount();i++) if(clickRecursive(n.getChild(i),text)) return true;
        return false;
    }
    public boolean globalBack(){return performGlobalAction(GLOBAL_ACTION_BACK);}
    public boolean home(){return performGlobalAction(GLOBAL_ACTION_HOME);}
    public boolean recents(){return performGlobalAction(GLOBAL_ACTION_RECENTS);}
    public boolean tap(float x,float y){
        if(android.os.Build.VERSION.SDK_INT<24)return false;
        Path p=new Path();p.moveTo(x,y);
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,50)).build(),null,null);
    }
    public boolean typeText(String text){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return false;
        AccessibilityNodeInfo target=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if(target==null)return false;
        Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);
    }
}
