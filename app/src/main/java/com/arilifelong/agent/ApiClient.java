package com.arilifelong.agent;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ApiClient {
    public interface Callback { void success(JSONObject result); void error(String message); }
    private static final String DEFAULT_BASE="https://phone-agent.onrender.com";
    private ApiClient(){}

    public static void chat(final String text, final Callback cb){
        new Thread(() -> {
            HttpURLConnection c=null;
            try{
                URL u=new URL(DEFAULT_BASE+"/api/chat");
                c=(HttpURLConnection)u.openConnection();
                c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(60000);
                c.setRequestProperty("Content-Type","application/json"); c.setDoOutput(true);
                JSONArray messages=new JSONArray();
                JSONObject m=new JSONObject(); m.put("role","user"); m.put("content",text); messages.put(m);
                JSONObject body=new JSONObject(); body.put("messages",messages);
                try(OutputStream os=c.getOutputStream()){ os.write(body.toString().getBytes("UTF-8")); }
                int code=c.getResponseCode();
                InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
                String response=read(in);
                JSONObject out=parseObject(response);
                Handler h=new Handler(Looper.getMainLooper());
                if(code>=200&&code<300)h.post(() -> cb.success(out));
                else h.post(() -> cb.error("שגיאת שרת ("+code+"): "+out.optString("error",response)));
            }catch(Exception e){
                String msg=e.getClass().getSimpleName()+": "+(e.getMessage()==null?"ללא פירוט":e.getMessage());
                new Handler(Looper.getMainLooper()).post(() -> cb.error("שגיאת חיבור לשרת הסוכן: "+msg));
            }finally{ if(c!=null)c.disconnect(); }
        }).start();
    }

    public static void transcribe(final File audio, final Callback cb){
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
                new Handler(Looper.getMainLooper()).post(() -> cb.error("שגיאת חיבור לתמלול: "+msg));
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