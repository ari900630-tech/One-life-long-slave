package com.arilifelong.agent;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import java.util.Locale;

public final class ActionEngine {
    private ActionEngine(){}
    private static Intent ready(Context c, Intent i){ return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); }

    public static boolean openUrl(Context c,String url){
        try { c.startActivity(ready(c,new Intent(Intent.ACTION_VIEW,Uri.parse(url)))); return true; } catch(Exception e){ return false; }
    }
    public static boolean openApp(Context c,String pkg){
        try {
            Intent i=c.getPackageManager().getLaunchIntentForPackage(pkg);
            if(i==null)return false;
            c.startActivity(ready(c,i)); return true;
        } catch(Exception e){ return false; }
    }
    public static boolean dial(Context c,String number){
        try { c.startActivity(ready(c,new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(number))))); return true; } catch(Exception e){ return false; }
    }
    public static boolean call(Context c,String number){
        if(androidx.core.content.ContextCompat.checkSelfPermission(c,android.Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED)return dial(c,number);
        try { c.startActivity(ready(c,new Intent(Intent.ACTION_CALL,Uri.parse("tel:"+Uri.encode(number))))); return true; } catch(Exception e){ return dial(c,number); }
    }
    public static boolean sms(Context c,String number,String text){
        try {
            Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(number)));
            i.putExtra("sms_body",text); c.startActivity(ready(c,i)); return true;
        } catch(Exception e){ return false; }
    }
    public static boolean email(Context c,String address,String subject,String body){
        try {
            Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+Uri.encode(address)));
            i.putExtra(Intent.EXTRA_SUBJECT,subject); i.putExtra(Intent.EXTRA_TEXT,body);
            c.startActivity(ready(c,i)); return true;
        } catch(Exception e){ return false; }
    }
    public static boolean maps(Context c,String query){
        return openUrl(c,"https://www.google.com/maps/search/?api=1&query="+Uri.encode(query));
    }
    public static boolean camera(Context c){
        try { c.startActivity(ready(c,new Intent("android.media.action.IMAGE_CAPTURE"))); return true; } catch(Exception e){ return false; }
    }
    public static boolean settings(Context c){
        try { c.startActivity(ready(c,new Intent(Settings.ACTION_SETTINGS))); return true; } catch(Exception e){ return false; }
    }
    public static boolean appSettings(Context c,String pkg){
        try { c.startActivity(ready(c,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg)))); return true; } catch(Exception e){ return false; }
    }
    public static boolean playStoreSearch(Context c,String query){
        try { return openUrl(c,"https://play.google.com/store/search?q="+Uri.encode(query)+"&c=apps"); } catch(Exception e){ return false; }
    }
    public static boolean notifications(Context c){
        return AgentAccessibilityService.getInstance()!=null && AgentAccessibilityService.getInstance().notifications();
    }
    public static boolean quickSettings(Context c){
        return AgentAccessibilityService.getInstance()!=null && AgentAccessibilityService.getInstance().quickSettings();
    }
    public static boolean back(){return AgentAccessibilityService.getInstance()!=null&&AgentAccessibilityService.getInstance().globalBack();}
    public static boolean home(){return AgentAccessibilityService.getInstance()!=null&&AgentAccessibilityService.getInstance().home();}
    public static boolean recents(){return AgentAccessibilityService.getInstance()!=null&&AgentAccessibilityService.getInstance().recents();}
}