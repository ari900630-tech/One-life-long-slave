package com.arilifelong.agent;

import android.content.Intent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import java.util.ArrayList;
import java.util.List;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class AgentAccessibilityService extends AccessibilityService {
    private static AgentAccessibilityService instance;
    private final List<String> diagnostics = new ArrayList<>();
    private final Handler handler = new Handler();
    private String lastAnnouncedScreen="";
    private long lastAnnouncedAt=0;
    public static AgentAccessibilityService getInstance(){ return instance; }

    @Override public void onServiceConnected(){ super.onServiceConnected(); instance=this; loadDiagnostics(); RuntimeLogger.init(this,"accessibility_service_connected"); RuntimeLogger.log(this,"ACCESSIBILITY","service connected"); }
    private void loadDiagnostics(){
        String saved=getSharedPreferences("agents_runtime",MODE_PRIVATE).getString("diagnostics","");
        if(saved!=null&&!saved.isEmpty()){ diagnostics.clear(); for(String x:saved.split("\\n")) if(!x.isEmpty()) diagnostics.add(x); while(diagnostics.size()>100) diagnostics.remove(0); }
    }
    public void recordDiagnostic(String action,String message){
        String e=System.currentTimeMillis()+"|"+action+"|"+message; diagnostics.add(e); while(diagnostics.size()>100) diagnostics.remove(0);
        getSharedPreferences("agents_runtime",MODE_PRIVATE).edit().putString("diagnostics",joinDiagnostics()).apply();
    }
    private String joinDiagnostics(){ StringBuilder b=new StringBuilder(); for(String x:diagnostics){if(b.length()>0)b.append("\\n");b.append(x);} return b.toString(); }
    public List<String> diagnosticsSnapshot(){ return new ArrayList<>(diagnostics.subList(Math.max(0,diagnostics.size()-20),diagnostics.size())); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null){RuntimeLogger.log(this,"ACCESSIBILITY_EVENT","null");return;}
        RuntimeLogger.log(this,"ACCESSIBILITY_EVENT","type="+event.getEventType()+" package="+event.getPackageName());
        int t=event.getEventType();
        if(t!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && t!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)return;
        if(t==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && System.currentTimeMillis()-lastAnnouncedAt<1200)return;
        announceScreenIfChanged();
    }
    private void announceScreenIfChanged(){
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return;
        String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
        if(pkg.equals(getPackageName()))return;
        StringBuilder b=new StringBuilder(pkg);
        collectVisibleDescription(root,b);
        String description=b.toString();
        if(description.equals(lastAnnouncedScreen)||description.length()<4)return;
        lastAnnouncedScreen=description; lastAnnouncedAt=System.currentTimeMillis();
        Intent i=new Intent("com.arilifelong.agent.SCREEN_CHANGED");
        i.setPackage(getPackageName());
        // Do not expose or speak visible screen contents; announce only actionable capabilities.
        i.putExtra("suggestions",suggestionActions().toString());
        sendBroadcast(i);
    }
    private void collectVisibleDescription(AccessibilityNodeInfo n,StringBuilder b){
        if(n==null||b.length()>1800)return;
        CharSequence t=n.getText(),d=n.getContentDescription();
        String label=t!=null?t.toString().trim():(d!=null?d.toString().trim():"");
        if(!label.isEmpty()&&label.length()<=100&&!label.equals("✦"))b.append(" | ").append(label);
        for(int i=0;i<n.getChildCount()&&b.length()<1800;i++)collectVisibleDescription(n.getChild(i),b);
    }
    @Override public void onInterrupt(){}
    @Override public void onDestroy(){ if(instance==this)instance=null; super.onDestroy(); }

    public boolean clickText(String text){
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return false;
        return clickRecursive(root,text,false);
    }
    private List<AccessibilityNodeInfo> matchingNodes(String target){
        List<AccessibilityNodeInfo> out=new ArrayList<>();
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null){
            long end=System.currentTimeMillis()+1200;
            while(root==null&&System.currentTimeMillis()<end){
                try{Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                root=getRootInActiveWindow();
            }
        }
        if(root==null)return out;
        String[] alternatives=(target==null?"":target).split("\\|"); collectMatches(root,alternatives,out); return out;
    }
    private void collectMatches(AccessibilityNodeInfo n,String[] alternatives,List<AccessibilityNodeInfo> out){
        if(n==null)return; String ts=n.getText()==null?"":n.getText().toString().trim(); String ds=n.getContentDescription()==null?"":n.getContentDescription().toString().trim();
        for(String a:alternatives){String q=a.trim(); if(!q.isEmpty() && (ts.equalsIgnoreCase(q)||ds.equalsIgnoreCase(q)||ts.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))||ds.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))){out.add(n);break;}}
        for(int i=0;i<n.getChildCount();i++)collectMatches(n.getChild(i),alternatives,out);
    }
    public boolean clickTextOrDescription(String target){
        RuntimeLogger.log(this,"ACCESSIBILITY_ACTION","click target="+target);
        for(AccessibilityNodeInfo n:matchingNodes(target)){
            if(n.isClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
            AccessibilityNodeInfo p=n.getParent(); if(p!=null&&p.isClickable()&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
            Rect r=new Rect(); n.getBoundsInScreen(r); if(!r.isEmpty()&&tap(r.centerX(),r.centerY()))return true;
        } return false;
    }
    public boolean longClickText(String target){
        for(AccessibilityNodeInfo n:matchingNodes(target)){
            if(n.isLongClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))return true;
            Rect r=new Rect(); n.getBoundsInScreen(r); if(!r.isEmpty()&&longClick(r.centerX(),r.centerY()))return true;
        } return false;
    }
    public boolean swipeDirection(String direction){
        float w=getResources().getDisplayMetrics().widthPixels, h=getResources().getDisplayMetrics().heightPixels, cx=w/2f, cy=h/2f;
        float dx=0,dy=0; String d=direction==null?"up":direction.toLowerCase(Locale.ROOT); if("left".equals(d))dx=-w*.35f; else if("right".equals(d))dx=w*.35f; else if("down".equals(d))dy=h*.28f; else dy=-h*.28f;
        return swipe(cx-dx,cy-dy,cx+dx,cy+dy,420);
    }
    public boolean clickCurrentLike(){return clickTextOrDescription("Like|אהבתי|לייק|👍");}
    public boolean clickCurrentFollow(){return clickTextOrDescription("Follow|עקוב|עוקב|Follow back");}
    public boolean clickApprove(){return clickTextOrDescription("Approve|אשר|אישור|Allow|אפשר|Confirm|כן");}
    public boolean openChatMenu(){return clickTextOrDescription("שלוש נקודות|אפשרויות נוספות|More options|More|עוד|⋮|︙");}
    public boolean pinItem(){return clickTextOrDescription("נעץ|הצמד|הצמדה|Pin|Pinned");}
    public boolean pressSend(){return clickTextOrDescription("שלח|Send|שליחה|Send message|שלח הודעה");}
    public boolean uninstallApp(String packageName,String appLabel){
        if(android.os.Build.VERSION.SDK_INT<24)return false;
        String target=packageName==null||packageName.trim().isEmpty()?"":packageName.trim();
        if(target.isEmpty()){AccessibilityNodeInfo root=getRootInActiveWindow(); target=root==null?"":String.valueOf(root.getPackageName());}
        if(target.isEmpty()||target.equals(getPackageName()))return false;
        String label=appLabel==null?"":appLabel.trim();
        if(label.isEmpty())try{label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(target,0)).toString();}catch(Exception ignored){}
        if(!home())return false;
        final String pkg=target, finalLabel=label;
        handler.postDelayed(()->findAndUninstall(pkg,finalLabel,0),800);
        recordDiagnostic("UNINSTALL_APP","START|package="+pkg); return true;
    }
    private void findAndUninstall(String pkg,String label,int attempt){
        if(attempt>15){recordDiagnostic("UNINSTALL_APP","FAILURE|not_found");return;}
        AccessibilityNodeInfo root=getRootInActiveWindow(); AccessibilityNodeInfo icon=root==null?null:findNode(root,pkg,label);
        if(icon!=null){Rect r=new Rect();icon.getBoundsInScreen(r);if(!r.isEmpty()&&longClick(r.centerX(),r.centerY())){handler.postDelayed(()->findUninstallTarget(r.centerX(),r.centerY(),0),850);return;}}
        handler.postDelayed(()->findAndUninstall(pkg,label,attempt+1),450);
    }
    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo n,String pkg,String label){
        if(n==null)return null; String p=n.getPackageName()==null?"":n.getPackageName().toString();String t=n.getText()==null?"":n.getText().toString();String d=n.getContentDescription()==null?"":n.getContentDescription().toString();
        if((p.equals(pkg)||(label.length()>0&& (t.equalsIgnoreCase(label)||d.equalsIgnoreCase(label))))&&(n.isClickable()||n.isLongClickable()||n.getChildCount()==0))return n;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo f=findNode(n.getChild(i),pkg,label);if(f!=null)return f;}return null;
    }
    private void findUninstallTarget(float sx,float sy,int attempt){
        if(attempt>12){recordDiagnostic("UNINSTALL_APP","FAILURE|target_not_found");return;}
        AccessibilityNodeInfo root=getRootInActiveWindow(); AccessibilityNodeInfo target=root==null?null:findLabel(root,"הסר התקנה|הסרת התקנה|הסר|Uninstall|Remove|Remove app");
        if(target!=null){Rect r=new Rect();target.getBoundsInScreen(r);if(!r.isEmpty()&&drag(sx,sy,r.centerX(),r.centerY())){handler.postDelayed(()->confirmUninstall(0),900);return;}}
        handler.postDelayed(()->findUninstallTarget(sx,sy,attempt+1),350);
    }
    private AccessibilityNodeInfo findLabel(AccessibilityNodeInfo n,String labels){String[] a=labels.split("\\|");String t=n.getText()==null?"":n.getText().toString(),d=n.getContentDescription()==null?"":n.getContentDescription().toString();for(String x:a)if(t.equalsIgnoreCase(x)||d.equalsIgnoreCase(x))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo f=findLabel(n.getChild(i),labels);if(f!=null)return f;}return null;}
    private boolean drag(float x1,float y1,float x2,float y2){Path p=new Path();p.moveTo(x1,y1);p.lineTo(x2,y2);return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,900)).build(),null,null);}
    private void confirmUninstall(int attempt){if(attempt>10)return;AccessibilityNodeInfo root=getRootInActiveWindow();if(root!=null){AccessibilityNodeInfo n=findLabel(root,"הסר התקנה|הסרת התקנה|הסר|Uninstall|OK|אישור");if(n!=null&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){recordDiagnostic("UNINSTALL_APP","SUCCESS");return;}}handler.postDelayed(()->confirmUninstall(attempt+1),450);}
    public boolean openNotificationsAndClick(String target,boolean longClick){ if(!notifications())return false; handler.postDelayed(()->{if(longClick)longClickText(target);else clickTextOrDescription(target);},450); return true; }
    public boolean openQuickSettingsAndClick(String target,boolean longClick){ if(!quickSettings())return false; handler.postDelayed(()->{if(longClick)longClickText(target);else clickTextOrDescription(target);},450); return true; }
    public boolean performActionWithFallback(String type,String target,String direction){
        long start=System.currentTimeMillis(); boolean ok=false; int attempts=0;
        for(int i=0;i<3;i++){attempts=i+1; try{
            if("TYPE_TEXT".equals(type))ok=setText(target); else if("SEND_TEXT".equals(type)){ok=setText(target)&&clickTextOrDescription("Send|שלח|שליחה|➤|✓");}
            else if("CLICK_TEXT".equals(type)||"CLICK_CONTENT_DESCRIPTION".equals(type)||"CLICK_ROLE".equals(type))ok=clickTextOrDescription(target);
            else if("LONG_CLICK_TEXT".equals(type))ok=longClickText(target); else if("SCROLL".equals(type))ok=scroll(!"up".equalsIgnoreCase(direction)); else if("SWIPE".equals(type))ok=swipeDirection(direction);
            else if("LIKE".equals(type))ok=clickCurrentLike(); else if("FOLLOW".equals(type))ok=clickCurrentFollow(); else if("APPROVE".equals(type))ok=clickApprove();
            if(ok)break; if(i<2){Thread.sleep(120L*(i+1)); AccessibilityNodeInfo r=getRootInActiveWindow();if(r!=null)r.refresh();}
        }catch(Exception ignored){} }
        recordDiagnostic(type,(ok?"SUCCESS":"FAILURE")+"|attempts="+attempts+"|durationMs="+(System.currentTimeMillis()-start)); return ok;
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
        AccessibilityNodeInfo target=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if(target==null) target=findEditable(root);
        if(target==null)return false;
        if(!target.isFocused())target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle b=new Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text==null?"":text);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);
    }
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n){
        if(n==null)return null;
        if(n.isEditable())return n;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo f=findEditable(n.getChild(i));if(f!=null)return f;}
        return null;
    }
    public boolean settingsAction(String action,String value){
        String a=action==null?"":action.toLowerCase(Locale.ROOT);
        String v=value==null?"":value;
        if("open".equals(a))return clickTextOrDescription("Settings|הגדרות");
        if("wifi".equals(a))return clickTextOrDescription("Wi-Fi|Wi‑Fi|WiFi|רשת ואינטרנט|אינטרנט");
        if("bluetooth".equals(a))return clickTextOrDescription("Bluetooth|בלוטות'|בלוטוס");
        if("sound".equals(a)||"volume".equals(a))return clickTextOrDescription("Sound|צליל|קול");
        if("display".equals(a)||"brightness".equals(a))return clickTextOrDescription("Display|תצוגה|בהירות");
        if("battery".equals(a))return clickTextOrDescription("Battery|סוללה");
        if("apps".equals(a))return clickTextOrDescription("Apps|אפליקציות");
        if("notifications".equals(a))return clickTextOrDescription("Notifications|התראות");
        if("privacy".equals(a))return clickTextOrDescription("Privacy|פרטיות");
        if("security".equals(a))return clickTextOrDescription("Security|אבטחה");
        if("storage".equals(a))return clickTextOrDescription("Storage|אחסון");
        if("language".equals(a))return clickTextOrDescription("Language|שפה|שפות");
        if("date_time".equals(a))return clickTextOrDescription("Date & time|Date and time|תאריך ושעה");
        if("accessibility".equals(a))return clickTextOrDescription("Accessibility|נגישות");
        if("permissions".equals(a))return clickTextOrDescription("Permissions|הרשאות");
        if("accounts".equals(a))return clickTextOrDescription("Accounts|חשבונות");
        if("location".equals(a))return clickTextOrDescription("Location|מיקום");
        if("screen_lock".equals(a))return clickTextOrDescription("Screen lock|נעילת מסך");
        if("search".equals(a)&&!v.isEmpty())return clickTextOrDescription(v);
        if("click".equals(a)&&!v.isEmpty())return clickTextOrDescription(v);
        if("scroll".equals(a))return swipeDirection(v.isEmpty()?"up":v);
        if("back".equals(a))return ActionEngine.back();
        return false;
    }

    public boolean instagramAction(String action,String value){
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return false;
        String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
        if(!"com.instagram.android".equals(pkg)){
            recordDiagnostic("INSTAGRAM_"+(action==null?"":action),"FAILURE|wrong_package="+pkg);
            return false;
        }
        String a=action==null?"":action.toLowerCase(Locale.ROOT).trim();
        String target=value==null?"":value.trim();
        boolean ok=false;
        if("like".equals(a))ok=instagramClick("like|אהבתי|לייק|Liked|Unlike|Unlike post");
        else if("save".equals(a))ok=instagramClick("save|שמור|Saved|Unsave");
        else if("share".equals(a))ok=instagramClick("share|שתף|שלח|Share");
        else if("comment".equals(a))ok=instagramClick("comment|תגובה|תגובות|Add a comment|הוסף תגובה");
        else if("follow".equals(a))ok=instagramClick("follow|עקוב|Follow back");
        else if("unfollow".equals(a))ok=instagramClick("unfollow|הפסק לעקוב|Unfollow");
        else if("search".equals(a))ok=instagramClick("search|חיפוש");
        else if("profile".equals(a))ok=instagramClick("profile|פרופיל");
        else if("home".equals(a))ok=instagramClick("home|בית");
        else if("reels".equals(a))ok=instagramClick("reels|רילס");
        else if("stories".equals(a))ok=instagramClick("story|stories|סטורי|סיפור");
        else if("messages".equals(a))ok=instagramClick("messages|message|הודעות|הודעה");
        else if("new_post".equals(a))ok=instagramClick("new post|פוסט חדש|יצירה|Create|Create post");
        else if("next".equals(a))ok=swipeDirection("up");
        else if("previous".equals(a))ok=swipeDirection("down");
        else if("back".equals(a))ok=ActionEngine.back();
        else if("type_comment".equals(a))ok=setText(target);
        else if("send".equals(a))ok=instagramClick("send|שלח|שליחה|Send");
        else if("open_result".equals(a)&&!target.isEmpty())ok=instagramClick(target);
        if(ok) waitForInstagramUiChange(350);
        recordDiagnostic("INSTAGRAM_"+a,(ok?"SUCCESS":"FAILURE")+"|target="+target);
        return ok;
    }

    private boolean instagramClick(String alternatives){
        List<AccessibilityNodeInfo> nodes=matchingNodes(alternatives);
        for(AccessibilityNodeInfo n:nodes){
            if(n==null)continue;
            if(n.isClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
            AccessibilityNodeInfo p=n.getParent();
            if(p!=null&&p.isClickable()&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
            Rect r=new Rect();
            n.getBoundsInScreen(r);
            if(!r.isEmpty()&&r.width()>2&&r.height()>2&&tap(r.centerX(),r.centerY()))return true;
        }
        return false;
    }

    private void waitForInstagramUiChange(long ms){
        try{Thread.sleep(Math.max(100,Math.min(1200,ms)));}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }

    public JSONArray suggestionActions(){
        JSONArray out=new JSONArray();
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return out;
        String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
        if(pkg.equals("com.android.chrome")){
            addSuggestion(out,"chrome_clear_search","מחק את החיפוש/הטקסט הקיים");
            addSuggestion(out,"chrome_new_tab","פתח כרטיסייה חדשה");
            addSuggestion(out,"back","חזור לדף הקודם");
            addSuggestion(out,"scroll","גלול במסך");
        }
        collectSuggestions(root,out);
        return out;
    }
    private void addSuggestion(JSONArray a,String type,String label){
        for(int i=0;i<a.length();i++)try{if(a.getJSONObject(i).optString("type").equals(type))return;}catch(Exception ignored){}
        JSONObject o=new JSONObject();try{o.put("type",type);o.put("label",label);a.put(o);}catch(Exception ignored){}
    }
    private void collectSuggestions(AccessibilityNodeInfo n,JSONArray out){
        if(n==null||out.length()>=7)return;
        if(n.isEditable()){addSuggestion(out,"type_text","הקלד טקסט בשדה הנוכחי");}
        if(n.isClickable()){
            String t=n.getText()==null?"":n.getText().toString().trim();
            String d=n.getContentDescription()==null?"":n.getContentDescription().toString().trim();
            String label=!t.isEmpty()?t:d;
            if(!label.isEmpty()&&label.length()<=50&&!label.equals("✦"))addSuggestion(out,"click_text",label);
        }
        for(int i=0;i<n.getChildCount()&&out.length()<7;i++)collectSuggestions(n.getChild(i),out);
    }
    public boolean chromeNewTab(){ return clickTextOrDescription("New tab|כרטיסייה חדשה|כרטיסיה חדשה|פתח כרטיסייה|New Tab"); }
    public boolean chromeCloseTab(){ return clickTextOrDescription("Close tab|סגור כרטיסייה|סגור כרטיסיה|Close"); }
    public boolean chromeNextTab(){ return clickTextOrDescription("Next tab|הכרטיסייה הבאה|כרטיסייה הבאה"); }
    public boolean chromePreviousTab(){ return clickTextOrDescription("Previous tab|הכרטיסייה הקודמת|כרטיסייה קודמת"); }
    public boolean chromeClearSearch(){
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return false;
        AccessibilityNodeInfo target=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if(target==null)target=findEditable(root);
        if(target==null)return false;
        Bundle b=new Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"");
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
    public boolean closeCurrentApp(){
        RuntimeLogger.log(this,"CLOSE_APP","opening recents");
        if(!performGlobalAction(GLOBAL_ACTION_RECENTS))return false;
        try{Thread.sleep(450);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        float w=getResources().getDisplayMetrics().widthPixels;
        float h=getResources().getDisplayMetrics().heightPixels;
        boolean swiped=swipe(w/2f,h*0.70f,w/2f,h*0.22f,500);
        RuntimeLogger.log(this,"CLOSE_APP","recents_swipe="+swiped);
        try{Thread.sleep(350);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        performGlobalAction(GLOBAL_ACTION_HOME);
        return swiped;
    }
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
    public String screenText(){
        AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null)return "אין גישה למסך כרגע";
        StringBuilder b=new StringBuilder(); collectText(root,b,0);
        String s=b.toString().trim(); return s.length()>7000?s.substring(0,7000):s;
    }
    private void collectText(AccessibilityNodeInfo n,StringBuilder b,int depth){
        if(n==null||depth>30)return;
        CharSequence t=n.getText(),d=n.getContentDescription();
        String a=t==null?"":t.toString().trim(),c=d==null?"":d.toString().trim();
        if(!a.isEmpty())b.append(a).append(" | ");
        if(!c.isEmpty()&&!c.equals(a))b.append(c).append(" | ");
        for(int i=0;i<n.getChildCount();i++)collectText(n.getChild(i),b,depth+1);
    }
    public int clickRepeat(String text,int count,long delay){
        int done=0,n=Math.max(1,Math.min(30,count));
        for(int i=0;i<n;i++){if(clickContains(text))done++;try{Thread.sleep(Math.max(50,Math.min(1000,delay)));}catch(Exception ignored){}}
        return done;
    }
    public boolean scrollUntilText(String text,boolean forward,int max,long delay){
        int n=Math.max(1,Math.min(80,max));
        for(int i=0;i<n;i++){if(clickContains(text))return true;if(!scroll(forward))break;try{Thread.sleep(Math.max(50,Math.min(1000,delay)));}catch(Exception ignored){}}
        return clickContains(text);
    }
    public boolean screenshot(){
        return android.os.Build.VERSION.SDK_INT>=30 && performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
    }
    public boolean tap(float x,float y){
        if(android.os.Build.VERSION.SDK_INT<24)return false;
        Path p=new Path();p.moveTo(x,y);
        return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,50)).build(),null,null);
    }
}