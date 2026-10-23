package com.hammad.redmagictweaks.xposed;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.FrameworkFeatures;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** `android` (system_server). Every target below was verified in the decompiled services.jar / framework.jar (fw 11.0.11). */
final class FrameworkHooks {
    private FrameworkHooks() {}

    private static final String WM = "com.android.server.wm.";
    private static Context sys;
    private static final Map<String, Boolean> userAppCache = new HashMap<>();
    private static final Set<String> NEVER = new HashSet<>();
    static { NEVER.add("android"); NEVER.add("com.android.systemui"); NEVER.add("com.zte.mifavor.launcher"); NEVER.add("org.lsposed.manager"); NEVER.add(com.hammad.redmagictweaks.BuildConfig.APPLICATION_ID); }

    static void install(ClassLoader cl) {
        systemContext(cl);
        freeform(cl);
        orientation(cl);
        phantom(cl);
        misc(cl);
        screenshots(cl);
        apex(cl);
        audio(cl);
        thermalAndPower(cl);
        refreshRate(cl);
        notifications(cl);
        games(cl);
        googleIntl(cl);
        try { GameViewHooks.install(cl); } catch (Throwable t) { Lx.e("Games: view hooks", t); }
        Lx.i("Framework: hooks ready");
    }

    // ------------------------------------------------------------ Google apps: international mode

    private static final String CN_GOOGLE = "cn.google.services";
    private static final Map<Integer, Boolean> googleUid = new java.util.concurrent.ConcurrentHashMap<>();

    /** The binder caller is one of Google's own apps (Play services, Services Framework, Play Store, com.google.android.*). */
    private static boolean googleCaller() {
        int uid = android.os.Binder.getCallingUid();
        if (uid < 10000) return false;
        Boolean b = googleUid.get(uid);
        if (b != null) return b;
        boolean g = false;
        try {
            String[] pkgs = sys == null ? null : sys.getPackageManager().getPackagesForUid(uid);
            if (pkgs != null) for (String p : pkgs) if (p.startsWith("com.google.android.") || p.equals("com.android.vending")) { g = true; break; }
        } catch (Throwable ignored) { return false; }
        googleUid.put(uid, g);
        return g;
    }

