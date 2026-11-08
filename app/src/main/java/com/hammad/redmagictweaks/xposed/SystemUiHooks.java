package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;

import com.hammad.redmagictweaks.Features;

import java.io.File;
import java.io.InputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** com.android.systemui — charging animation set / built-in & imported frames / play on every screen-on. */
final class SystemUiHooks {
    private SystemUiHooks() {}

    private static final String CHARGE_UTILS = "com.zte.feature.charging.ChargeUtils";
    private static final String CHARGING_FEATURE = "com.zte.feature.charging.ChargingFeature";
    private static final String CHARGING_VIEW = "com.zte.feature.charging.ChargingView";
    private static final String OPERATOR_UTILS = "com.zte.customize.OperatorUtils";
    private static final Uri PROVIDER = Uri.parse("content://" + com.hammad.redmagictweaks.LogProvider.AUTHORITY);
    private static final String FILE_BASE = com.hammad.redmagictweaks.ChargeSets.FILE_BASE;

    /** Frame counts of served folders, asked from the module app (cached 5 s). */
    private static final java.util.Map<String, long[]> COUNTS = new java.util.HashMap<>();
    /** The folders the animation now playing is made of, in order, and their frame counts (set when it starts). */
    private static volatile String[] segs;
    private static volatile int[] segCounts;
    private static boolean warnedMissing;

    static void install(ClassLoader cl) {
        chargingSet(cl);
        everyScreenOn(cl);
    }

    private static String[] picked() {
        int i = Cfg.choice(Features.CHARGE_SET);
        if (i < 0 || i >= Features.CHARGE_SET_DEFS.length) i = 0;
        return Features.CHARGE_SET_DEFS[i];
    }
    /** The set in effect: the picked one, or for "Follow the theme" the applied theme's set. */
    private static String[] def() {
        String[] d = picked();
        if (!"theme".equals(d[1])) return d;
        String[] t = com.hammad.redmagictweaks.ChargeSets.forTheme(themeId, lastFast);
        return new String[]{d[0] + " -> " + t[1], t[0], t[1]};
    }

    // ---- applied theme (for "Follow the theme") ----
    private static final String THEME_CHANGED = "com.mifavor.intent.action.THEME_CHANGED_YRZX";
    private static volatile String themeId;
    private static volatile boolean lastFast;
    private static boolean receiverOn;

