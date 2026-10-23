package com.hammad.redmagictweaks.xposed;

import android.content.Context;

import com.hammad.redmagictweaks.ExtrasFeatures;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * "More apps" (docs/sweep-findings.md). Two mechanisms:
 *  1. ZTE feature flags: every app reads them through the framework class com.zte.feature.Feature
 *     (get / get(def) / getBoolean / getInt), including the reflection wrappers LoadMethodEx and
 *     ZteFeatureWrapper. We hook those four entry points once per process and answer from an override
 *     table built from the switches that are on. Flags are keyed case-insensitively (Feature lower-cases).
 *  2. Plain boolean helpers in the app (Phone Manager ads, Magic Voice block list, touping support checks).
 */
final class ExtrasHooks {
    private ExtrasHooks() {}

    private static final String FEATURE = "com.zte.feature.Feature";

    /** Switch key -> flags it forces (name=value). Evaluated on every lookup so toggles apply without reinstall. */
    private static final Object[][] FLAGS = {
        {ExtrasFeatures.GES_DOUBLE_TAP_SLEEP, "ZTE_FEATURE_DOUBLE_TAP_TO_SLEEP=true", "ZTE_FEATURE_DOUBLETAP=true"},
        {ExtrasFeatures.GES_RAISE_WAKE, "ZTE_FEATURE_RAISEWAKE=true"},
        {ExtrasFeatures.GES_SPLIT_SWIPE, "ZTE_FEATURE_SWIPE_TO_SPLIT_SCREEN=true", "MYOS_FEATURE_LOW_END_PHONE=false"},
        {ExtrasFeatures.GES_BLACK_LETTERS, "ZTE_FEATURE_BLACKCHAR=true"},
        {ExtrasFeatures.GES_RANGE_SHOT, "ZTE_FEATURE_THREE_FINGER_RANGE_SHOT=true", "ZTE_FEATURE_FINGERSSHOT=true"},
        {ExtrasFeatures.GES_POCKET, "ZTE_FEATURE_POCKETMODE=true"},
        {ExtrasFeatures.GES_SHAKE_LIGHT, "ZTE_FEATURE_SHAKE_FLASH_LIGHT=true"},
        {ExtrasFeatures.GES_MOTION, "ZTE_FEATURE_MOTIONANSWER=true", "ZTE_FEATURE_MOTIONALARM=true", "ZTE_FEATURE_SWITCHSPEAKER=true", "ZTE_FEATURE_WEAKEN_CALLRING=true"},
        {ExtrasFeatures.BAT_SUPER_MODE, "ZTE_FEATURE_REMOVE_SUPER_MODE=false", "ZTE_FEATURE_ULTRA_POWER_SAVE_MODE=true"},
        {ExtrasFeatures.BAT_REFRESH_RATE, "ZTE_FEATURE_POWER_SAVER_REMOVE_REFRESH_RATE=false"},
        {ExtrasFeatures.BAT_CHARGE_SEPARATION, "ZTE_FEATURE_BYPASS_CHARGE_SEPARATION=false"},
        {ExtrasFeatures.BAT_LONG_CHARGE, "ZTE_FEATURE_SHOW_LONG_CHARGE_NOTIFY=true"},
        {ExtrasFeatures.BAT_TIME, "ZTE_FEATURE_POWER_SAVER_NEED_SHOW_BATTERY_TIME=true"},
        {ExtrasFeatures.BAT_HEALTH, "ZTE_FEATURE_POWER_BATTERY_HEALTH=true"},
        {ExtrasFeatures.KEY_CONTINUOUS, "ZTE_FEATURE_KEYMAP_CONTINUOUS_CLICK=true"},
        {ExtrasFeatures.KEY_XGRAVITY, "ZTE_FEATURE_GAMEASSIST_PLUGIN_OPERATION_DEVICES=true", "ZTE_FEATURE_REDMAGIC_X_GRAVITY_GAMEPAD=true", "ZTE_FEATURE_LEARNED_BEHAVIOR_X_GRAVITY=true"},
        {ExtrasFeatures.LIGHT_FAN, "COLORFULLIGHT_SUPPORT_DAZZLING=true", "ZTE_FEATURE_GAME_FAN=true", "ZTE_FEATURE_FAN_COLORFUL_LIGHT=true"},
        {ExtrasFeatures.LIGHT_NEW_PROTOCOL, "ZTE_FEATURE_COLORFUL_USE_NEW_PROTOCOL=true"},
        {ExtrasFeatures.CAST_ALL_FEATURES, "ZTE_FEATURE_EXPAND_PROJECTION_SCREEN=true", "ZTE_FEATURE_DP_SUPPORT_ENABLE=true"},
    };

