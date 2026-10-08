package com.arilifelong.agent;

import android.content.*;
import android.os.Build;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && Settings.canDrawOverlays(context)) {
            Intent i=new Intent(context,FloatingAgentService.class);
            if(Build.VERSION.SDK_INT>=26) context.startForegroundService(i); else context.startService(i);
        }
    }
}
