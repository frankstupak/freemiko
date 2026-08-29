package com.mikounchained.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

/**
 * Feature 2 — the always-on nav bar.
 *
 * The Miko ROM ships a 0px system navbar and verity blocks patching the system overlay, so MikoUnchained
 * draws its OWN back/home/recents bar as a TYPE_APPLICATION_OVERLAY window managed here. Being an
 * overlay window it floats above every app and survives app switches; being a foreground service it
 * survives memory pressure and is restarted (START_STICKY) if the ROM kills it. Back and recents are
 * injected as key events through the root shell — no accessibility service, no manual setup dance.
 * Home just returns to this launcher.
 *
 * The overlay app-op is granted for us by {@link Neuter} at first run/boot, so no "draw over other
 * apps" toggle is ever shown to the user.
 */
public class NavBarService extends Service {

    private static final String TAG = "MikoUnchained";
    private static final String CHAN = "mikounchained_navbar";
    private static final int NOTE_ID = 42;

    private WindowManager wm;
    private View bar;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNote();
        addBar();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (bar == null) addBar();   // self-repair if the view was lost
        return START_STICKY;
    }

    private void startForegroundNote() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHAN, "MikoUnchained nav bar",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CHAN)
                : new Notification.Builder(this);
        Notification n = b.setContentTitle("MikoUnchained")
                .setContentText("Navigation bar active")
                .setSmallIcon(R.drawable.ic_home)
                .setOngoing(true)
                .build();
        startForeground(NOTE_ID, n);
    }

    private void addBar() {
        try {
            wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            bar = LayoutInflater.from(this).inflate(R.layout.navbar, null);

            bar.findViewById(R.id.nav_back).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { RootShell.get().run("input keyevent 4"); }      // BACK
            });
            bar.findViewById(R.id.nav_home).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { goHome(); }
            });
            bar.findViewById(R.id.nav_recents).setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { RootShell.get().run("input keyevent 187"); }    // APP_SWITCH
            });

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int h = Math.round(48 * dm.density);

            int type = (Build.VERSION.SDK_INT >= 26)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, h, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM;
            wm.addView(bar, lp);
            Log.i(TAG, "nav bar attached (h=" + h + "px)");
        } catch (Exception e) {
            Log.e(TAG, "nav bar attach FAILED (overlay app-op missing?)", e);
            bar = null;
        }
    }

    private void goHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bar != null && wm != null) {
            try { wm.removeView(bar); } catch (Exception ignore) { }
            bar = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
