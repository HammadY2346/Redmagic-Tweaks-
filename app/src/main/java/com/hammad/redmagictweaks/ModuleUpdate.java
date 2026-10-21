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

    public static void check(final Context context) {
        if (checked) return;
        checked = true;
        final Context c = context.getApplicationContext();
        new Thread(() -> {
            try {
                long installed = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime / 1000;
                Map<String, Long> started = Root.startTimes(Catalog.allPackages());
                List<String> stale = new ArrayList<>();
                for (Map.Entry<String, Long> e : started.entrySet()) if (e.getValue() + 2 < installed) stale.add(e.getKey());
                if (!stale.isEmpty()) {
                    Pending.addUpdate(c, stale);
                    LogStore.append(c, LogStore.stamp() + " [app] still running the previous build: " + String.join(", ", stale));
                }
            } catch (Throwable ignored) { }
        }, "rmt-update-check").start();
    }
}
