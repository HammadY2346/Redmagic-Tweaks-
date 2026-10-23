package com.hammad.redmagictweaks.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hammad.redmagictweaks.LogStore;
import com.hammad.redmagictweaks.Pending;
import com.hammad.redmagictweaks.R;
import com.hammad.redmagictweaks.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * The one bar at the bottom of every screen that collects what is waiting for a restart ("3 changes need a System UI
 * restart · Restart") and applies it. System UI is checked afterwards (its process id has to change): only then the bar
 * says "Applied"; otherwise it offers a soft reboot instead of claiming success.
 */
final class RestartBar {
    private final Activity a;
    private final View bar;
    private final TextView text;
    private final MaterialButton action, secondary;
    private boolean busy;
    private final SharedPreferences.OnSharedPreferenceChangeListener watch;

    RestartBar(Activity a) {
        this.a = a;
        bar = a.findViewById(R.id.restart_bar);
        text = a.findViewById(R.id.restart_text);
        action = a.findViewById(R.id.restart_action);
        secondary = a.findViewById(R.id.restart_secondary);
        watch = (sp, key) -> { if ("pending".equals(key)) a.runOnUiThread(this::update); };
        Pending.watch(a, watch);
        Ui.fonts(bar);
        update();
    }

    void destroy() { Pending.unwatch(a, watch); }

    void update() {
        if (bar == null || busy) return;
        int sys = Pending.systemUi(a), reboot = Pending.reboot(a), appChanges = Pending.appChanges(a);
        List<String> apps = Pending.apps(a);
        if (sys == 0 && reboot == 0 && apps.isEmpty()) { bar.setVisibility(View.GONE); return; }
        List<String> parts = new ArrayList<>();
        if (sys > 0) parts.add("System UI");
        for (String p : apps) parts.add(appName(p));
        int changes = Math.max(sys, 0) + appChanges;
        StringBuilder s = new StringBuilder();
        if (!parts.isEmpty()) {
            boolean one = changes <= 1;
            s.append(one ? "1 change needs " : changes + " changes need ");
            if (sys > 0 && apps.isEmpty()) s.append("a System UI restart");
            else s.append(join(parts)).append(" to restart");
            if (reboot > 0) s.append(reboot == 1 ? ", 1 a reboot" : ", " + reboot + " a reboot");
        } else {
            s.append(reboot == 1 ? "1 change needs a reboot" : reboot + " changes need a reboot");
        }
        if (Pending.update(a) && !parts.isEmpty()) {
            // a module update the running apps have not loaded yet
            s = new StringBuilder("Update installed: ").append(join(parts)).append(parts.size() == 1 ? " needs" : " need").append(" a restart to load it");
            if (reboot > 0) s.append(reboot == 1 ? ", 1 change a reboot" : ", " + reboot + " changes a reboot");
        }
        if (Pending.rebootForUpdate(a)) {
            // system_server keeps the old build until a reboot (game view, triggers, boost, refresh rate...)
            s = new StringBuilder("Update installed: reboot the phone to load it");
            if (!parts.isEmpty()) s.append(" (Restart only reloads ").append(join(parts)).append("; game and system tweaks need the reboot)");
        }
        text.setText(s);
        if (!parts.isEmpty()) {
            action.setText("Restart");
            action.setOnClickListener(v -> restart());
            secondary.setVisibility(reboot > 0 ? View.VISIBLE : View.GONE);
            secondary.setText("Reboot");
            secondary.setOnClickListener(v -> reboot());
        } else {
            action.setText("Reboot");
            action.setOnClickListener(v -> reboot());
            secondary.setVisibility(View.GONE);
        }
        bar.setVisibility(View.VISIBLE);
    }

    private static String join(List<String> l) {
        if (l.size() <= 2) return String.join(" and ", l);
        return String.join(", ", l.subList(0, l.size() - 1)) + " and " + l.get(l.size() - 1);
    }

    static String appName(String pkg) {
        switch (pkg) {
            case "com.zte.mifavor.launcher": return "the launcher";
            case "com.android.settings": return "Settings";
            case "com.zte.beautify": return "Theme Store";
            case "cn.nubia.gameassist": case "cn.nubia.gamelauncher": case "cn.nubia.gamehelpmodule": case "cn.nubia.gamelab": case "cn.nubia.gamehighlights":
                return "Game Space";
            case "com.fingerprint.sensorservice": case "com.zte.fingerprints": return "fingerprint";
            case "com.android.packageinstaller": return "installer";
            default:
                String[] p = pkg.split("\\.");
                return p[p.length - 1];
        }
    }

    private void restart() {
        busy = true;
        text.setText("Restarting…");
        action.setEnabled(false);
        final boolean sys = Pending.systemUi(a) > 0;
        final List<String> apps = Pending.apps(a);
        new Thread(() -> {
            String msg = null;
            boolean sysFailed = false;
            if (!apps.isEmpty()) {
                Root.Result r = Root.restartApps(apps.toArray(new String[0]));
                if (!r.ok()) msg = "Root command failed: " + r.out;
                else Pending.clearApps(a);
            }
            if (sys) {
                Root.Result r = Root.restartSystemUi();
                LogStore.append(a, LogStore.stamp() + " [app] " + r.out);
                if (r.ok()) Pending.clearSystemUi(a);
                else if (r.code == 2) sysFailed = true;
                else msg = "Root command failed: " + r.out;
            }
            final String m = msg;
            final boolean failed = sysFailed;
            a.runOnUiThread(() -> {
                busy = false;
                action.setEnabled(true);
                if (failed) {
                    new MaterialAlertDialogBuilder(a).setTitle("System UI did not restart")
                            .setMessage("Its process kept running, so the changes are not showing yet. A soft reboot restarts it for sure (about 15 seconds).")
                            .setPositiveButton("Soft reboot", (d, w) -> softReboot())
                            .setNegativeButton("Later", null).show();
                } else if (m != null) {
                    Toast.makeText(a, m, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(a, "Applied", Toast.LENGTH_SHORT).show();
                }
                update();
            });
        }).start();
    }

    private void reboot() {
        new MaterialAlertDialogBuilder(a).setTitle("Reboot needed")
                .setMessage("Framework tweaks load when Android starts. A soft reboot restarts Android without restarting the phone (about 15 seconds); a full reboot takes longer.")
                .setPositiveButton("Reboot", (d, w) -> { Pending.clearAll(a); new Thread(Root::reboot).start(); })
                .setNeutralButton("Soft reboot", (d, w) -> softReboot())
                .setNegativeButton("Later", null).show();
    }

    private void softReboot() {
        Pending.clearAll(a);
        new Thread(Root::softReboot).start();
    }
}
