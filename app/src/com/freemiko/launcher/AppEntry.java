package com.freemiko.launcher;

import android.graphics.drawable.Drawable;

/** One launchable app in the drawer. */
public final class AppEntry {
    public final String label;
    public final Drawable icon;
    public final String pkg;
    public final String activity;

    public AppEntry(String label, Drawable icon, String pkg, String activity) {
        this.label = label;
        this.icon = icon;
        this.pkg = pkg;
        this.activity = activity;
    }
}