    /** Which switches are relevant per package — keeps the override table small and the log readable. */
    private static final Map<String, String[]> PER_PKG = new HashMap<>();
    static {
        PER_PKG.put("com.zte.zgesture", new String[]{ExtrasFeatures.GES_DOUBLE_TAP_SLEEP, ExtrasFeatures.GES_RAISE_WAKE, ExtrasFeatures.GES_SPLIT_SWIPE, ExtrasFeatures.GES_BLACK_LETTERS, ExtrasFeatures.GES_RANGE_SHOT, ExtrasFeatures.GES_POCKET, ExtrasFeatures.GES_SHAKE_LIGHT, ExtrasFeatures.GES_MOTION});
        PER_PKG.put("com.zte.powersavemode", new String[]{ExtrasFeatures.BAT_SUPER_MODE, ExtrasFeatures.BAT_REFRESH_RATE, ExtrasFeatures.BAT_CHARGE_SEPARATION, ExtrasFeatures.BAT_SEPARATION_100, ExtrasFeatures.BAT_LONG_CHARGE, ExtrasFeatures.BAT_TIME, ExtrasFeatures.BAT_HEALTH});
        PER_PKG.put("cn.nubia.keymapcenter", new String[]{ExtrasFeatures.KEY_CONTINUOUS, ExtrasFeatures.KEY_XGRAVITY});
        PER_PKG.put("cn.nubia.colorfullight", new String[]{ExtrasFeatures.LIGHT_FAN, ExtrasFeatures.LIGHT_NEW_PROTOCOL});
        PER_PKG.put("cn.nubia.touping", new String[]{ExtrasFeatures.CAST_ALL_FEATURES});
    }

    /** lower-case flag -> "true"/"false" for the switches that are currently on. */
    private static Map<String, String> overrides(String pkg) {
        Map<String, String> m = new HashMap<>();
        String[] keys = PER_PKG.get(pkg);
        if (keys == null) return m;
        for (String key : keys) {
            if (!Cfg.on(key)) continue;
            for (Object[] row : FLAGS) {
                if (!row[0].equals(key)) continue;
                for (int i = 1; i < row.length; i++) {
                    String[] kv = ((String) row[i]).split("=", 2);
                    m.put(kv[0].toLowerCase(Locale.ENGLISH), kv[1]);
                }
            }
        }
        return m;
    }

    static void install(ClassLoader cl, String pkg) {
        if (PER_PKG.containsKey(pkg)) installFlagOverride(cl, pkg);
        switch (pkg) {
            case "com.zte.powersavemode": installBattery(cl); break;
            case "cn.zte.chargeseparation": installChargeSeparation(cl); break;
            case "cn.nubia.gamepi": installGamePi(cl); break;
            case "com.zte.heartyservice": installPhoneManager(cl); break;
            case "cn.nubia.game.magicvoice": installMagicVoice(cl); break;
            case "cn.nubia.touping": installCast(cl); break;
            case "com.android.nfc": installNfc(cl); break;
            case "com.zte.cn.doubleapp": installClone(cl); break;
            default: break;
        }
    }

    // ---- 1. feature flags -------------------------------------------------------------------

