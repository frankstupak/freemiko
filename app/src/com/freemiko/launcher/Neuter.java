package com.freemiko.launcher;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Feature 3 — watchdog neuter.
 *
 * ServiceExam's SecurityMonitor greps `ps` for "adbd" every ~2s and, on a match, execs `su -> reboot`
 * to freeze a tampered unit. We defuse it by shadowing /system/bin/reboot with a no-op, applied in
 * init's GLOBAL mount namespace by the embedded {@code neuterd} daemon (see native/neuterd.c). This
 * class materializes neuterd from an embedded base64 blob and launches it, then does the ancillary
 * setup that makes a repurposed unit usable: grant FreeMiko the overlay app-op (so the nav bar needs
 * no manual "draw over other apps" toggle) and re-enable adb-over-wifi.
 *
 * Runs on first launch (HomeActivity) and again on every BOOT_COMPLETED (BootReceiver), because the
 * daemon process does not survive a reboot even though its binary, in /data, does. The su on this
 * ROM has no -c, so the script is fed on stdin. Fail-loud into logcat + /data/local/tmp/freemiko.
 */
public final class Neuter {

    private static final String TAG = "FreeMiko";
    private static final String PKG = "com.freemiko.launcher";

    /** If true, also bring up adb-over-wifi (:5555) + keep-awake at neuter time. The whole point of
     *  the neuter is to keep a repurposed dev unit reachable; flip to false to neuter only. */
    private static final boolean ENABLE_ADB_TCP = true;

    /** neuterd (arm64 ELF, freestanding) as base64 — injected from native/neuterd by build.sh. */
    private static final String NEUTERD_B64 = "@@NEUTERD_B64@@";

    private Neuter() { }

    private static String payload() {
        StringBuilder s = new StringBuilder();
        s.append("D=/data/local/tmp/freemiko\n");
        s.append("LOG=$D/neuter.log\n");
        s.append("mkdir -p \"$D\" 2>/dev/null\n");
        s.append("echo \"[neuter up=$(cut -d' ' -f1 /proc/uptime)] freemiko\" >> \"$LOG\"\n");
        // 1) materialize the self-healing global-namespace neuter daemon from embedded base64
        s.append("echo '").append(NEUTERD_B64).append("' | base64 -d > \"$D/neuterd\" 2>>\"$LOG\"; chmod 755 \"$D/neuterd\"\n");
        // 2) (re)launch it; detached so it outlives this su call. setsid keeps it off our process group.
        s.append("pkill -f \"$D/neuterd\" 2>/dev/null\n");
        s.append("setsid \"$D/neuterd\" </dev/null >>\"$LOG\" 2>&1 &\n");
        s.append("sleep 1\n");
        s.append("if [ \"$(wc -c < /system/bin/reboot)\" -lt 100 ]; then echo '  reboot NEUTERED (global ns, self-healing)' >> \"$LOG\"; ");
        s.append("else echo \"  !! neuter NOT applied yet (reboot=$(wc -c < /system/bin/reboot) bytes)\" >> \"$LOG\"; fi\n");
        // 3) grant FreeMiko the overlay app-op so the nav bar draws with no manual toggle
        s.append("( appops set ").append(PKG).append(" SYSTEM_ALERT_WINDOW allow || cmd appops set ").append(PKG).append(" SYSTEM_ALERT_WINDOW allow ) 2>>\"$LOG\" && echo '  overlay app-op granted' >> \"$LOG\"\n");
        if (ENABLE_ADB_TCP) {
            s.append("setprop service.adb.tcp.port 5555\n");
            s.append("setprop ctl.restart adbd\n");
            s.append("settings put global stay_on_while_plugged_in 3 2>>\"$LOG\" || true\n");
            s.append("echo \"  adb tcp 5555 requested; init.svc.adbd=$(getprop init.svc.adbd)\" >> \"$LOG\"\n");
        }
        s.append("echo '  done.' >> \"$LOG\"\n");
        s.append("wc -c < /system/bin/reboot\n");   // echoed back so we can read the result
        return s.toString();
    }

    /** Apply the neuter. Returns true if /system/bin/reboot ended up shadowed (< 100 bytes). */
    public static boolean apply(Context ctx) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("/system/bin/su");
            OutputStream os = p.getOutputStream();
            os.write(payload().getBytes("UTF-8"));
            os.flush();
            os.close();
            String last = null, line;
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = r.readLine()) != null) last = line.trim();
            int rc = p.waitFor();
            int rebootBytes = -1;
            try { if (last != null) rebootBytes = Integer.parseInt(last); } catch (NumberFormatException ignore) { }
            boolean ok = rebootBytes >= 0 && rebootBytes < 100;
            Log.i(TAG, "neuter apply rc=" + rc + " reboot=" + rebootBytes + "B ok=" + ok);
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "neuter apply FAILED (root unavailable?)", e);
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }
}
