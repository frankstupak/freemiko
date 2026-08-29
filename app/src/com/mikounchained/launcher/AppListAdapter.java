package com.mikounchained.launcher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/** Grid adapter for the app drawer. Framework-only (no androidx) to keep the no-Gradle build clean. */
public final class AppListAdapter extends BaseAdapter {

    private final Context ctx;
    private final List<AppEntry> items;
    private final LayoutInflater inflater;

    public AppListAdapter(Context ctx, List<AppEntry> items) {
        this.ctx = ctx;
        this.items = items;
        this.inflater = LayoutInflater.from(ctx);
    }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView : inflater.inflate(R.layout.item_app, parent, false);
        AppEntry e = items.get(position);
        ((ImageView) v.findViewById(R.id.app_icon)).setImageDrawable(e.icon);
        ((TextView) v.findViewById(R.id.app_label)).setText(e.label);
        return v;
    }
}
