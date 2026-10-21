package com.hammad.redmagictweaks.xposed;

import android.content.Context;

import com.hammad.redmagictweaks.Features;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** com.android.packageinstaller — skip the ad/virus scan, hide warnings and store nags. */
final class InstallerHooks {
    private InstallerHooks() {}

    private static final String PIA = "com.android.packageinstaller.PackageInstallerActivity";

    static void install(ClassLoader cl) {
        // 1. no "scanning" splash + no ad/virus scan
        Class<?> ad = H.cls(cl, "com.android.packageinstaller.ad.AdUtil");
        if (ad != null) H.constant(ad, "isAdSwitchOn", boolean.class, H.types(Context.class), false, () -> Cfg.on(Features.INST_SKIP_SCAN));
        Class<?> scan = H.cls(cl, "com.android.packageinstaller.InstallStaging$ScanningAsyncTask");
        if (scan != null) {
            H.hookAll(scan, "onPostExecute", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(Features.INST_SKIP_SCAN)) return;
                    try {
                        Object outer = XposedHelpers.getObjectField(p.thisObject, "this$0");
                        XposedHelpers.setLongField(outer, "mScanningStartTime", 0L);   // elapsed >= 2 s -> continue immediately
                    } catch (Throwable t) { Lx.w("installer: cannot shortcut scan delay: " + t); }
                }
            });
        }
        Class<?> pu = H.cls(cl, "com.android.packageinstaller.PackageUtil");
        if (pu != null && Cfg.on(Features.INST_SKIP_SCAN)) {
            try { XposedHelpers.setStaticBooleanField(pu, "ZTE_FEATURE_ODM_VERTU", true); Lx.i("installer: ODM_VERTU flag set (plain install flow)"); }
            catch (Throwable t) { Lx.w("installer: ODM_VERTU not settable: " + t); }
        }

        Class<?> pia = H.cls(cl, PIA);
        if (pia == null) return;
        // 2. no "install from the official store instead" recommendation
        H.constant(pia, "enableOfficialRecommendation", boolean.class, H.types(), false, () -> Cfg.on(Features.INST_NO_STORE_NAG));
        H.constant(pia, "enableOfficialRecommendationNUB", boolean.class, H.types(), false, () -> Cfg.on(Features.INST_NO_STORE_NAG));
        // 3. risk / fraud / virus levels -> safe
        H.hook(H.find(pia, "getCookUI", int.class, int.class, int.class, boolean.class, String.class, String.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(Features.INST_NO_RISK)) return;
                p.args[0] = 0; p.args[1] = 0; p.args[2] = 0; p.args[4] = null; p.args[5] = null;
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object ui = p.getResult();
                if (ui == null) return;
                try {
                    if (Cfg.on(Features.INST_NO_RISK)) { XposedHelpers.setBooleanField(ui, "needIsolate", false); XposedHelpers.setBooleanField(ui, "showRiskCheckbox", false); }
                    if (Cfg.on(Features.INST_HIDE_PURE)) XposedHelpers.setBooleanField(ui, "hidePureModeSwitchLayout", true);
                } catch (Throwable t) { Lx.w("installer: UICookTool fields: " + t); }
            }
        });
        Lx.i("Installer: hooks ready");
    }
}