    private static android.content.SharedPreferences store(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("rmtweaks_charging", Context.MODE_PRIVATE);
    }
    private static void themeChanged(Context c, String id) {
        if (id == null || id.equals(themeId)) return;
        themeId = id;
        try { store(c).edit().putString("theme_id", id).apply(); } catch (Throwable ignored) { }
        Lx.i("charging: theme now " + id + " -> " + java.util.Arrays.toString(com.hammad.redmagictweaks.ChargeSets.forTheme(id, false)));
    }
    /** Remembered theme id + a receiver for theme changes (once, when System UI has a context). */
    private static synchronized void watchTheme(Context c) {
        if (receiverOn || c == null) return;
        receiverOn = true;
        try { if (themeId == null) themeId = store(c).getString("theme_id", null); } catch (Throwable ignored) { }
        try {
            android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context ctx, android.content.Intent in) { themeChanged(ctx, in.getStringExtra("resId")); }
            };
            android.content.IntentFilter f = new android.content.IntentFilter(THEME_CHANGED);
            if (android.os.Build.VERSION.SDK_INT >= 33) c.registerReceiver(r, f, Context.RECEIVER_EXPORTED); else c.registerReceiver(r, f);
            Lx.i("charging: watching theme changes (current " + themeId + ")");
        } catch (Throwable t) { Lx.w("charging: theme receiver failed " + t); }
    }
    private static String kind() { return def()[1]; }
    private static String arg() { return def()[2]; }
    /** Our frames (served by the module app) instead of System UI's. */
    private static boolean served() { String k = kind(); return k.equals("asset") || k.equals("custom") || k.equals("staged"); }
    /** One full-screen frame layer only: no stock middle / bottom layers under it. */
    private static boolean ownLayer() { return served() || kind().equals("file"); }

    private static int count(Context c, String folder) {
        long now = System.currentTimeMillis();
        synchronized (COUNTS) {
            long[] e = COUNTS.get(folder);
            if (e != null && now - e[1] < 5000) return (int) e[0];
        }
        int n;
        try {
            Bundle b = c.getContentResolver().call(PROVIDER, "frame_count", folder, null);
            n = b == null ? 0 : b.getInt("count", 0);
        } catch (Throwable t) { n = 0; Lx.w("charging: frame count unavailable " + t); }
        synchronized (COUNTS) { COUNTS.put(folder, new long[]{n, now}); }
        return n;
    }

    private static int battery(Context c) {
        try {
            android.os.BatteryManager bm = (android.os.BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
            return bm == null ? -1 : bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Throwable t) { return -1; }
    }

    /** Works out the folders to play now (staged sets: by charger speed and battery level). Returns the frame total. */
    private static int plan(Context c, boolean fast) {
        String k = kind(), a = arg();
        String[] want;
        if (k.equals("staged")) {
            String speed = fast && count(c, a + "_fast") > 0 ? "_fast" : "_normal";
            int level = battery(c);
            want = level >= com.hammad.redmagictweaks.ChargeSets.STAGE_FULL_FROM
                    ? new String[]{a + speed + "_mid", a + speed + "_full"}   // staff turning into the full rod
                    : new String[]{a + speed};
            Lx.i("charging: " + a + " at " + level + " %" + (fast ? " (fast)" : "") + " -> " + java.util.Arrays.toString(want));
        } else {
            want = new String[]{a};
        }
        int[] n = new int[want.length];
        int total = 0;
        for (int i = 0; i < want.length; i++) { n[i] = count(c, want[i]); total += n[i]; }
        segs = want; segCounts = n;
        return total;
    }

    private static String filePattern(boolean fast) {
        String[] p = arg().split("\\|");
        return p[fast && p.length > 1 ? 1 : 0];
    }

    private static boolean fileSetPresent(boolean fast) {
        String f = FILE_BASE + String.format(filePattern(fast), "01");
        return new File(f).exists();
    }

    /** The "fast charging" argument of getChargeBitmapByIndex: the boolean right after the Options one (firmwares add args). */
    private static boolean fastArg(Object[] args) {
        for (int i = 0; i < args.length - 1; i++) if (args[i] instanceof BitmapFactory.Options || (args[i] == null && i == 4)) {
            for (int j = i + 1; j < args.length; j++) if (args[j] instanceof Boolean) return (Boolean) args[j];
        }
        for (int j = args.length - 1; j >= 0; j--) if (args[j] instanceof Boolean) return (Boolean) args[j];
        return false;
    }
    private static BitmapFactory.Options optsArg(Object[] args) {
        for (Object o : args) if (o instanceof BitmapFactory.Options) return (BitmapFactory.Options) o;
        return null;
    }

    private static void chargingSet(ClassLoader cl) {
        final Class<?> cu = H.cls(cl, CHARGE_UTILS);
        if (cu == null) return;

        if (kind().equals("flag")) {
            Class<?> ou = H.cls(cl, OPERATOR_UTILS);
            if (ou != null) {
                try { XposedHelpers.setStaticBooleanField(ou, arg(), true); Lx.i("charging: " + arg() + " forced on"); }
                catch (Throwable t) { Lx.e("charging: cannot set " + arg(), t); }
            }
        }

        // getCurrentDrawable(plugType, usingTheme, isRedMagic, fast) -> frame table (its length = frame count)
        H.hookAll(cu, "getCurrentDrawable", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                boolean fast = p.args.length > 0 && Boolean.TRUE.equals(p.args[p.args.length - 1]);
                lastFast = fast;
                watchTheme(Lx.context());
                String k = kind();
                switch (k) {
                    case "table": {
                        String field = arg();
                        if (fast) {
                            String f2 = field.equals("Drawable") ? "Fast_Drawable" : field.replace("_Drawable", "_Fast_Drawable");
                            if (XposedHelpers.findFieldIfExists(cu, f2) != null) field = f2;
                        }
                        try {
                            Object arr = XposedHelpers.getStaticObjectField(cu, field);
                            if (arr instanceof int[]) p.setResult(arr);
                        } catch (Throwable t) { if (!warnedMissing) { warnedMissing = true; Lx.w("charging: no table " + field + " on this firmware, using stock"); } }
                        break;
                    }
                    case "file": {
                        if (!fileSetPresent(fast)) {
                            if (!warnedMissing) { warnedMissing = true; Lx.w("charging: collab files not shipped on this firmware (" + FILE_BASE + filePattern(fast) + ") — using stock"); }
                            return;
                        }
                        p.setResult(new int[75]);
                        break;
                    }
                    case "asset": case "custom": case "staged": {
                        Context c = Lx.context();
                        int n = c == null ? 0 : plan(c, fast);
                        if (n <= 0) { if (!warnedMissing) { warnedMissing = true; Lx.w("charging: no frames for set " + arg()); } return; }
                        p.setResult(new int[n]);
                        break;
                    }
                    default: break;
                }
            }
        });

        // getChargeBitmapByIndex(i, res, configCustom, table, options, fast[, plugType]) -> our frame.
        // Global 15/16 have the 6-argument form, CN 11.0.11 a 7-argument one: hook every overload, read args by type.
        int hooked = H.hookAll(cu, "getChargeBitmapByIndex", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                String k = kind();
                if (!(p.args.length > 0 && p.args[0] instanceof Integer)) return;
                int i = (Integer) p.args[0];
                BitmapFactory.Options opts = optsArg(p.args);
                boolean fast = fastArg(p.args);
                if (k.equals("file")) {
                    if (!fileSetPresent(fast)) return;
                    String idx = (i + 1 > 9 ? "" : "0") + (i + 1);
                    Bitmap b = BitmapFactory.decodeFile(FILE_BASE + String.format(filePattern(fast), idx), opts);
                    if (b != null) p.setResult(b);
                    return;
                }
                if (!served()) return;
                Context c = Lx.context();
                if (c == null) return;
                String[] sg = segs; int[] sc = segCounts;
                if (sg == null || sc == null) { plan(c, fast); sg = segs; sc = segCounts; }
                String folder = sg[sg.length - 1];
                int off = i;
                for (int s = 0; s < sg.length; s++) {
                    if (off < sc[s]) { folder = sg[s]; break; }
                    off -= sc[s];
                    if (s == sg.length - 1) { folder = sg[s]; off = Math.max(0, sc[s] - 1); }
                }
                try (InputStream in = c.getContentResolver().openInputStream(Uri.parse("content://" + com.hammad.redmagictweaks.LogProvider.AUTHORITY + "/charging/" + folder + "/" + off))) {
                    if (in == null) return;
                    Bitmap b = BitmapFactory.decodeStream(in, null, opts);
                    if (b != null) p.setResult(b);
                } catch (Throwable t) {
                    Lx.w("charging: frame " + off + " of " + folder + " failed: " + t);
                }
            }
        });
        // our sets / firmware files are one full-screen layer: no stock middle or bottom layers
        H.hookAll(cu, "getCurrentMiddleDrawable", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { if (ownLayer()) p.setResult(null); }
        });
        Class<?> cv = H.cls(cl, CHARGING_VIEW);
        if (cv != null) {
            XC_MethodHook noLayer = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { if (ownLayer()) p.setResult(true); }
            };
            H.hookAll(cv, "isNotShowMiddleCharging", noLayer);
            H.hookAll(cv, "isNotShowBottomCharging", noLayer);
        }
        // the theme-changed broadcast also reaches System UI's own receiver (ChargingFeature.handleBroadcasts)
        Class<?> cf = H.cls(cl, CHARGING_FEATURE);
        if (cf != null) H.hookAll(cf, "handleBroadcasts", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length < 2 || !(p.args[1] instanceof android.content.Intent)) return;
                android.content.Intent in = (android.content.Intent) p.args[1];
                if (THEME_CHANGED.equals(in.getAction()) && p.args[0] instanceof Context) themeChanged((Context) p.args[0], in.getStringExtra("resId"));
            }
        });
        Lx.i("SystemUI: charging hooks ready (set=" + def()[0] + ", frame hook " + (hooked > 0 ? "on" : "MISSING") + ")");
    }

    private static void everyScreenOn(ClassLoader cl) {
        Class<?> cf = H.cls(cl, CHARGING_FEATURE);
        if (cf == null) return;
        H.hook(H.find(cf, "onFinishedWakingUp"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                watchTheme(Lx.context());   // early, so a theme change is seen before the next charge
                if (!Cfg.on(Features.CHARGE_EVERY_LOCK)) return;
                try {
                    Object f = p.thisObject;
                    boolean charging = Boolean.TRUE.equals(XposedHelpers.callMethod(f, "isCharging"));
                    boolean showing = Boolean.TRUE.equals(XposedHelpers.callMethod(f, "isKeyguardViewShowing"));
                    boolean anim = Boolean.TRUE.equals(XposedHelpers.callMethod(f, "isAnimationShowing"));
                    if (charging && showing && !anim) {
                        XposedHelpers.callMethod(f, "startChargingAnimation", 300L);
                        Lx.i("charging: replayed on wake-up");
                    }
                } catch (Throwable t) { Lx.e("charging: wake-up replay", t); }
            }
        });
    }
}