    /**
     * China firmware declares the system feature cn.google.services; Google Play services reads it as "China build" and
     * turns off Quick Share / Nearby Share and other services. Hidden from Google's apps only (hasSystemFeature and the
     * feature list); every other app sees the firmware as it is.
     */
    private static void googleIntl(ClassLoader cl) {
        int n = 0;
        Class<?> base = XposedHelpers.findClassIfExists("com.android.server.pm.IPackageManagerBase", cl);
        if (H.hook(H.find(base, "hasSystemFeature", String.class, int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!CN_GOOGLE.equals(p.args[0]) || !Boolean.TRUE.equals(p.getResult()) || !Cfg.on(FrameworkFeatures.GOOGLE_INTL)) return;
                if (!googleCaller()) return;
                p.setResult(false);
                if (loggedGames.add("cngoogle:" + android.os.Binder.getCallingUid())) Lx.i("Google: " + CN_GOOGLE + " hidden from uid " + android.os.Binder.getCallingUid());
            }
        })) n++;
        Class<?> impl = XposedHelpers.findClassIfExists("com.android.server.pm.PackageManagerService$IPackageManagerImpl", cl);
        if (H.hook(H.find(impl, "getSystemAvailableFeatures"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (!Cfg.on(FrameworkFeatures.GOOGLE_INTL) || p.getResult() == null || !googleCaller()) return;
                    java.util.List<?> l = (java.util.List<?>) XposedHelpers.callMethod(p.getResult(), "getList");
                    java.util.List<android.content.pm.FeatureInfo> out = new java.util.ArrayList<>();
                    boolean drop = false;
                    for (Object o : l) {
                        if (o instanceof android.content.pm.FeatureInfo && CN_GOOGLE.equals(((android.content.pm.FeatureInfo) o).name)) { drop = true; continue; }
                        out.add((android.content.pm.FeatureInfo) o);
                    }
                    if (drop) p.setResult(XposedHelpers.newInstance(p.getResult().getClass(), out));
                } catch (Throwable t) { Lx.e("Google: feature list", t); }
            }
        })) n++;
        Lx.i("Google: international mode hooks " + n);
    }

    // ------------------------------------------------------------ helpers

    private static void systemContext(ClassLoader cl) {
        try {
            Class<?> at = XposedHelpers.findClass("android.app.ActivityThread", cl);
            Object thread = XposedHelpers.callStaticMethod(at, "currentActivityThread");
            if (thread != null) sys = (Context) XposedHelpers.callMethod(thread, "getSystemContext");
        } catch (Throwable ignored) { }
        // the log provider needs a context; system_server has no Application, so pick it up once services start
        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass("com.android.server.SystemServer", cl), "startOtherServices", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (sys == null) sys = (Context) XposedHelpers.getObjectField(p.thisObject, "mSystemContext");
                        Lx.attach(sys);
                        try { VolumeBoostHooks.start(sys); } catch (Throwable t) { Lx.e("VolumeBoost start", t); }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { Lx.w("framework: SystemServer hook " + t); }
    }

    /** scope index: 0 off, 1 user apps, 2 all apps */
    private static boolean freeformAllowed(String pkg) {
        int scope = Cfg.choice(FrameworkFeatures.FREEFORM);
        if (scope == 0 || pkg == null || NEVER.contains(pkg)) return false;
        return scope == 2 || isUserApp(pkg);
    }

    private static boolean isUserApp(String pkg) {
        if (pkg == null) return false;
        Boolean c = userAppCache.get(pkg);
        if (c != null) return c;
        boolean r = false;
        try {
            if (sys != null) {
                ApplicationInfo ai = sys.getPackageManager().getApplicationInfo(pkg, 0);
                r = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0;
            }
        } catch (PackageManager.NameNotFoundException e) { r = false; }
        catch (Throwable t) { return false; }
        if (userAppCache.size() > 500) userAppCache.clear();
        userAppCache.put(pkg, r);
        return r;
    }

    private static boolean isUserApp(ApplicationInfo ai) {
        return ai != null && (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0 && !NEVER.contains(ai.packageName);
    }

    /** package of an ActivityRecord / Task / TaskFragment host object, or null */
    private static String pkgOf(Object wmObj) {
        if (wmObj == null) return null;
        try {
            Object info = XposedHelpers.getObjectField(wmObj, "info");           // ActivityRecord.info (ActivityInfo)
            if (info != null) return ((android.content.pm.ActivityInfo) info).packageName;
        } catch (Throwable ignored) { }
        try {
            Object ra = XposedHelpers.getObjectField(wmObj, "realActivity");    // Task.realActivity
            if (ra instanceof ComponentName) return ((ComponentName) ra).getPackageName();
        } catch (Throwable ignored) { }
        try {
            Object top = XposedHelpers.callMethod(wmObj, "getTopNonFinishingActivity");
            if (top != null) return pkgOf(top);
        } catch (Throwable ignored) { }
        return null;
    }

    /** ActivityInfo.isFixedOrientation is hidden API: landscape/portrait family incl. sensor/reverse/user variants. */
    private static boolean isFixedOrientation(int o) {
        return o == 0 || o == 1 || o == 5 || o == 6 || o == 7 || o == 8 || o == 9 || o == 11 || o == 12 || o == 14;
    }

    private static boolean standardType(Object wmObj) {
        try { return Boolean.TRUE.equals(XposedHelpers.callMethod(wmObj, "isActivityTypeStandard")); } catch (Throwable t) { return true; }
    }

    private static XC_MethodHook allowForPkgArg(final int argIdx) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Object a = p.args[argIdx];
                String pkg = a instanceof ComponentName ? ((ComponentName) a).getPackageName() : a instanceof String ? (String) a : null;
                if (freeformAllowed(pkg)) p.setResult(true);
            }
        };
    }

    private static XC_MethodHook allowForThis() {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (Cfg.choice(FrameworkFeatures.FREEFORM) == 0) return;
                if (standardType(p.thisObject) && freeformAllowed(pkgOf(p.thisObject))) p.setResult(true);
            }
        };
    }

    // ------------------------------------------------------------ 1. freeform

    private static void freeform(ClassLoader cl) {
        Class<?> wru = H.cls(cl, "android.app.WindowReplyUtils");
        if (wru != null) H.hook(H.find(wru, "isForceSupportWhiteListForWR", String.class), allowForPkgArg(0));
        Class<?> atms = H.cls(cl, WM + "ActivityTaskManagerService");
        if (atms != null) {
            H.hook(H.find(atms, "isReachWrMaxSizeForMulti"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(FrameworkFeatures.FREEFORM_NOLIMIT)) p.setResult(false); }
            });
        }
        Class<?> tdam = H.cls(cl, WM + "TaskDisplayAreaMifavor");
        if (tdam != null) {
            H.hookAll(tdam, "alertMessageForReachMultiWrMaxSizeWr", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(FrameworkFeatures.FREEFORM_NOLIMIT)) p.setResult(null); }
            });
            H.hookAll(tdam, "checkTaskSupportWr", new XC_MethodHook() {          // (Context, Task)
                @Override protected void beforeHookedMethod(MethodHookParam p) { if (p.args.length == 2 && freeformAllowed(pkgOf(p.args[1]))) p.setResult(true); }
            });
            H.hookAll(tdam, "checkTaskSupportForCompWr", allowForPkgArg(1));    // (Context, ComponentName)
            H.hookAll(tdam, "isSupportWindowReplyState", new XC_MethodHook() {   // (ActivityRecord|Task, Context) -> int | TaskNotSupportWindowReplyStateWr
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!freeformAllowed(pkgOf(p.args[0]))) return;
                    Object r = p.getResult();
                    if (r instanceof Integer) { if ((Integer) r != 0) p.setResult(0); }
                    else if (r != null) p.setResult(null);
                }
            });
            H.hookAll(tdam, "isSupportWindowReplyStateForTask", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.getResult() instanceof Integer && (Integer) p.getResult() != 0 && freeformAllowed(pkgOf(p.args[0]))) p.setResult(0);
                }
            });
            H.hookAll(tdam, "isResizeable", new XC_MethodHook() {                // (ActivityRecord)
                @Override protected void beforeHookedMethod(MethodHookParam p) { if (p.args.length == 1 && standardType(p.args[0]) && freeformAllowed(pkgOf(p.args[0]))) p.setResult(true); }
            });
            H.hookAll(tdam, "isSupportResize", allowForPkgArg(1));              // (Context, ComponentName)
        }
        Class<?> acc = H.cls(cl, WM + "ActivityClientController");
        if (acc != null) {
            H.hookAll(acc, "checkTaskSupportForCompWr", allowForPkgArg(0));
            H.hookAll(acc, "checkTaskSupportForPkgWr", allowForPkgArg(0));
        }
        Class<?> ar = H.cls(cl, WM + "ActivityRecord");
        if (ar != null) {
            H.hookAll(ar, "supportsMultiWindow", allowForThis());
            H.hookAll(ar, "supportsMultiWindowInDisplayArea", allowForThis());
            H.hookAll(ar, "supportsFreeformInDisplayArea", allowForThis());
        }
        Class<?> task = H.cls(cl, WM + "Task");
        if (task != null) H.hookAll(task, "supportsFreeformInDisplayArea", allowForThis());
        Class<?> tf = H.cls(cl, WM + "TaskFragment");
        if (tf != null) for (String m : new String[]{"supportsMultiWindow", "supportsMultiWindowCheckForOtherMultifromWr", "supportsMultiWindowInDisplayArea", "supportsMultiWindowInDisplayAreaCheckForOtherMultifromWr"})
            H.hookAll(tf, m, allowForThis());
    }

    // ------------------------------------------------------------ 2. orientation

    private static void orientation(ClassLoader cl) {
        Class<?> ar = H.cls(cl, WM + "ActivityRecord");
        if (ar == null) return;
        H.hook(H.find(ar, "isRestrictedFixedOrientation", int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(FrameworkFeatures.ORIENTATION)) return;
                int o = (Integer) p.args[0];
                if (o == 14 /* LOCKED */ || !isFixedOrientation(o)) return;
                try {
                    Object info = XposedHelpers.getObjectField(p.thisObject, "info");
                    ApplicationInfo ai = info == null ? null : ((android.content.pm.ActivityInfo) info).applicationInfo;
                    if (isUserApp(ai)) p.setResult(true);
                } catch (Throwable ignored) { }
            }
        });
    }

    // ------------------------------------------------------------ 3. phantom processes

    private static void phantom(ClassLoader cl) {
        Class<?> ppl = H.cls(cl, "com.android.server.am.PhantomProcessList");
        if (ppl != null) H.hookAll(ppl, "trimPhantomProcessesIfNecessary", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(FrameworkFeatures.PHANTOM)) p.setResult(null); }
        });
        Class<?> amc = H.cls(cl, "com.android.server.am.ActivityManagerConstants");
        if (amc != null) H.hookAll(amc, "updateMaxPhantomProcesses", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (Cfg.on(FrameworkFeatures.PHANTOM)) try { XposedHelpers.setIntField(p.thisObject, "MAX_PHANTOM_PROCESSES", Integer.MAX_VALUE); } catch (Throwable ignored) { }
            }
        });
    }

    // ------------------------------------------------------------ 4/8/9/12/13 misc

    private static void misc(ClassLoader cl) {
        Class<?> awn = H.cls(cl, WM + "AlertWindowNotification");
        if (awn != null) {
            XC_MethodHook skip = new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(FrameworkFeatures.NO_ALERT_NOTIF)) p.setResult(null); } };
            H.hookAll(awn, "post", skip);
            H.hookAll(awn, "onPostNotification", skip);
        }
        Class<?> lssa = H.cls(cl, "com.android.server.locksettings.LockSettingsStrongAuth");
        if (lssa != null) H.hookAll(lssa, "rescheduleStrongAuthTimeoutAlarm", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(FrameworkFeatures.NO_72H)) p.setResult(null); }
        });
        for (String c : new String[]{"com.android.server.datacollection.ZteTrackManager", "com.android.server.redmagic.trackclient.ZteTrackManager"}) {
            Class<?> t = XposedHelpers.findClassIfExists(c, cl);
            if (t != null) H.hookAll(t, "init", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(FrameworkFeatures.NO_TELEMETRY)) { Lx.i("framework: telemetry init blocked"); p.setResult(null); } }
            });
        }
        Class<?> ws = H.cls(cl, WM + "WindowState");
        if (ws != null) H.hook(H.find(ws, "getTouchOcclusionMode"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(FrameworkFeatures.UNTRUSTED_TOUCH)) p.setResult(2); }
        });
        Class<?> pwm = H.cls(cl, "com.android.server.policy.PhoneWindowManager");
        if (pwm != null) H.hook(H.find(pwm, "powerLongPress", long.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(FrameworkFeatures.POWER_ASSIST)) return;
                try {
                    XposedHelpers.setBooleanField(p.thisObject, "mPowerKeyHandled", true);
                    try { XposedHelpers.callMethod(p.thisObject, "performHapticFeedback", 10003, "Power - Long Press - Assist"); } catch (Throwable ignored) { }
                    XposedHelpers.callMethod(p.thisObject, "launchAssistAction", null, -2, p.args[0], 6);
                    p.setResult(null);
                } catch (Throwable t) { Lx.w("framework: assist on long-press failed: " + t); }
            }
        });
    }

    // ------------------------------------------------------------ 5. screenshots in secure apps

    private static void screenshots(ClassLoader cl) {
        final java.util.function.BooleanSupplier on = () -> Cfg.on(FrameworkFeatures.SECURE_SHOT);
        Class<?> wms = H.cls(cl, WM + "WindowManagerService");
        if (wms != null) {
            H.hookAll(wms, "getDisableSecureWindows", new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (on.getAsBoolean()) p.setResult(true); } });
            H.hookAll(wms, "registerScreenRecordingCallback", new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (on.getAsBoolean()) p.setResult(false); } });
        }
        Class<?> ws = H.cls(cl, WM + "WindowState");
        if (ws != null) H.hook(H.find(ws, "isSecureLocked"), new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (on.getAsBoolean()) p.setResult(false); } });
        Class<?> atms = H.cls(cl, WM + "ActivityTaskManagerService");
        if (atms != null) H.hookAll(atms, "registerScreenCaptureObserver", new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (on.getAsBoolean()) p.setResult(null); } });
        Class<?> dc = XposedHelpers.findClassIfExists("com.android.server.display.DisplayControl", cl);
        if (dc != null) H.hookAll(dc, "createVirtualDisplay", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (on.getAsBoolean() && p.args.length >= 2 && p.args[1] instanceof Boolean) p.args[1] = true; }
        });
        Class<?> vda = XposedHelpers.findClassIfExists("com.android.server.display.VirtualDisplayAdapter", cl);
        if (vda != null) H.hookAll(vda, "createVirtualDisplayLocked", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!on.getAsBoolean() || p.args.length < 7) return;
                try {
                    int ownerUid = (Integer) p.args[2];
                    if (ownerUid >= 10000 && p.args[1] == null) return;
                    if (p.args[6] instanceof Integer) p.args[6] = ((Integer) p.args[6]) | 4;
                } catch (Throwable ignored) { }
            }
        });
        Class<?> shb = XposedHelpers.findClassIfExists("android.window.ScreenCapture$ScreenshotHardwareBuffer", cl);
        if (shb != null) H.hookAll(shb, "containsSecureLayers", new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (on.getAsBoolean()) p.setResult(false); } });
        Class<?> sc = XposedHelpers.findClassIfExists("android.window.ScreenCapture", cl);
        if (sc != null) {
            XC_MethodHook secureLayers = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!on.getAsBoolean() || p.args.length == 0 || p.args[0] == null) return;
                    try {
                        java.lang.reflect.Field f = XposedHelpers.findFieldIfExists(p.args[0].getClass(), "mCaptureSecureLayers");
                        if (f == null) f = XposedHelpers.findFieldIfExists(p.args[0].getClass().getSuperclass(), "mCaptureSecureLayers");
                        if (f != null) { f.setAccessible(true); f.setBoolean(p.args[0], true); }
                    } catch (Throwable ignored) { }
                }
            };
            H.hookAll(sc, "nativeCaptureDisplay", secureLayers);
            H.hookAll(sc, "nativeCaptureLayers", secureLayers);
        }
    }

    // ------------------------------------------------------------ 6. APEX services (wifi / bluetooth)

    private static void apex(ClassLoader cl) {
        Class<?> ssm = H.cls(cl, "com.android.server.SystemServiceManager");
        if (ssm == null) return;
        H.hook(H.find(ssm, "loadClassFromLoader", String.class, ClassLoader.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                String name = String.valueOf(p.args[0]);
                ClassLoader loader = (ClassLoader) p.args[1];
                try {
                    if ("com.android.server.wifi.WifiService".equals(name)) {
                        Class<?> store = loader.loadClass("com.android.server.wifi.WifiSettingsStore");
                        int n = H.hookAll(store, "isAirplaneSensitive", new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam q) { if (Cfg.on(FrameworkFeatures.AIRPLANE_WIFI)) q.setResult(false); }
                        });
                        Lx.i("framework: wifi apex hooked (" + n + ")");
                    } else if ("com.android.server.bluetooth.BluetoothService".equals(name)) {
                        Class<?> bms = loader.loadClass("com.android.server.bluetooth.BluetoothManagerService");
                        int n = H.hookAll(bms, "onAirplaneModeChanged", new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam q) {
                                if (Cfg.on(FrameworkFeatures.AIRPLANE_BT) && q.args.length >= 1 && Boolean.TRUE.equals(q.args[0])) q.setResult(null);
                            }
                        });
                        Lx.i("framework: bluetooth apex hooked (" + n + ")");
                    }
                } catch (Throwable t) { Lx.w("framework: apex hook for " + name + " failed: " + t); }
            }
        });
    }

    // ------------------------------------------------------------ 7. audio

    private static void audio(ClassLoader cl) {
        Class<?> sdh = XposedHelpers.findClassIfExists("com.android.server.audio.SoundDoseHelper", cl);
        Class<?> as = H.cls(cl, "com.android.server.audio.AudioService");
        XC_MethodHook noWarn = new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(FrameworkFeatures.NO_SAFE_VOLUME)) p.setResult(false); } };
        Class<?> target = sdh != null ? sdh : as;
        if (target != null) for (String m : new String[]{"checkSafeMediaVolume", "raiseVolumeDisplaySafeMediaVolume", "willDisplayWarningAfterCheckVolume"}) H.hookAll(target, m, noWarn);
        if (as != null) H.hookAll(as, "volumeAdjustmentAllowedByDnd", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (Cfg.on(FrameworkFeatures.DND_VOLUME)) p.setResult(true); }
        });
    }

    // ------------------------------------------------------------ 10/11 thermal + power

    // ------------------------------------------------------------ refresh rate (ZTE ScreenRefreshRateController)

    private static final java.util.Set<String> VIDEO_APPS = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.google.android.youtube", "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus",
            "org.videolan.vlc", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro", "com.zte.videoplayer", "cn.nubia.videoplayer",
            "com.google.android.apps.youtube.music", "tv.twitch.android.app", "com.hulu.plus", "com.plexapp.android"));
    private static volatile String topPkg = "";

    /** Target rate for the current state, or -1 when the override is off / the rate is not supported. */
    private static int forcedFps(Class<?> ctl) {
        int idx = Cfg.choice(FrameworkFeatures.FORCE_RATE);
        if (idx <= 0 || idx >= FrameworkFeatures.FORCE_RATE_VALUES.length) return -1;
        if (Cfg.on(FrameworkFeatures.FORCE_RATE_VIDEO_60) && VIDEO_APPS.contains(topPkg)) return 60;
        int want = FrameworkFeatures.FORCE_RATE_VALUES[idx];
        try {
            int highest = XposedHelpers.getStaticIntField(ctl, "sHighestFps");
            if (want < 0) return highest > 0 ? highest : -1;
            if (want > highest && highest > 0) return highest;
            if (want == 165 && !XposedHelpers.getStaticBooleanField(ctl, "isSupport_165Hz")) return highest;
            if (want == 144 && !XposedHelpers.getStaticBooleanField(ctl, "isSupport_144Hz")) return highest;
            if (want == 90 && !XposedHelpers.getStaticBooleanField(ctl, "isSupport_90Hz")) return -1;
        } catch (Throwable ignored) { }
        return want;
    }

    /**
     * Every refresh-rate change on this firmware funnels through
     * ScreenRefreshRateController.setDesiredDisplayConfig(int fps, String who) (setDesiredConfig only posts to it).
     * While the screen is on we replace the requested rate; the screen-off / AOD requests pass through so the
     * panel still drops when idle. getTargetFps is forced too so the vote box agrees and nothing re-votes lower.
     */
    private static void refreshRate(ClassLoader cl) {
        final Class<?> ctl = XposedHelpers.findClassIfExists("com.android.server.am.ScreenRefreshRateController", cl);
        if (ctl == null) { Lx.w("framework: ScreenRefreshRateController missing"); return; }
        final java.util.concurrent.atomic.AtomicInteger lastLogged = new java.util.concurrent.atomic.AtomicInteger(-2);
        int n = 0;
        if (H.hook(H.find(ctl, "setDesiredDisplayConfig", int.class, String.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                String who = String.valueOf(p.args[1]);
                if (who.startsWith("screen-off")) return;
                // frame interpolation sets its own panel rate (getInterpFps). With a target picked, nothing runs the panel
                // slower while it is on: a touch, TGPA or Game Assist request could otherwise drop it back to 120 Hz
                if ("Enable-interp".equals(who) || interpActive(p.thisObject, topPkg)) {
                    try {
                        int want = interpTarget(ctl);
                        int asked = (Integer) p.args[0];
                        if (want > 0 && asked < want && interpActive(p.thisObject, topPkg)) {
                            p.args[0] = want;
                            if (lastLogged.getAndSet(20000 + want) != 20000 + want)
                                Lx.i("RefreshRate: frame interpolation keeps the panel at " + want + " Hz (asked " + asked + " by " + who + ")");
                        }
                    } catch (Throwable t) { Lx.e("RefreshRate: interpolation floor", t); }
                    return;
                }
                int f = forcedFps(ctl);
                if (f <= 0) return;
                int asked = (Integer) p.args[0];
                if (asked != f) {
                    p.args[0] = f;
                    if (lastLogged.getAndSet(f) != f) Lx.i("RefreshRate: forced " + f + " Hz (asked " + asked + " by " + who + ")");
                }
            }
        })) n++;
        if (H.hook(H.find(ctl, "getTargetFps", String.class, String.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (interpActive(p.thisObject, (String) p.args[0])) return;   // the interpolation rate stays
                int f = forcedFps(ctl);
                if (f > 0) p.setResult(f);
            }
        })) n++;
        // Game Space frame interpolation (GFRC): the panel rate while it is on. Stock: Super 120 Hz, Ultra sHighestFps.
        if (H.hook(H.find(ctl, "getInterpFps", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (XposedHelpers.getIntField(p.thisObject, "mInterp") == 0) return;
                    int want = interpTarget(ctl);
                    if (want <= 0) return;
                    Object was = p.getResult();
                    if (was instanceof Integer && (Integer) was == want) return;
                    p.setResult(want);
                    if (lastLogged.getAndSet(10000 + want) != 10000 + want) Lx.i("RefreshRate: frame interpolation runs the panel at " + want + " Hz (stock " + was + ")");
                } catch (Throwable t) { Lx.e("RefreshRate: interpolation rate", t); }
            }
        })) n++;
        else Lx.w("framework: ScreenRefreshRateController#getInterpFps not found, interpolation rate stays stock");
        // cubeserver -> ScreenRefreshRateInternal.notePackageInterpState -> here: interpolation on (1 Super, 2 Ultra) / off (0)
        if (H.hook(H.find(ctl, "updatePackageInterpState", String.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                final String pkg = (String) p.args[0];
                final int level = (Integer) p.args[1];
                Lx.i("Games: frame interpolation " + (level == 0 ? "off" : "on, " + (level == 1 ? "Super" : level == 2 ? "Ultra" : "level " + level)) + " for " + pkg);
                if (level != 0) interpCheck(ctl, p.thisObject, pkg, level);
            }
        })) n++;
        // remember the foreground package for the video exception
        if (H.hook(H.find(ctl, "onAppForegroundEvent", String.class, String.class, int.class, int.class, long.class, String.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) { if (p.args[0] != null) topPkg = (String) p.args[0]; }
        })) n++;
        // Auto (mode 0) ceiling: GlobalSettingFpsController.convertModeToValue(0) picks 120 (or 90/60) as the
        // smart-mode high step; raise it to sHighestFps so the stock 60<->high switching uses 165.
        final Class<?> gsf = XposedHelpers.findClassIfExists("com.android.server.am.GlobalSettingFpsController", cl);
        if (gsf != null) {
            XC_MethodHook raise = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        int mode = p.args.length > 0 && p.args[0] instanceof Integer ? (Integer) p.args[0] : XposedHelpers.getStaticIntField(gsf, "sRefreshRateMode");
                        if (mode == 5 && (!com.hammad.redmagictweaks.FlavorBits.publicBuild() || android.os.Build.VERSION.SDK_INT >= 36)) {
                            // the 165 Hz choice (refresh_rate_mode 5): firmware without that mode (RedMagic OS 10, whose
                            // Settings gets the choice from SettingsHooks) runs it at the panel's highest rate
                            int hi = XposedHelpers.getStaticIntField(ctl, "sHighestFps");
                            if (hi > 144 && XposedHelpers.getStaticIntField(gsf, "sSettingFps") != hi) {
                                XposedHelpers.setStaticIntField(gsf, "sSettingFps", hi);
                                if (p.method.getName().equals("getSettingFps")) p.setResult(hi);
                                Lx.i("RefreshRate: fixed mode 5 -> " + hi + " Hz");
                            }
                            return;
                        }
                        if (!Cfg.on(FrameworkFeatures.AUTO_RATE_MAX)) return;
                        if (mode != 0) return;                                   // only the Auto mode
                        if (XposedHelpers.getStaticIntField(gsf, "sLockRefreshRateState") == 1) return;
                        int highest = XposedHelpers.getStaticIntField(ctl, "sHighestFps");
                        int cur = XposedHelpers.getStaticIntField(gsf, "sSettingFps");
                        if (highest > cur) {
                            XposedHelpers.setStaticIntField(gsf, "sSettingFps", highest);
                            if (p.method.getName().equals("getSettingFps")) p.setResult(highest);
                            if (lastLogged.getAndSet(-highest) != -highest) Lx.i("RefreshRate: auto mode high step " + cur + " -> " + highest + " Hz");
                        }
                    } catch (Throwable t) { Lx.e("RefreshRate: auto ceiling", t); }
                }
            };
            if (H.hook(H.find(gsf, "convertModeToValue", int.class), raise)) n++;
            if (H.hook(H.find(gsf, "getSettingFps"), raise)) n++;
        } else Lx.w("framework: GlobalSettingFpsController missing");
        Lx.i("framework: refresh-rate hooks " + n);
    }

    /** The panel rate picked for frame interpolation (GAME_GFRC_TARGET), or -1 = stock. */
    private static int interpTarget(Class<?> ctl) {
        int idx = Cfg.choice(Features.GAME_GFRC_TARGET);
        if (idx <= 0 || idx >= Features.GFRC_TARGET_HZ.length) return -1;
        int hi = XposedHelpers.getStaticIntField(ctl, "sHighestFps");
        int want = Features.GFRC_TARGET_HZ[idx] < 0 ? hi : Features.GFRC_TARGET_HZ[idx];
        if (want == 90 && !staticBool(ctl, "isRealSupport_90Hz")) return -1;          // no real 90 Hz mode: stock choice
        if (want == 144 && !staticBool(ctl, "isSupport_144Hz")) want = hi;
        if (hi > 0) want = Math.min(want, hi);
        return Math.max(60, want);
    }

    /**
     * 4 s after interpolation turns on: what the interpolation and the panel really run at (the vendor.gpp properties
     * cubeserver set, the display's rate, Qualcomm's list entry for the game), so a log shows where a frame cap comes from.
     */
    private static void interpCheck(final Class<?> ctl, final Object ctlObj, final String pkg, final int level) {
        Thread t = new Thread(() -> {
            try { Thread.sleep(4000); } catch (InterruptedException e) { return; }
            try {
                StringBuilder sb = new StringBuilder("Games: interpolation check for ").append(pkg).append(" (")
                        .append(level == 1 ? "Super" : level == 2 ? "Ultra" : "level " + level).append("): ");
                for (String k : new String[]{"vendor.gpp.frc.enable", GFRC_INTERP_PROP, "vendor.gpp.gfrc.upscale.ratio", GFRC_ALL_PROP})
                    sb.append(k.substring("vendor.gpp.".length())).append('=').append(sysProp(k)).append(", ");
                try {
                    Object dmg = XposedHelpers.callStaticMethod(Class.forName("android.hardware.display.DisplayManagerGlobal"), "getInstance");
                    Object d = XposedHelpers.callMethod(dmg, "getRealDisplay", 0);
                    Object mode = XposedHelpers.callMethod(d, "getMode");
                    sb.append("panel ").append(Math.round((Float) XposedHelpers.callMethod(mode, "getRefreshRate"))).append(" Hz, render ")
                            .append(Math.round((Float) XposedHelpers.callMethod(d, "getRefreshRate"))).append(" fps max, ");
                } catch (Throwable e) { sb.append("panel ? (").append(e.getClass().getSimpleName()).append("), "); }
                try {
                    sb.append("interp panel ").append(XposedHelpers.callMethod(ctlObj, "getInterpFps", 0)).append(" Hz, top ")
                            .append(XposedHelpers.getStaticIntField(ctl, "sHighestFps")).append(" Hz, ");
                } catch (Throwable ignored) { }
                String listed = "not on Qualcomm's list";
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader("/system/etc/gpp_app_list"))) {
                    for (String line; (line = r.readLine()) != null; ) if (line.trim().startsWith(pkg + ",")) { listed = "Qualcomm's list: " + line.trim(); break; }
                } catch (Throwable e) { listed = "Qualcomm's list unreadable"; }
                sb.append(listed);
                Lx.i(sb.toString());
            } catch (Throwable e) { Lx.e("Games: interpolation check", e); }
        }, "rmtweaks-interp-check");
        t.setDaemon(true);
        t.start();
    }

    /** Frame interpolation is on for pkg (or for any package when pkg is null): ScreenRefreshRateController.mInterp / mInterpPackage. */
    private static boolean interpActive(Object ctl, String pkg) {
        try {
            if (XposedHelpers.getIntField(ctl, "mInterp") == 0) return false;
            Object ip = XposedHelpers.getObjectField(ctl, "mInterpPackage");
            return ip != null && (pkg == null || pkg.isEmpty() || ip.equals(pkg));
        } catch (Throwable t) { return false; }
    }

    private static boolean staticBool(Class<?> c, String field) {
        try { return XposedHelpers.getStaticBooleanField(c, field); } catch (Throwable t) { return true; }
    }

    // ------------------------------------------------------------ games: frame interpolation, full screen, launch boost

    /** Qualcomm GPP: with this vendor property at 1, cubeserver's GfrcModeCtrl gives every package the full GFRC capability (22). */
    private static final String GFRC_ALL_PROP = "vendor.gpp.allgame.enable";
    private static final String GFRC_INTERP_PROP = "vendor.gpp.gfrc.interp.rate";
    private static final Map<String, String> gppLogged = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean gfrcPropSetByUs, gfrcPropFailLogged;
    private static volatile Object gameAssistSvc;
    private static volatile Method gameListCheck;
    private static final Set<String> loggedGames = java.util.Collections.synchronizedSet(new HashSet<>());

    /** pkg is a game added to Game Space (ZTE's IGameAssistSerivce local service in system_server, the check GameRatioStub uses). */
    static boolean isGameSpaceGame(ClassLoader cl, String pkg) {
        if (pkg == null) return false;
        try {
            if (gameAssistSvc == null) {
                Class<?> ls = XposedHelpers.findClassIfExists("com.android.server.LocalServices", cl);
                Class<?> igs = XposedHelpers.findClassIfExists("com.android.server.common.IGameAssistSerivce", cl);
                if (ls == null || igs == null) return false;
                Object svc = XposedHelpers.callStaticMethod(ls, "getService", igs);
                if (svc == null) return false;
                gameListCheck = igs.getMethod("isGameSpaceListApp", String.class);
                gameAssistSvc = svc;
            }
            return Boolean.TRUE.equals(gameListCheck.invoke(gameAssistSvc, pkg));
        } catch (Throwable t) { return false; }
    }

    private static String sysProp(String key) {
        try { return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class).invoke(null, key, ""); }
        catch (Throwable t) { return ""; }
    }

    /** vendor.gpp.allgame.enable follows the switch (the property is not persistent: set again after every boot). */
    private static void syncGfrcProp() {
        boolean on = Cfg.on(Features.GAME_GFRC_ALL);
        String cur = sysProp(GFRC_ALL_PROP);
        String want = on ? "1" : gfrcPropSetByUs && "1".equals(cur) ? "0" : null;
        if (want == null || want.equals(cur)) return;
        try {
            Class.forName("android.os.SystemProperties").getMethod("set", String.class, String.class).invoke(null, GFRC_ALL_PROP, want);
            gfrcPropSetByUs = on;
            Lx.i("Games: " + GFRC_ALL_PROP + "=" + want + (on ? " (frame interpolation for every Game Space game)" : ""));
        } catch (Throwable t) {
            Throwable c = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
            if (!gfrcPropFailLogged) {
                gfrcPropFailLogged = true;
                Lx.w("Games: setting " + GFRC_ALL_PROP + " failed (" + c + "); the Game Space capability hook still unlocks the choices");
            }
        }
    }

    /**
     * Every vendor.gpp.* property system_server sets (cubeserver's GfrcModeCtrl, MindSync, us) is logged when it changes,
     * so a log shows the interpolation level (1 Super, 2 Ultra) Qualcomm's gppservice was given. This phone runs the VPP
     * session (libgppvppgfrcplussession), which in logs added one generated frame per game frame at both levels.
     */
    private static void gppProps() {
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length < 2 || !(p.args[0] instanceof String)) return;
                String key = (String) p.args[0];
                if (!key.startsWith("vendor.gpp.")) return;
                try {
                    String val = String.valueOf(p.args[1]), note = "";
                    if (GFRC_INTERP_PROP.equals(key)) note = "1".equals(val) ? " (Super)" : "2".equals(val) ? " (Ultra)" : "";
                    if (!val.equals(gppLogged.put(key, val))) Lx.i("Games: " + key + "=" + val + note);
                } catch (Throwable t) { Lx.e("Games: vendor.gpp property", t); }
            }
        };
        boolean ok = H.hook(H.find(XposedHelpers.findClassIfExists("android.os.SystemProperties", null), "set", String.class, String.class), h);
        Lx.i("Games: frame generation property hook " + (ok ? "on" : "missing"));
    }

    /**
     * MindSync's game-mode cleanup (ManualBgCleanupPolicy.startBgAppsCleanupFromGameMode, reason bgc_gamemode) force-stops
     * background apps when a game starts, RM Tweaks included, and the log provider goes with it: nothing from a game
     * session reached the log. RM Tweaks is taken off the list (filterProtectedAppForGM, after).
     */
    private static void keepAliveInGames(ClassLoader cl) {
        Class<?> c = XposedHelpers.findClassIfExists("com.android.server.am.ManualBgCleanupPolicy", cl);
        final String me = com.hammad.redmagictweaks.BuildConfig.APPLICATION_ID;
        int n = 0;
        for (Method m : H.findAll(c, "filterProtectedAppForGM")) {
            if (H.hook(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0 && p.args[0] instanceof java.util.List && ((java.util.List<?>) p.args[0]).remove(me))
                        Lx.i("Games: game-mode cleanup leaves RM Tweaks running");
                }
            })) n++;
        }
        if (n == 0) Lx.w("Games: game-mode cleanup hook not found (RM Tweaks may be stopped when a game starts)");
    }

    private static void games(final ClassLoader cl) {
        gppProps();
        keepAliveInGames(cl);
        int n = 0;
        // 1. frame interpolation + super resolution for every game. Game Assist asks MindSync for a game's GFRC capability
        //    (tens = upscaling, units = interpolation, 0..2 each; 0 keeps the panel at "Original"): answer 22.
        Class<?> msvc = XposedHelpers.findClassIfExists("com.android.server.am.MindSyncService", cl);
        Method cap = null;
        if (msvc != null) {
            for (int i = 1; i <= 12 && cap == null; i++) {
                Class<?> stub = XposedHelpers.findClassIfExists("com.android.server.am.MindSyncService$" + i, cl);
                if (stub != null) cap = H.find(stub, "getGfrcCapByPkg", String.class);
            }
        }
        if (H.hook(cap, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    syncGfrcProp();
                    if (!Cfg.on(Features.GAME_GFRC_ALL) || !(p.getResult() instanceof Integer)) return;
                    int was = (Integer) p.getResult();
                    if (was == 22) return;
                    p.setResult(22);
                    if (loggedGames.add("gfrc:" + p.args[0])) Lx.i("Games: frame interpolation + super resolution unlocked for " + p.args[0] + " (capability " + was + " -> 22)");
                } catch (Throwable t) { Lx.e("Games: GFRC capability", t); }
            }
        })) n++;
        else Lx.w("Games: MindSyncService getGfrcCapByPkg not found, frame interpolation for every game only via " + GFRC_ALL_PROP);
        // the property is not persistent: set it once the system is up (and again whenever Game Assist asks, above)
        Thread t = new Thread(() -> {
            for (int i = 0; i < 150; i++) {
                if ("1".equals(sysProp("sys.boot_completed"))) { syncGfrcProp(); return; }
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
            }
        }, "rmtweaks-gfrc");
        t.setDaemon(true);
        t.start();

        // 2. full screen for Game Space games: no max aspect ratio unless a ratio was picked in Game Space
        //    (AppCompatAspectRatioPolicy.applyAspectRatio then leaves the bounds alone; a 16:9 picture can't be stretched).
        Class<?> policy = XposedHelpers.findClassIfExists(WM + "AppCompatAspectRatioPolicy", cl);
        if (H.hook(H.find(policy, "getMaxAspectRatio"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (!Cfg.on(Features.GAME_FULLSCREEN)) return;
                    Object r = p.getResult();
                    if (!(r instanceof Float) || (Float) r == 0f) return;
                    Object ar = XposedHelpers.getObjectField(p.thisObject, "mActivityRecord");
                    String pkg = ar == null ? null : (String) XposedHelpers.getObjectField(ar, "packageName");
                    if (!isGameSpaceGame(cl, pkg) || ratioPicked(pkg)) return;
                    p.setResult(0f);
                    if (loggedGames.add("full:" + pkg)) Lx.i("Games: " + pkg + " full screen (max aspect ratio " + r + " dropped)");
                } catch (Throwable t) { Lx.e("Games: full screen", t); }
            }
        })) n++;
        else Lx.w("Games: AppCompatAspectRatioPolicy#getMaxAspectRatio not found, full screen for games off");

        // 3. experimental: a longer launch boost for Game Space games, on top of the stock one
        Class<?> ats = XposedHelpers.findClassIfExists(WM + "ActivityTaskSupervisor", cl);
        if (H.hookAll(ats, "acquireAppLaunchPerfLock", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    int idx = Cfg.choice(Features.GAME_LAUNCH_BOOST);
                    if (idx <= 0 || idx >= Features.LAUNCH_BOOST_MS.length || p.args.length != 1 || p.args[0] == null) return;
                    String pkg = (String) XposedHelpers.getObjectField(p.args[0], "packageName");
                    if (!boostWanted(cl, pkg)) return;
                    Object boost = XposedHelpers.getObjectField(p.thisObject, "mPerfBoost");
                    if (boost == null) return;
                    holdBoost(boost, pkg, Features.LAUNCH_BOOST_MS[idx]);
                } catch (Throwable t) { Lx.e("Games: launch boost", t); }
            }
        }) > 0) n++;
        Lx.i("Games: system_server hooks " + n);
    }

    /** The games picked for the launch boost ("pkg;pkg"), else every Game Space game. */
    private static boolean boostWanted(ClassLoader cl, String pkg) {
        if (pkg == null) return false;
        String picked = Cfg.str(Features.GAME_LAUNCH_BOOST_PKGS);
        if (picked != null && !picked.trim().isEmpty()) {
            for (String e : picked.split(";")) if (pkg.equals(e.trim())) return true;
            return false;
        }
        return isGameSpaceGame(cl, pkg);
    }

    private static android.os.Handler boostHandler;
    private static final java.util.Map<String, Long> boostUntil = new java.util.concurrent.ConcurrentHashMap<>();
    /** One hint at most this long: the perf HAL may cut a single long launch hint short, so long boosts are re-sent. */
    private static final int BOOST_STEP_MS = 5000;

    /**
     * VENDOR_HINT_FIRST_LAUNCH_BOOST (0x1081, launch type 1) for {@code ms}: sent in steps of up to 5 s back to back, so
     * 12 / 15 / 20 s really last that long. A new launch of the same game restarts the window instead of adding a second
     * chain.
     */
    private static void holdBoost(final Object boost, final String pkg, final int ms) {
        final long until = android.os.SystemClock.uptimeMillis() + ms;
        Long prev = boostUntil.put(pkg, until);
        boolean chainRunning = prev != null && prev > android.os.SystemClock.uptimeMillis();
        synchronized (FrameworkHooks.class) {
            if (boostHandler == null) {
                android.os.HandlerThread t = new android.os.HandlerThread("rmtweaks-boost");
                t.start();
                boostHandler = new android.os.Handler(t.getLooper());
            }
        }
        if (loggedGames.add("boost:" + pkg + ":" + ms)) Lx.i("Games: " + (ms / 1000) + " s launch boost for " + pkg + " (hint every " + (BOOST_STEP_MS / 1000) + " s)");
        if (chainRunning) return;
        boostHandler.post(new Runnable() {
            @Override public void run() {
                Long end = boostUntil.get(pkg);
                long left = end == null ? 0 : end - android.os.SystemClock.uptimeMillis();
                if (left < 300) { boostUntil.remove(pkg); return; }
                int step = (int) Math.min(BOOST_STEP_MS, left);
                try { XposedHelpers.callMethod(boost, "perfHint", 0x1081, pkg, step, 1); }
                catch (Throwable t) { Lx.e("Games: launch boost hint", t); boostUntil.remove(pkg); return; }
                boostHandler.postDelayed(this, Math.max(250, step - 200));
            }
        });
    }

    /** A ratio was picked for pkg in Game Space (Settings.Global nubia_gameratio_enable_pkgs, "pkg," entries). */
    private static boolean ratioPicked(String pkg) {
        try {
            if (sys == null) return false;
            String s = android.provider.Settings.Global.getString(sys.getContentResolver(), "nubia_gameratio_enable_pkgs");
            if (s == null || s.isEmpty()) return false;
            for (String e : s.split(",")) if (pkg.equals(e.trim())) return true;
            return false;
        } catch (Throwable t) { return false; }
    }

    private static void thermalAndPower(ClassLoader cl) {
        Class<?> tms = H.cls(cl, "com.android.server.power.ThermalManagerService");
        if (tms != null) H.hookAll(tms, "onTemperatureChanged", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(FrameworkFeatures.NO_THERMAL_LIMIT) || p.args[0] == null) return;
                try {
                    Object t = p.args[0];                       // android.os.Temperature (system API, not in the SDK stubs)
                    int status = (Integer) XposedHelpers.callMethod(t, "getStatus");
                    if (status == 6 /* SHUTDOWN */ || status == 0 /* NONE */) return;
                    float value = (Float) XposedHelpers.callMethod(t, "getValue");
                    int type = (Integer) XposedHelpers.callMethod(t, "getType");
                    String name = (String) XposedHelpers.callMethod(t, "getName");
                    p.args[0] = XposedHelpers.newInstance(t.getClass(), value, type, name, 0);
                } catch (Throwable e) { Lx.w("framework: thermal rewrite failed: " + e); }
            }
        });
        Class<?> pms = H.cls(cl, "com.android.server.power.PowerManagerService");
        if (pms != null) H.hookAll(pms, "getScreenOffTimeoutLocked", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { if (Cfg.on(FrameworkFeatures.SCREEN_NEVER)) p.setResult((long) Integer.MAX_VALUE); }
        });
    }

    // ------------------------------------------------------------ 14. quiet when unlocked

    private static final ThreadLocal<Object> stashedAudio = new ThreadLocal<>();

    private static void notifications(ClassLoader cl) {
        Class<?> nah = XposedHelpers.findClassIfExists("com.android.server.notification.NotificationAttentionHelper", cl);
        if (nah == null) return;
        H.hookAll(nah, "buzzBeepBlinkLocked", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(FrameworkFeatures.QUIET_UNLOCKED)) return;
                try {
                    boolean screenOn = XposedHelpers.getBooleanField(p.thisObject, "mScreenOn");
                    Object km = XposedHelpers.getObjectField(p.thisObject, "mKeyguardManager");
                    boolean locked = km != null && ((android.app.KeyguardManager) km).isKeyguardLocked();
                    if (screenOn && !locked) {
                        stashedAudio.set(XposedHelpers.getObjectField(p.thisObject, "mAudioManager"));
                        XposedHelpers.setObjectField(p.thisObject, "mAudioManager", null);
                    }
                } catch (Throwable ignored) { }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object a = stashedAudio.get();
                if (a != null) { try { XposedHelpers.setObjectField(p.thisObject, "mAudioManager", a); } catch (Throwable ignored) { } stashedAudio.remove(); }
            }
        });
        H.hookAll(nah, "clearSoundLocked", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    if (XposedHelpers.getObjectField(p.thisObject, "mAudioManager") == null) {
                        Context c = (Context) XposedHelpers.getObjectField(p.thisObject, "mContext");
                        XposedHelpers.setObjectField(p.thisObject, "mAudioManager", c.getSystemService(android.media.AudioManager.class));
                    }
                } catch (Throwable ignored) { }
            }
        });
    }
}
