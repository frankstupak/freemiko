package com.freemiko.launcher;

import android.util.Log;

import java.io.OutputStream;

/**
 * A single long-lived root shell. This ROM's su takes its commands on stdin (no -c), so we keep one
 * `su` process open and write newline-terminated commands to it. Used by the nav bar to inject key
 * events (input keyevent ...) with low latency — no per-press su spawn.
 *
 * Thread-safe. Fail-soft: if root is unavailable the shell simply reports not-alive and callers no-op.
 */
public final class RootShell {

    private static final String TAG = "FreeMiko";
    private static final String SU = "/system/bin/su";

    private static RootShell sInstance;

    private Process proc;
    private OutputStream stdin;

    private RootShell() { }

    public static synchronized RootShell get() {
        if (sInstance == null) sInstance = new RootShell();
        sInstance.ensureAlive();
        return sInstance;
    }

    private synchronized void ensureAlive() {
        if (isAlive()) return;
        try {
            proc = Runtime.getRuntime().exec(SU);
            stdin = proc.getOutputStream();
            // Prove the channel works; harmless if it doesn't.
            stdin.write("id >/dev/null 2>&1\n".getBytes("UTF-8"));
            stdin.flush();
        } catch (Exception e) {
            Log.e(TAG, "RootShell: su unavailable", e);
            proc = null;
            stdin = null;
        }
    }

    public synchronized boolean isAlive() {
        if (proc == null || stdin == null) return false;
        try {
            proc.exitValue();   // throws IllegalThreadStateException while still running
            return false;       // exited
        } catch (IllegalThreadStateException running) {
            return true;
        }
    }

    /** Run one command line as root. Returns false if root is unavailable. */
    public synchronized boolean run(String cmd) {
        ensureAlive();
        if (!isAlive()) return false;
        try {
            stdin.write((cmd + "\n").getBytes("UTF-8"));
            stdin.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "RootShell.run failed: " + cmd, e);
            proc = null;
            stdin = null;
            return false;
        }
    }
}
