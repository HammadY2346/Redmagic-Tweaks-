package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.LinkedHashSet;

/** Home / launcher packages currently installed (stock + third-party). */
public final class Homes {
    private Homes() {}

    /** Stock RedMagic / ZTE launcher. */
    public static final String STOCK = "com.zte.mifavor.launcher";

    /** True when this feature's pack / shape must reach every home app, not only the stock launcher. */
    public static boolean globalIcons(String key) {
        return Features.LAUNCHER_ICON_PACK.equals(key) || Features.LAUNCHER_ICON_SHAPE.equals(key);
    }

    /** Stock launcher plus every package that handles {@link Intent#CATEGORY_HOME}. */
    public static String[] packages(Context c) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(STOCK);
        if (c == null) return out.toArray(new String[0]);
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(home, PackageManager.MATCH_ALL)) {
                if (ri != null && ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    out.add(ri.activityInfo.packageName);
                }
            }
        } catch (Throwable ignored) { }
        return out.toArray(new String[0]);
    }

    /** True when {@code pkg} is a home app (or the stock launcher). */
    public static boolean isHome(Context c, String pkg) {
        if (pkg == null) return false;
        if (STOCK.equals(pkg)) return true;
        for (String p : packages(c)) if (pkg.equals(p)) return true;
        return false;
    }
}
