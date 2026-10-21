package com.hammad.redmagictweaks.xposed;

import android.app.Application;
import android.content.Context;

import com.hammad.redmagictweaks.Features;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Module entry point (assets/xposed_init). Dispatches to one backend per host package. */
public final class Entry implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    public static String modulePath;

    @Override public void initZygote(StartupParam param) { modulePath = param.modulePath; }

    /** Install one backend; a crash in one never blocks the others in the same process. */
    private static void run(String name, Runnable r) {
        try { r.run(); } catch (Throwable t) { Lx.e(name + ": install crashed", t); }
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        final String pkg = lp.packageName;
        if (pkg == null) return;
        boolean main = pkg.equals(lp.processName);
        // Game packages run helper services in extra processes (e.g. Game Space's com.nubia.tgkservice):
        // hook every process of those; everything else only in its main process.
        boolean allProcesses = pkg.startsWith("cn.nubia.game")
                || pkg.equals("com.zte.heartyservice")      // UI lives in :ui
                || pkg.equals("com.zte.powersavemode")      // :service
                || pkg.equals("cn.nubia.touping");          // :lelinkps
        if (!main && !allProcesses) return;
        Lx.init(main ? pkg : pkg + "[" + lp.processName + "]");

        if (Features.PKG.equals(pkg)) {
            try {
                XposedHelpers.findAndHookMethod("com.hammad.redmagictweaks.ModuleStatus", lp.classLoader, "isActive",
                        de.robv.android.xposed.XC_MethodReplacement.returnConstant(true));
            } catch (Throwable t) { Lx.e("self status hook", t); }
            return;
        }

        // Fail-safe: nothing at all in safe mode; the framework skips its tweaks after two unfinished boots; an app that
        // crashed three times in five minutes with the tweaks loaded runs stock until they are turned back on (Guard).
        try {
            if (Guard.safeMode()) { Lx.w("Safe mode is on: no tweaks loaded in " + pkg); return; }
            if ("android".equals(pkg)) { if (Guard.bootLoop()) return; }
            else if (Guard.crashPaused(pkg, lp.appInfo == null ? null : lp.appInfo.dataDir)) return;
        } catch (Throwable t) { Lx.e("fail-safe check", t); }

        // Give the logger a Context as soon as the host Application exists.
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { Lx.attach((Context) p.args[0]); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { Lx.e("Application.attach hook", t); }

        long t0 = System.currentTimeMillis();
        try {
            switch (pkg) {
                case "cn.nubia.gameassist":      GameAssistHooks.install(lp.classLoader); break;
                case "cn.nubia.gamelauncher":    GameLauncherHooks.install(lp.classLoader); break;
                case "cn.nubia.gamehelpmodule":  GameHelperHooks.install(lp.classLoader); break;
                case "cn.nubia.gamelab":         GameLabHooks.install(lp.classLoader); break;
                case "cn.nubia.gamehighlights":  GameHighlightsHooks.install(lp.classLoader); break;
                case "com.zte.beautify":         run("Theme", () -> ThemeHooks.install(lp.classLoader)); run("ThemeStore", () -> ThemeStoreHooks.install(lp.classLoader)); break;
                case "com.zte.fingerprints":     FingerprintHooks.installThemeApp(lp.classLoader); break;
                case "com.fingerprint.sensorservice": run("Fingerprint", () -> FingerprintHooks.installSensorService(lp.classLoader)); run("SensorFingerprint", () -> SensorFingerprintHooks.install(lp.classLoader)); break;
                case "com.zte.mifavor.launcher":
                    run("Launcher", () -> LauncherHooks.install(lp.classLoader));
                    run("Recents", () -> RecentsHooks.install(lp.classLoader));
                    break;
                case "com.android.systemui":
                    run("Font", () -> FontHooks.install(lp.classLoader));
                    run("Charging", () -> SystemUiHooks.install(lp.classLoader));
                    run("LockScreen", () -> LockHooks.install(lp.classLoader));
                    run("AOD", () -> AodHooks.install(lp.classLoader));
                    run("OneUiLock", () -> OneUiLockHooks.install(lp.classLoader));
                    run("StatusBar", () -> StatusBarHooks.install(lp.classLoader));
                    run("FingerprintUi", () -> FingerprintUiHooks.install(lp.classLoader));
                    run("Cosmetics", () -> CosmeticsHooks.install(lp.classLoader));
                    run("Clock", () -> ClockHooks.install(lp.classLoader));
                    run("Icons", () -> IconPackHooks.install(lp.classLoader));
                    run("QS", () -> QsHooks.install(lp.classLoader));
                    run("Surface", () -> SurfaceHooks.install(lp.classLoader));
                    break;
                case "com.android.packageinstaller": InstallerHooks.install(lp.classLoader); break;
                case "com.android.settings":     SettingsHooks.install(lp.classLoader); break;
                case "com.android.permissioncontroller": PermissionHooks.install(lp.classLoader); break;
                case "android":                  FrameworkHooks.install(lp.classLoader); break;
                case "com.zte.zgesture": case "com.zte.powersavemode": case "cn.nubia.keymapcenter": case "cn.nubia.colorfullight":
                case "com.zte.heartyservice": case "cn.nubia.game.magicvoice": case "cn.nubia.touping":
                    ExtrasHooks.install(lp.classLoader, pkg); break;
                default: return;
            }
            run("Extra", () -> com.hammad.redmagictweaks.FlavorBits.installHooks(pkg, lp.classLoader));
            Lx.i("hooks installed in " + (System.currentTimeMillis() - t0) + " ms; settings=" + Cfg.dump());
        } catch (Throwable t) {
            Lx.e("backend install crashed for " + pkg, t);
        }
    }
}
