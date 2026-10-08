package com.arilifelong.agent;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public final class ApiClient {
    public interface Callback { void success(JSONObject result); void error(String message); }
    private static final String DEFAULT_BASE="https://one-life-long-slave.onrender.com";
    private static final JSONArray history=new JSONArray();
    private static final Object HISTORY_LOCK=new Object();
    private ApiClient(){}

    private static final String REMOTE_PREFS="remote_control";
    private static volatile boolean remoteRunning=false;
    public static void startRemotePolling(final android.content.Context context, final java.util.function.Consumer<String> onCommand){
        if(remoteRunning)return; remoteRunning=true;
        new Thread(() -> {
            try{
                android.content.SharedPreferences p=context.getSharedPreferences(REMOTE_PREFS,0);
                String deviceId=p.getString("device_id","");
                if(deviceId.isEmpty()){deviceId=UUID.randomUUID().toString();p.edit().putString("device_id",deviceId).apply();}
                String token=p.getString("token","");
                if(token.isEmpty()){
                    token=registerRemote(deviceId);
                    if(token.isEmpty()) throw new IOException("remote register returned empty token");
                    p.edit().putString("token",token).apply();
                }
                while(remoteRunning){
                    try{
                        String cmd=pollRemote(token);
                        if(cmd!=null&&!cmd.isEmpty())new Handler(Looper.getMainLooper()).post(() -> onCommand.accept(cmd));
                    }catch(RemoteUnauthorizedException e){
                        try{
                            token=registerRemote(deviceId);
                            if(token.isEmpty()) throw new IOException("remote re-register returned empty token");
                            p.edit().putString("token",token).apply();
                        }catch(Exception ignored){}
                    }catch(Exception ignored){}
                    Thread.sleep(1500);
                }
            }catch(Exception ignored){}
        },"remote-command-poll").start();
    }
    public static void stopRemotePolling(){remoteRunning=false;}
    private static final class RemoteUnauthorizedException extends IOException{}
    private static String registerRemote(String deviceId)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(DEFAULT_BASE+"/api/remote/register").openConnection();
        c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);
        JSONObject b=new JSONObject();b.put("deviceId",deviceId);try(OutputStream os=c.getOutputStream()){os.write(b.toString().getBytes("UTF-8"));}
        int code=c.getResponseCode();String out=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());if(code<200||code>=300)throw new IOException("remote register "+code);return parseObject(out).optString("token","");
    }
    private static String pollRemote(String token)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(DEFAULT_BASE+"/api/remote/poll?token="+java.net.URLEncoder.encode(token,"UTF-8")).openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(10000);c.setReadTimeout(15000);
        int code=c.getResponseCode();String out=read(code>=200&&code<300?c.getInputStream():c.getErrorStream());
        if(code==401)throw new RemoteUnauthorizedException();
        if(code<200||code>=300)throw new IOException("remote poll "+code);
        JSONObject o=parseObject(out);JSONObject item=o.optJSONObject("command");return item==null?null:item.optString("text","");
    }

    public static void chat(final String text, final Callback cb){
        RuntimeLogger.log(null,"API_CHAT_REQUEST","text="+text);
        new Thread(() -> {
            HttpURLConnection c=null;
            try{
                URL u=new URL(DEFAULT_BASE+"/api/chat");
                c=(HttpURLConnection)u.openConnection();
                c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(60000);
                c.setRequestProperty("Content-Type","application/json"); c.setDoOutput(true);
                JSONArray messages=new JSONArray();
                synchronized(HISTORY_LOCK){
                    for(int i=0;i<history.length();i++)messages.put(history.getJSONObject(i));
                }
                String context="";
                try{
                    AgentAccessibilityService svc=AgentAccessibilityService.getInstance();
                    if(svc!=null)context="\n\nמה שנגיש כרגע במסך:\n"+svc.screenText();
                }catch(Exception ignored){}
                JSONObject m=new JSONObject(); m.put("role","user"); m.put("content",text+context); messages.put(m);
                synchronized(HISTORY_LOCK){
                    history.put(new JSONObject().put("role","user").put("content",text+context));
                    while(history.length()>20)history.remove(0);
                }
                JSONObject body=new JSONObject(); body.put("messages",messages);
                body.put("mode","all");
                RuntimeLogger.log(null,"API_CHAT_SEND","messages="+messages.length()+" bytes="+body.toString().length());
                try(OutputStream os=c.getOutputStream()){ os.write(body.toString().getBytes("UTF-8")); }
                int code=c.getResponseCode();
                InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
                String response=read(in);
                RuntimeLogger.log(null,"API_TRANSCRIBE_RESPONSE","http="+code+" body="+response);
                RuntimeLogger.log(null,"API_CHAT_RESPONSE","http="+code+" body="+response);
                JSONObject out=parseObject(response);
                Handler h=new Handler(Looper.getMainLooper());
                if(code>=200&&code<300){
                    String reply=out.optString("reply","");
                    synchronized(HISTORY_LOCK){
                        if(!reply.isEmpty())history.put(new JSONObject().put("role","assistant").put("content",reply));
                        while(history.length()>12)history.remove(0);
                    }
                    h.post(() -> cb.success(out));
                }
                else { RuntimeLogger.log(null,"API_CHAT_ERROR","http="+code+" error="+out.optString("error",response)); h.post(() -> cb.error("שגיאת שרת ("+code+"): "+out.optString("error",response))); }
            }catch(Exception e){
                String msg=e.getClass().getSimpleName()+": "+(e.getMessage()==null?"ללא פירוט":e.getMessage());
                RuntimeLogger.log(null,"API_CHAT_EXCEPTION",msg); new Handler(Looper.getMainLooper()).post(() -> cb.error("שגיאת חיבור לשרת הסוכן: "+msg));
            }finally{ if(c!=null)c.disconnect(); }
        }).start();
    }

    public static void transcribe(final File audio, final Callback cb){
        RuntimeLogger.log(null,"API_TRANSCRIBE_REQUEST","file="+(audio==null?"null":audio.getName())+" size="+(audio==null?0:audio.length()));
        new Thread(() -> {
            HttpURLConnection c=null;
            String boundary="----PhoneAgent"+System.currentTimeMillis();
            try{
                if(audio==null||!audio.exists()||audio.length()==0)
                    throw new IOException("קובץ הקול ריק או לא נוצר");
                URL u=new URL(DEFAULT_BASE+"/api/transcribe");
                c=(HttpURLConnection)u.openConnection();
                c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(90000);
                c.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
                c.setRequestProperty("Accept","application/json");
                c.setDoOutput(true);
                try(OutputStream os=c.getOutputStream(); FileInputStream fis=new FileInputStream(audio)){
                    String head="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n";
                    os.write(head.getBytes("UTF-8"));
                    byte[] buf=new byte[8192]; int n;
                    while((n=fis.read(buf))!=-1)os.write(buf,0,n);
                    os.write(("\r\n--"+boundary+"--\r\n").getBytes("UTF-8"));
                }
                int code=c.getResponseCode();
                InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
                String response=read(in);
                JSONObject out=parseObject(response);
                Handler h=new Handler(Looper.getMainLooper());
                if(code>=200&&code<300){
                    String text=out.optString("text","");
                    if(text.trim().isEmpty()) h.post(() -> cb.error("התמלול חזר ללא טקסט"));
                    else h.post(() -> cb.success(out));
                }else{
                    h.post(() -> cb.error("שגיאת תמלול ("+code+"): "+out.optString("error",response)));
                }
            }catch(Exception e){
                String msg=e.getClass().getSimpleName()+": "+(e.getMessage()==null?"ללא פירוט":e.getMessage());
                RuntimeLogger.log(null,"API_TRANSCRIBE_EXCEPTION",msg); new Handler(Looper.getMainLooper()).post(() -> cb.error("שגיאת חיבור לתמלול: "+msg));
            }finally{ if(c!=null)c.disconnect(); if(audio!=null)audio.delete(); }
        }).start();
    }

    private static JSONObject parseObject(String response){
        try{return new JSONObject(response==null?"{}":response);}
        catch(Exception e){
            JSONObject o=new JSONObject();
            try{o.put("error",response==null?"תשובה ריקה":response);}catch(Exception ignored){}
            return o;
        }
    }

    private static String read(InputStream in)throws Exception{
        if(in==null)return "";
        StringBuilder s=new StringBuilder(); char[] b=new char[2048];
        try(Reader r=new InputStreamReader(in,"UTF-8")){ int n; while((n=r.read(b))!=-1)s.append(b,0,n); }
        return s.toString();
    }
}