    private static void installFlagOverride(ClassLoader cl, final String pkg) {
        Class<?> f = H.cls(cl, FEATURE);
        if (f == null) { Lx.w("Extras: " + FEATURE + " not found in " + pkg); return; }
        final java.util.Set<String> logged = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
        XC_MethodHook cb = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!(p.args[0] instanceof String)) return;
                String name = ((String) p.args[0]).toLowerCase(Locale.ENGLISH);
                String v = overrides(pkg).get(name);
                if (v == null) return;
                Object stock = p.getResult();
                Class<?> rt = ((java.lang.reflect.Method) p.method).getReturnType();
                if (rt == boolean.class) p.setResult(Boolean.parseBoolean(v));
                else if (rt == int.class) p.setResult("true".equals(v) ? 1 : 0);
                else p.setResult(v);
                if (logged.add(name)) Lx.i("Extras: flag " + name + " stock=" + stock + " -> " + v);
            }
        };
        int n = 0;
        n += H.hookAll(f, "get", cb);
        n += H.hookAll(f, "getBoolean", cb);
        n += H.hookAll(f, "getInt", cb);
        Lx.i("Extras: feature-flag override on " + n + " Feature methods; active=" + overrides(pkg).keySet());
    }

    // ---- 2. app-specific helpers ---------------------------------------------------------------

    private static void installBattery(ClassLoader cl) {
        Class<?> fc = H.cls(cl, "com.zte.powersavemode.main.FeatureConfig");
        Class<?> ui = H.cls(cl, "com.zte.powersavemode.common.utils.UIUtils");
        if (fc == null) { Lx.w("Extras: powersavemode FeatureConfig not found"); return; }
        int n = 0;
        // Ultra power saving: the static-final gates are computed at class init, so also force the readers.
        if (H.constant(fc, "isRemoveSuperMode", boolean.class, H.types(), false, () -> Cfg.on(ExtrasFeatures.BAT_SUPER_MODE))) n++;
        if (H.constant(fc, "isUltraPowerSaveModeNotSupported", boolean.class, H.types(), false, () -> Cfg.on(ExtrasFeatures.BAT_SUPER_MODE))) n++;
        if (ui != null && H.constant(ui, "isSupportSuperMode", boolean.class, H.types(), true, () -> Cfg.on(ExtrasFeatures.BAT_SUPER_MODE))) n++;
        if (H.constant(fc, "isRemoveRefreshRate", boolean.class, H.types(), false, () -> Cfg.on(ExtrasFeatures.BAT_REFRESH_RATE))) n++;
        if (H.constant(fc, "isHideRefreshRate", boolean.class, H.types(), false, () -> Cfg.on(ExtrasFeatures.BAT_REFRESH_RATE))) n++;
        if (H.constant(fc, "isShowChargeSeparation", boolean.class, H.types(), true, () -> Cfg.on(ExtrasFeatures.BAT_CHARGE_SEPARATION))) n++;
        if (H.constant(fc, "isShowLongChargeNotifySwitch", boolean.class, H.types(), true, () -> Cfg.on(ExtrasFeatures.BAT_LONG_CHARGE))) n++;
        if (H.constant(fc, "isShowBatteryTime", boolean.class, H.types(), true, () -> Cfg.on(ExtrasFeatures.BAT_TIME))) n++;
        // Battery health page (global firmware has ZTE_FEATURE_POWER_BATTERY_HEALTH on): PowerSaveModeFragment removes the
        // "battery_health_settings" entry unless FeatureConfig.isSupportBatteryHealth(); the page (batteryHealth.BatteryHealthActivity)
        // is in the China build too.
        if (H.constant(fc, "isSupportBatteryHealth", boolean.class, H.types(), true, () -> Cfg.on(ExtrasFeatures.BAT_HEALTH))) n++;
        n += batteryHealthData(cl);
        n += separationTo100(cl);
        if (ui != null) {
            if (H.constant(ui, "isSupport5GSaveMode", boolean.class, H.types(), true, () -> Cfg.on(ExtrasFeatures.BAT_5G_SAVE))) n++;
            if (H.constant(ui, "isSupport5GSaveAppSetting", boolean.class, H.types(), true, () -> Cfg.on(ExtrasFeatures.BAT_5G_SAVE))) n++;
            if (H.constant(fc, "isSupport5G", boolean.class, H.types(), true, () -> Cfg.on(ExtrasFeatures.BAT_5G_SAVE))) n++;
        }
        Lx.i("Extras: battery hooks " + n);
    }

    /**
     * Charge separation's "Auto turn on" threshold (Settings.Global charge_separation_threshold, read by ZTE's
     * cn.zte.chargeseparation service): the slider (CustomSeekBarPreference "separation_charge_seek_bar", 20..90 from its
     * XML) gets 100 as its top, and PowerSaveModeApp.onCreate no longer puts a value above 90 back to 90.
     */
    private static int separationTo100(ClassLoader cl) {
        int n = 0;
        Class<?> bar = H.cls(cl, "com.zte.powersavemode.common.ui.CustomSeekBarPreference");
        if (bar != null) {
            XC_MethodHook top = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        if (!Cfg.on(ExtrasFeatures.BAT_SEPARATION_100)) return;
                        if (!"separation_charge_seek_bar".equals(XposedHelpers.callMethod(p.thisObject, "getKey"))) return;
                        if (XposedHelpers.getIntField(p.thisObject, "MAX") < 100) {
                            XposedHelpers.setIntField(p.thisObject, "MAX", 100);
                            Lx.i("Extras: charge separation slider now goes to 100 %");
                        }
                    } catch (Throwable t) { Lx.e("Extras: charge separation slider", t); }
                }
            };
            for (java.lang.reflect.Method m : H.findAll(bar, "onBindViewHolder")) if (m.getParameterTypes().length == 1 && H.hook(m, top)) n++;
        }
        Class<?> st = H.cls(cl, "com.zte.powersavemode.common.utils.SwitchTools");
        if (st != null && H.hook(H.find(st, "setSeparationThreshold", Context.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(ExtrasFeatures.BAT_SEPARATION_100) || !Integer.valueOf(90).equals(p.args[1])) return;
                for (StackTraceElement e : new Throwable().getStackTrace()) {
                    if (e.getClassName().endsWith("PowerSaveModeApp") && "onCreate".equals(e.getMethodName())) {
                        p.setResult(null);                              // the start-up "above 90 -> 90" reset
                        Lx.i("Extras: charge separation threshold above 90 % kept");
                        return;
                    }
                }
            }
        })) n++;
        if (n == 0) Lx.w("Extras: charge separation 100 % hooks not found");
        return n;
    }

    /**
     * Game Space's TGPA server (cn.nubia.gamepi): Tencent games ask it about frame interpolation ("insert frame", key 107)
     * and super resolution (108) through DisplayExtend.getSystemData(pkg, key, json action). Every question is logged.
     */
    private static void installGamePi(ClassLoader cl) {
        Class<?> de = H.cls(cl, "cn.nubia.gamepi.osapi.DisplayExtend");
        if (de == null) { Lx.w("Extras: GamePi DisplayExtend not found"); return; }
        int n = 0;
        if (H.hook(H.find(de, "getSystemData", String.class, int.class, String.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Lx.i("GamePi: " + p.args[0] + " asked key " + p.args[1] + " " + p.args[2] + " -> " + p.getResult());
            }
        })) n++;
        Lx.i("Extras: GamePi hooks " + n);
    }

    /**
     * ZTE's charge-separation service (cn.zte.chargeseparation, started when the charger is plugged in) turns separation on
     * when level >= charge_separation_threshold, but ChargeSeparationController.init() first puts a threshold above 90
     * back to 90 (updateThresholdForInit, "FOTA upgrade"). That reset is skipped, so 100 % stays 100 %.
     */
    private static void installChargeSeparation(ClassLoader cl) {
        Class<?> c = H.cls(cl, "cn.zte.chargeseparation.ChargeSeparationController");
        boolean ok = c != null && H.hook(H.find(c, "updateThresholdForInit"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(ExtrasFeatures.BAT_SEPARATION_100)) return;
                p.setResult(null);
                Lx.i("Extras: charge separation threshold above 90 % kept by the charging service");
            }
        });
        Lx.i("Extras: charge separation service hook " + (ok ? "on" : "missing"));
    }

    /**
     * The health page reads ZTE's vendor config (VendorConfigWrapper: "zte.battery.cycle_count" and "zte.battery.remain_life",
     * both x100). When a value is missing (-1) it falls back to the battery driver: the cycle count from the battery
     * broadcast (android.os.extra.CYCLE_COUNT) or /sys/class/power_supply/battery/cycle_count, and the remaining capacity
     * from charge_full / charge_full_design.
     */
    private static int batteryHealthData(ClassLoader cl) {
        Class<?> vc = H.cls(cl, "com.zte.powersavemode.provider.retrieve.VendorConfigWrapper");
        if (vc == null) { Lx.w("Extras: battery health data source (VendorConfigWrapper) not found"); return 0; }
        int n = 0;
        final boolean[] logged = new boolean[2];
        if (H.hook(vc, "getBatteryCycleCount", int.class, H.types(), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!Cfg.on(ExtrasFeatures.BAT_HEALTH) || !(p.getResult() instanceof Integer)) return;
                int v = (Integer) p.getResult();
                if (!logged[0]) { logged[0] = true; Lx.i("Extras: battery health: vendor cycle count " + v + " (x100)"); }
                if (v >= 0) return;
                int c = cycleCount();
                if (c >= 0) { p.setResult(c * 100); Lx.i("Extras: battery health: cycle count " + c + " from the battery driver"); }
            }
        })) n++;
        if (H.hook(vc, "getBatteryRemainLife", int.class, H.types(), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!Cfg.on(ExtrasFeatures.BAT_HEALTH) || !(p.getResult() instanceof Integer)) return;
                int v = (Integer) p.getResult();
                if (!logged[1]) { logged[1] = true; Lx.i("Extras: battery health: vendor remaining life " + v + " (x100)"); }
                if (v > 0) return;
                int pct = healthPercent();
                if (pct > 0) { p.setResult(pct * 100); Lx.i("Extras: battery health: " + pct + " % from charge_full / charge_full_design"); }
            }
        })) n++;
        return n;
    }

    private static final String PS = "/sys/class/power_supply/battery/";

    private static long sysfs(String name) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(PS + name))) {
            String l = r.readLine();
            return l == null ? -1 : Long.parseLong(l.trim());
        } catch (Throwable t) { return -1; }
    }

    private static int cycleCount() {
        try {
            Context c = android.app.AndroidAppHelper.currentApplication();
            if (c != null) {
                android.content.Intent i = c.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
                int v = i == null ? -1 : i.getIntExtra("android.os.extra.CYCLE_COUNT", -1);
                if (v >= 0) return v;
            }
        } catch (Throwable ignored) { }
        long v = sysfs("cycle_count");
        return v >= 0 && v < 100000 ? (int) v : -1;
    }

    private static int healthPercent() {
        long full = sysfs("charge_full"), design = sysfs("charge_full_design");
        if (full <= 0 || design <= 0) return -1;
        long pct = Math.round(full * 100.0 / design);
        return (int) Math.max(1, Math.min(100, pct));
    }

    private static void installPhoneManager(ClassLoader cl) {
        int n = 0;
        Class<?> nu = H.cls(cl, "com.zte.heartyservice.common.utils.NubiaUtil");
        Class<?> pf = H.cls(cl, "com.zte.heartyservice.privacy.PrivacyFacade");
        Class<?> port = H.cls(cl, "com.zte.heartyservice.common.porting.PortFunction");
        if (nu != null && H.constant(nu, "isNubiaAdsDisplay", boolean.class, H.types(), false, () -> Cfg.on(ExtrasFeatures.HS_NO_ADS))) n++;
        if (pf != null && H.constant(pf, "isEnableAd", boolean.class, H.types(), false, () -> Cfg.on(ExtrasFeatures.HS_NO_ADS))) n++;
        if (port != null && H.constant(port, "isSupportLandscape", boolean.class, H.types(), true, () -> Cfg.on(ExtrasFeatures.HS_LANDSCAPE))) n++;
        Lx.i("Extras: Phone Manager hooks " + n + (nu == null && pf == null ? " (classes not in this process)" : ""));
    }

    private static void installMagicVoice(ClassLoader cl) {
        int n = 0;
        Class<?> c = H.cls(cl, "cn.nubia.game.magicvoice.MagicVoiceConst");
        if (c != null) {
            // Keep the "empty package" answer (true) — only the fixed dialer/contacts list is lifted.
            if (H.hook(c, "isBlackListApp", boolean.class, H.types(String.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(ExtrasFeatures.MV_CALLS)) return;
                    String s = (String) p.args[0];
                    if (s != null && !s.isEmpty() && Boolean.TRUE.equals(p.getResult())) p.setResult(false);
                }
            })) n++;
        }
        Lx.i("Extras: Magic Voice hooks " + n);
    }

    private static void installCast(ClassLoader cl) {
        int n = 0;
        Class<?> st = H.cls(cl, "cn.nubia.touping.Utils.TouPingStatusUtils");
        if (st != null && H.constant(st, "isSupportHightFPS", boolean.class, H.types(Context.class), true, () -> Cfg.on(ExtrasFeatures.CAST_HIGH_FPS))) n++;
        java.util.function.BooleanSupplier all = () -> Cfg.on(ExtrasFeatures.CAST_ALL_FEATURES);
        String[][] targets = {
            {"cn.nubia.touping.Utils.DLNAUtils", "isSupportAudioSplitProjection", "isSupportHDMIForSprdAndMtk"},
            {"cn.nubia.touping.Utils.PhoneInfo", "isSupportAudioSplitProjection", "isSupportExpandProjectionScreen", "isSupportHDMIForSprdAndMtk"},
            {"cn.nubia.touping.Utils.FeatureUtils", "isSupportMainMachineMode"},
            {"cn.nubia.touping.AbstractActivity", "isSupportDevice"},
            {"com.hpplay.sdk.source.config.LelinkConfig", "isSupportDA", "isSupportIM", "isSupportMirror"},
            {"com.hpplay.sdk.source.utils.CastUtil", "isSupportCloudMultiCast"},
        };
        for (String[] t : targets) {
            Class<?> c = H.cls(cl, t[0]);
            if (c == null) continue;
            for (int i = 1; i < t.length; i++) if (H.constant(c, t[i], boolean.class, H.types(), true, all)) n++;
        }
        // Context-taking overloads
        Class<?> dl = H.cls(cl, "cn.nubia.touping.Utils.DLNAUtils");
        if (dl != null && H.constant(dl, "isSupportWiredScreenProjection", boolean.class, H.types(Context.class), true, all)) n++;
        Class<?> pi = H.cls(cl, "cn.nubia.touping.Utils.PhoneInfo");
        if (pi != null && H.constant(pi, "isSupportWiredScreenProjection", boolean.class, H.types(Context.class), true, all)) n++;
        Class<?> svc = H.cls(cl, "cn.nubia.touping.NubiaPCMirror.NubiaWirelessTouPingService");
        if (svc != null && H.constant(svc, "isEnableAudioSplit", boolean.class, H.types(Context.class), true, all)) n++;
        Lx.i("Extras: screen-cast hooks " + n);
    }

    private static void installNfc(ClassLoader cl) {
        Class<?> svc = H.cls(cl, "com.android.nfc.NfcService");
        if (svc == null) { Lx.w("Extras: NfcService not found"); return; }
        int n = 0;
        if (H.hook(H.find(svc, "playSound", int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(ExtrasFeatures.NFC_MUTE)) p.setResult(null); }
        })) n++;
        // sendMessage(16 = MSG_APPLY_SCREEN_STATE, Integer state): 1 off-unlocked, 2 off-locked, 4 on-locked, 8 on-unlocked
        if (H.hook(H.find(svc, "sendMessage", int.class, Object.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(ExtrasFeatures.NFC_SCREEN_OFF)) return;
                if (!Integer.valueOf(16).equals(p.args[0]) || !(p.args[1] instanceof Integer)) return;
                int st = (Integer) p.args[1];
                if (st == 1 || st == 2 || st == 4) { p.args[1] = 8; Lx.i("Extras: NFC screen state " + st + " -> 8 (on, unlocked)"); }
            }
        })) n++;
        Lx.i("Extras: NFC hooks " + n);
    }

    private static void installClone(ClassLoader cl) {
        int n = 0;
        Class<?> up = H.cls(cl, "com.zte.cn.doubleapp.common.UpdateUtils");
        if (up != null && H.hook(H.find(up, "getSupportApps"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!Cfg.on(ExtrasFeatures.CLONE_ANY)) return;
                try {
                    Context c = (Context) de.robv.android.xposed.XposedHelpers.getObjectField(p.thisObject, "mContext");
                    android.content.pm.PackageManager pm = c.getPackageManager();
                    java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
                    Object prev = p.getResult();
                    if (prev instanceof java.util.List) for (Object o : (java.util.List<?>) prev) all.add(String.valueOf(o));
                    android.content.Intent main = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER);
                    for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                        if (ri.activityInfo == null) continue;
                        String pkg = ri.activityInfo.packageName;
                        if (!"com.zte.cn.doubleapp".equals(pkg)) all.add(pkg);
                    }
                    p.setResult(new java.util.ArrayList<>(all));
                    Lx.i("Extras: clone list expanded to " + all.size() + " apps");
                } catch (Throwable t) { Lx.e("Extras: clone list", t); }
            }
        })) n++;
        Class<?> ut = H.cls(cl, "com.zte.cn.doubleapp.common.Utils");
        if (ut != null && H.constant(ut, "showLimitedApps", boolean.class, H.types(Context.class), false, () -> Cfg.on(ExtrasFeatures.CLONE_NO_LIMIT))) n++;
        Lx.i("Extras: app-cloning hooks " + n);
    }
}
