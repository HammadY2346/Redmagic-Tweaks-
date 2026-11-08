package com.hammad.redmagictweaks;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

/**
 * Writes the chosen app-icon shape into the real framework mask ({@code config_icon_mask}), the same string
 * Settings changes, so every launcher sees it — including One UI Home, which LSPosed does not inject until it is
 * ticked in the module scope. Then the home apps are restarted so they drop their icon cache.
 */
public final class IconMask {
    private IconMask() {}

    /** One UI Home package (the ported launcher). Must be ticked in LSPosed for icon packs. */
    public static final String ONE_UI = "com.sec.android.app.launcher";
    private static final String OVERLAY = "com.android.shell:rmtweaks_iconmask";

    public static boolean oneUiInstalled(Context c) {
        try { return c.getPackageManager().getPackageInfo(ONE_UI, 0) != null; }
        catch (Throwable t) { return false; }
    }

    /** Apply the saved shape system-wide and restart every home app. */
    public static void apply(Context c) { apply(c, true); }

    /** @param restart false only re-asserts the overlay (used when the app opens, so the launcher is not killed every time). */
    public static void apply(Context c, boolean restart) {
        final int shape = Prefs.choice(c, Features.LAUNCHER_ICON_SHAPE);
        final String[] homes = Homes.packages(c);
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            Root.Result r = Root.run(script(shape, homes, restart));
            if (restart) Root.restartApps(homes);
            if (!r.ok() && c instanceof Activity) {
                String msg = "Icon shape was not written system-wide (root?). " + r.out;
                ((Activity) c).runOnUiThread(() -> Toast.makeText(app, msg, Toast.LENGTH_LONG).show());
            }
        }, "icon-mask").start();
    }

    /** Drop cached icons and restart home apps after an icon-pack change. */
    public static void refreshHomes(Context c) {
        final String[] homes = Homes.packages(c);
        new Thread(() -> {
            Root.run(wipe(homes) + "true");
            Root.restartApps(homes);
        }, "icon-refresh").start();
    }

    private static String[] script(int shape, String[] homes, boolean restart) {
        String path = shape > 0 && shape < com.hammad.redmagictweaks.xposed.LauncherIcons.SHAPE_PATHS.length
                ? com.hammad.redmagictweaks.xposed.LauncherIcons.SHAPE_PATHS[shape] : null;
        String overlay;
        if (path == null) {
            overlay = "cmd overlay disable --user 0 " + OVERLAY + " >/dev/null 2>&1 || true";
        } else {
            String q = path.replace("'", "'\\''");
            overlay = "cmd overlay fabricate --target android --user 0 --name rmtweaks_iconmask android:string/config_icon_mask 3 '" + q + "'"
                    + " && cmd overlay enable --user 0 " + OVERLAY;
        }
        if (!restart) return new String[]{overlay};
        return new String[]{overlay, wipe(homes) + "true"};
    }

    private static String wipe(String[] homes) {
        StringBuilder b = new StringBuilder();
        for (String pkg : homes) {
            if (pkg == null || !pkg.matches("[A-Za-z0-9_.]+")) continue;
            b.append("for d in /data/data/").append(pkg).append(" /data/user/0/").append(pkg)
                    .append(" /data/user_de/0/").append(pkg).append("; do ")
                    .append("rm -f $d/databases/app_icons.db $d/databases/app_icons.db-journal $d/databases/app_icons.db-wal $d/databases/app_icons.db-shm ")
                    .append("$d/databases/icons.db $d/databases/icons.db-journal $d/databases/icons.db-wal $d/databases/icons.db-shm; done; ");
        }
        return b.toString();
    }
}
