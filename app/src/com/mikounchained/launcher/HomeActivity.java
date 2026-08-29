package com.mikounchained.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Feature 1 — the HOME replacement. A clean app drawer plus a settings entry, set as the default HOME
 * so it replaces the interim KISS launcher. On first run it applies the watchdog neuter (Feature 3)
 * and it always ensures the nav bar service (Feature 2) is running.
 */
public class HomeActivity extends Activity {

    private static final String PREFS = "mikounchained";
    private static final String KEY_FIRST_RUN = "first_run_done";

    private GridView grid;
    private final List<AppEntry> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        grid = findViewById(R.id.app_grid);
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showSettings(); }
        });

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                launch(apps.get(pos));
            }
        });

        ensureNavBar();
        firstRunNeuter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();          // refresh drawer each time we return home (installs/removals)
        ensureNavBar();
    }

    /** Start (or re-assert) the overlay nav bar service. */
    private void ensureNavBar() {
        Intent i = new Intent(this, NavBarService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    /** Apply the watchdog neuter once, off the UI thread. BootReceiver re-applies every boot. */
    private void firstRunNeuter() {
        final SharedPreferences p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(KEY_FIRST_RUN, false)) return;
        new Thread(new Runnable() {
            public void run() {
                final boolean ok = Neuter.apply(HomeActivity.this);
                p.edit().putBoolean(KEY_FIRST_RUN, true).apply();
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(HomeActivity.this,
                                ok ? "MikoUnchained: watchdog neutered" : "MikoUnchained: neuter needs root — see INSTALL.md",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void loadApps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN, null);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = getPackageManager().queryIntentActivities(launcher, 0);
        String self = getPackageName();
        apps.clear();
        for (ResolveInfo ri : ris) {
            if (self.equals(ri.activityInfo.packageName)) continue;   // hide ourselves
            apps.add(new AppEntry(
                    ri.loadLabel(getPackageManager()).toString(),
                    ri.loadIcon(getPackageManager()),
                    ri.activityInfo.packageName,
                    ri.activityInfo.name));
        }
        Collections.sort(apps, new Comparator<AppEntry>() {
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        grid.setAdapter(new AppListAdapter(this, apps));
    }

    private void launch(AppEntry e) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setClassName(e.pkg, e.activity);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSettings() {
        String[] items = {
                "Android Settings",
                "Re-apply watchdog neuter",
                "Restart nav bar",
        };
        new AlertDialog.Builder(this)
                .setTitle("MikoUnchained")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        switch (which) {
                            case 0:
                                startActivity(new Intent(Settings.ACTION_SETTINGS)
                                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                break;
                            case 1:
                                new Thread(new Runnable() {
                                    public void run() {
                                        final boolean ok = Neuter.apply(HomeActivity.this);
                                        runOnUiThread(new Runnable() {
                                            public void run() {
                                                Toast.makeText(HomeActivity.this,
                                                        ok ? "Neuter re-applied" : "Neuter failed (root?)",
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                }).start();
                                break;
                            case 2:
                                stopService(new Intent(HomeActivity.this, NavBarService.class));
                                ensureNavBar();
                                break;
                        }
                    }
                })
                .show();
    }

    /** HOME semantics: never let BACK leave the launcher. */
    @Override
    public void onBackPressed() { /* no-op: this is home */ }
}
