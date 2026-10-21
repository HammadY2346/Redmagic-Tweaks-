package com.hammad.redmagictweaks.xposed;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.hammad.redmagictweaks.FrameworkFeatures;

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
        Lx.i("Framework: hooks ready");
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
                int f = forcedFps(ctl);
                if (f > 0) p.setResult(f);
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
                    if (!Cfg.on(FrameworkFeatures.AUTO_RATE_MAX)) return;
                    try {
                        int mode = p.args.length > 0 && p.args[0] instanceof Integer ? (Integer) p.args[0] : XposedHelpers.getStaticIntField(gsf, "sRefreshRateMode");
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
