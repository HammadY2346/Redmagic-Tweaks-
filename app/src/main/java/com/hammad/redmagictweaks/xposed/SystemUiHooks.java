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
    private static final String OPERATOR_UTILS = "com.zte.customize.OperatorUtils";
    private static final Uri PROVIDER = Uri.parse("content://" + com.hammad.redmagictweaks.LogProvider.AUTHORITY);
    private static final String FILE_BASE = "/system/etc/custom_config/app/systemUI/charge/";

    private static int frameCount = -1;
    private static long frameCountAt;
    private static String frameCountSet;
    private static boolean warnedMissing;

    static void install(ClassLoader cl) {
        chargingSet(cl);
        everyScreenOn(cl);
    }

    private static String[] def() {
        int i = Cfg.choice(Features.CHARGE_SET);
        if (i < 0 || i >= Features.CHARGE_SET_DEFS.length) i = 0;
        return Features.CHARGE_SET_DEFS[i];
    }
    private static String kind() { return def()[1]; }
    private static String arg() { return def()[2]; }
    private static boolean served() { String k = kind(); return k.equals("asset") || k.equals("custom"); }

    /** Frames available for the selected served set (asked from the module app, cached 5 s). */
    private static int servedFrames(Context c) {
        String set = arg();
        long now = System.currentTimeMillis();
        if (frameCount >= 0 && set.equals(frameCountSet) && now - frameCountAt < 5000) return frameCount;
        try {
            Bundle b = c.getContentResolver().call(PROVIDER, "frame_count", set, null);
            frameCount = b == null ? 0 : b.getInt("count", 0);
        } catch (Throwable t) { frameCount = 0; Lx.w("charging: frame count unavailable " + t); }
        frameCountAt = now; frameCountSet = set;
        return frameCount;
    }

    private static String filePattern(boolean fast) {
        String[] p = arg().split("\\|");
        return p[fast && p.length > 1 ? 1 : 0];
    }

    private static boolean fileSetPresent(boolean fast) {
        String f = FILE_BASE + String.format(filePattern(fast), "01");
        return new File(f).exists();
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
        H.hook(H.find(cu, "getCurrentDrawable", int.class, boolean.class, boolean.class, boolean.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                String k = kind();
                boolean fast = Boolean.TRUE.equals(p.args[3]);
                switch (k) {
                    case "table": {
                        String field = arg();
                        if (fast) {
                            String f2 = field.replace("_Drawable", "_Fast_Drawable");
                            if (field.equals("Drawable")) f2 = "Fast_Drawable";
                            if (XposedHelpers.findFieldIfExists(cu, f2) != null) field = f2;
                        }
                        try {
                            Object arr = XposedHelpers.getStaticObjectField(cu, field);
                            if (arr instanceof int[]) p.setResult(arr);
                        } catch (Throwable t) { Lx.e("charging: table " + field, t); }
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
                    case "asset": case "custom": {
                        Context c = Lx.context();
                        int n = c == null ? 0 : servedFrames(c);
                        if (n <= 0) { if (!warnedMissing) { warnedMissing = true; Lx.w("charging: no frames for set " + arg()); } return; }
                        p.setResult(new int[n]);
                        break;
                    }
                    default: break;
                }
            }
        });

        // getChargeBitmapByIndex(i, res, configCustom, table, options, fast, plugType) -> our frame
        H.hook(H.find(cu, "getChargeBitmapByIndex", int.class, android.content.res.Resources.class, boolean.class, int[].class, BitmapFactory.Options.class, boolean.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                String k = kind();
                int i = (Integer) p.args[0];
                BitmapFactory.Options opts = (BitmapFactory.Options) p.args[4];
                boolean fast = Boolean.TRUE.equals(p.args[5]);
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
                try (InputStream in = c.getContentResolver().openInputStream(Uri.parse("content://" + com.hammad.redmagictweaks.LogProvider.AUTHORITY + "/charging/" + arg() + "/" + i))) {
                    if (in == null) return;
                    Bitmap b = BitmapFactory.decodeStream(in, null, opts);
                    if (b != null) p.setResult(b);
                } catch (Throwable t) {
                    Lx.w("charging: frame " + i + " of " + arg() + " failed: " + t);
                }
            }
        });
        // served / file sets carry no middle layer
        H.hook(H.find(cu, "getCurrentMiddleDrawable", boolean.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { if (served() || kind().equals("file")) p.setResult(null); }
        });
        Lx.i("SystemUI: charging hooks ready (set=" + def()[0] + ")");
    }

    private static void everyScreenOn(ClassLoader cl) {
        Class<?> cf = H.cls(cl, CHARGING_FEATURE);
        if (cf == null) return;
        H.hook(H.find(cf, "onFinishedWakingUp"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
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
