package com.hammad.redmagictweaks;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LSPosed loads a module into an app when that app starts, so after installing a new build of this app, System UI and
 * the other hooked apps keep running the old hooks until they restart. Once per app launch this finds the hooked apps
 * whose process started before the installed build and puts them on the restart bar.
 */
public final class ModuleUpdate {
    private ModuleUpdate() {}

    private static volatile boolean checked;
    private static final String SYSTEM_SERVER = "system_server";

    public static void check(final Context context) {
        if (checked) return;
        checked = true;
        final Context c = context.getApplicationContext();
        new Thread(() -> {
            try {
                long installed = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime / 1000;
                List<String> names = new ArrayList<>(java.util.Arrays.asList(Catalog.allPackages()));
                names.add(SYSTEM_SERVER);
                Map<String, Long> started = Root.startTimes(names.toArray(new String[0]));
                List<String> stale = new ArrayList<>();
                boolean system = false;
                for (Map.Entry<String, Long> e : started.entrySet()) {
                    if (e.getValue() + 2 >= installed) continue;
                    if (SYSTEM_SERVER.equals(e.getKey())) system = true; else stale.add(e.getKey());
                }
                if (!stale.isEmpty()) {
                    Pending.addUpdate(c, stale);
                    LogStore.append(c, LogStore.stamp() + " [app] still running the previous build: " + String.join(", ", stale));
                }
                if (system) {
                    // a System UI restart does not reload system_server (Game view, triggers, boost, refresh rate...)
                    Pending.addRebootUpdate(c);
                    LogStore.append(c, LogStore.stamp() + " [app] the system (system_server) still runs the previous build: reboot the phone to load the new game, refresh-rate and system tweaks");
                }
            } catch (Throwable ignored) { }
        }, "rmt-update-check").start();
    }
}
