package com.arilifelong.agent;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int OVERLAY_REQUEST = 1001;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        TextView status = findViewById(R.id.status);
        Button enable = findViewById(R.id.enable);
        enable.setOnLongClickListener(v -> { startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS")); return true; });

        enable.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, OVERLAY_REQUEST);
            } else {
                startFloating();
                status.setText("הסוכן הצף פעיל בתחתית המסך");
            }
        });

        if (Settings.canDrawOverlays(this)) {
            startFloating();
            status.setText("הסוכן הצף פעיל");
        } else {
            status.setText("יש לאשר הרשאת הצגה מעל אפליקציות אחרות");
        }
    }

    private void startFloating() {
        Intent i = new Intent(this, FloatingAgentService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST && Settings.canDrawOverlays(this)) startFloating();
    }
}
