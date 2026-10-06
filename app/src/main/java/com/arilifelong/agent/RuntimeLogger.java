package com.arilifelong.agent;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RuntimeLogger {
    private static final String TAG="OneLifeAgent";
    private static final Object LOCK=new Object();
    private RuntimeLogger(){}
    public static void init(Context c,String event){log(c,"APP",event);}
    public static void log(Context c,String event,String message){
        String line=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date())+" | "+event+" | "+(message==null?"":message);
        Log.i(TAG,line);
        if(c==null)return;
        synchronized(LOCK){
            try{
                append(c,"agent-runtime.log",line);
                if(isFailure(event,message)){
                    append(c,"agent-failures.log",line);
                }
                rotate(c,"agent-runtime.log");
                rotate(c,"agent-failures.log");
            }catch(Exception e){Log.e(TAG,"LOGGER_FAILURE",e);}
        }
    }

    private static boolean isFailure(String event,String message){
        String s=((event==null?"":event)+" "+(message==null?"":message)).toLowerCase(Locale.ROOT);
        return s.contains("error")||s.contains("fail")||s.contains("failed")||s.contains("exception")||s.contains("שגיאה")||s.contains("נכשל")||s.contains("לא הצלח");
    }

    private static void append(Context c,String name,String line)throws Exception{
        File f=new File(c.getFilesDir(),name);
        FileWriter fw=new FileWriter(f,true);
        BufferedWriter bw=new BufferedWriter(fw);
        bw.write(line);bw.newLine();bw.close();fw.close();
    }

    private static void rotate(Context c,String name){
        try{
            File f=new File(c.getFilesDir(),name);
            if(f.length()>2*1024*1024){
                File old=new File(c.getFilesDir(),name+".old");
                if(old.exists())old.delete();
                f.renameTo(old);
            }
        }catch(Exception ignored){}
    }
    public static void log(Context c,String event){log(c,event,"");}
}