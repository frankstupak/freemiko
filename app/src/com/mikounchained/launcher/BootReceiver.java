package com.mikounchained.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Re-establishes MikoUnchained on every boot: re-applies the watchdog neuter (the neuterd process does not
 * survive a reboot even though its binary in /data does) and starts the nav bar overlay. This is the
 * same BOOT_COMPLETED mechanism a launcher/utility uses; on this ROM it is also the only way to run a
 * root command at boot (verity-RO init consumes no adb/property trigger).
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "MikoUnchained";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.i(TAG, "boot event: " + (intent != null ? intent.getAction() : "null"));
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            public void run() {
                try {
                    Neuter.apply(context);
                    Intent svc = new Intent(context, NavBarService.class);
                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
                    else context.startService(svc);
                } catch (Throwable t) {
                    Log.e(TAG, "boot setup failed", t);   // never crash the boot
                } finally {
                    pr.finish();
                }
            }
        }).start();
    }
}
