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
        try{ Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("market://search?q="+Uri.encode(query))); i.setPackage("com.android.vending"); c.startActivity(ready(c,i)); return true; }
        catch(Exception e){ return openUrl(c,"https://play.google.com/store/search?q="+Uri.encode(query)+"&c=apps"); }
    }
    public static boolean volume(Context c,String stream,String direction){
        try{ android.media.AudioManager am=(android.media.AudioManager)c.getSystemService(Context.AUDIO_SERVICE); int s=android.media.AudioManager.STREAM_MUSIC;
            if("ring".equalsIgnoreCase(stream))s=android.media.AudioManager.STREAM_RING;
            if("alarm".equalsIgnoreCase(stream))s=android.media.AudioManager.STREAM_ALARM;
            int d="down".equalsIgnoreCase(direction)?android.media.AudioManager.ADJUST_LOWER:("mute".equalsIgnoreCase(direction)?android.media.AudioManager.ADJUST_MUTE:android.media.AudioManager.ADJUST_RAISE);
            am.adjustStreamVolume(s,d,0); return true;
        }catch(Exception e){return false;}
    }
    public static boolean brightness(Context c,int value){
        try{ if(!Settings.System.canWrite(c)){c.startActivity(ready(c,new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+c.getPackageName()))));return false;}
            return Settings.System.putInt(c.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,Math.max(1,Math.min(255,value)));
        }catch(Exception e){return false;}
    }
    public static boolean systemAction(Context c,String action){
        try{String a=action==null?"":action.toLowerCase(Locale.ROOT);Intent i;
            if(a.contains("wifi"))i=new Intent(Settings.Panel.ACTION_WIFI);
            else if(a.contains("bluetooth"))i=new Intent(Settings.Panel.ACTION_BLUETOOTH);
            else if(a.contains("internet")||a.contains("network"))i=new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
            else if(a.contains("display")||a.contains("screen"))i=new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            else if(a.contains("sound")||a.contains("volume"))i=new Intent(Settings.ACTION_SOUND_SETTINGS);
            else if(a.contains("battery"))i=new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            else if(a.contains("apps"))i=new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
            else i=new Intent(Settings.ACTION_SETTINGS);
            c.startActivity(ready(c,i));return true;
        }catch(Exception e){return false;}
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