package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Safe mode and "reset all settings". Safe mode keeps every setting: it only sets the flag every hook checks first
 * (the module setting and the system property persist.redmagictweaks.safe), then restarts the hooked apps so they
 * come back without tweaks. Turning it off restarts them again with everything back.
 */
public final class Safety {
    private Safety() {}

    public static boolean safeMode(Context c) { return Prefs.on(c, Features.SAFE_MODE); }

    /** Run off the UI thread: sets the flag and restarts System UI and every hooked app. */
    public static Root.Result setSafeMode(Context c, boolean on) {
        Prefs.set(c, Features.SAFE_MODE, on);
        Root.Result prop = Root.setSafeProp(on);
        Root.Result r = Root.restartApps(Catalog.allPackages());
        Pending.clearSystemUi(c);
        Pending.clearApps(c);
        return prop.ok() ? r : prop;
    }

    /** Name of the layout "reset all" saves first. */
    public static String backupName() {
        return "Before reset " + new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
    }

    /** Saves the current setup as a layout, then every setting goes back to stock (safe mode stays as it is). */
    public static String resetAll(Context c) throws Exception {
        String name = backupName();
        LayoutStore.saveCurrent(c, name);
        SharedPreferences sp = Prefs.get(c);
        boolean safe = safeMode(c);
        SharedPreferences.Editor e = sp.edit();
        for (Map.Entry<String, ?> en : sp.getAll().entrySet()) if (!Features.SAFE_MODE.equals(en.getKey())) e.remove(en.getKey());
        e.putBoolean(Features.SAFE_MODE, safe);
        e.commit();
        Pending.addEverything(c);
        return name;
    }
}
