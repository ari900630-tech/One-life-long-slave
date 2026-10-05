package com.arilifelong.agent;
import android.content.Context; import android.content.Intent; import android.net.Uri;
public final class ActionEngine {
 private ActionEngine(){}
 public static boolean openUrl(Context c,String url){try{c.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}catch(Exception e){return false;}}
 public static boolean back(){return AgentAccessibilityService.getInstance()!=null&&AgentAccessibilityService.getInstance().globalBack();}
 public static boolean home(){return AgentAccessibilityService.getInstance()!=null&&AgentAccessibilityService.getInstance().home();}
 public static boolean recents(){return AgentAccessibilityService.getInstance()!=null&&AgentAccessibilityService.getInstance().recents();}
}