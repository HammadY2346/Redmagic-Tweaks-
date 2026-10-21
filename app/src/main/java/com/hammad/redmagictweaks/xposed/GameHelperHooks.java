package com.hammad.redmagictweaks.xposed;

import com.hammad.redmagictweaks.Features;

import java.util.HashSet;

import de.robv.android.xposed.XC_MethodHook;

/** cn.nubia.gamehelpmodule — macros / one-key combo. */
final class GameHelperHooks {
    private GameHelperHooks() {}

    static void install(ClassLoader cl) {
        Class<?> pu = H.cls(cl, "cn.nubia.gamehelper.utils.PackageUtils");
        if (pu != null) {
            H.constant(pu, "isBlackPackageName", boolean.class, H.types(String.class), false, () -> Cfg.on(Features.GAME_MACRO_ANY));
            H.hook(H.find(pu, "getBlackPackageSet"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (Cfg.on(Features.GAME_MACRO_ANY)) p.setResult(new HashSet<String>());
                }
            });
        }
        Class<?> db = H.cls(cl, "cn.nubia.gamehelper.db.DbHelper");
        if (db != null) H.constant(db, "isPackageCanUseMacro", boolean.class, H.types(String.class), true, () -> Cfg.on(Features.GAME_MACRO_ANY));
        Class<?> mgr = H.cls(cl, "cn.nubia.gamehelper.manager.GameMotionHelperManager");
        if (mgr != null) {
            H.constant(mgr, "canUseMacro", boolean.class, H.types(), true, () -> Cfg.on(Features.GAME_MACRO_ANY));
            H.constant(mgr, "canUseSpeedMode", boolean.class, H.types(), true, () -> Cfg.on(Features.GAME_COMBO_SPEED));
        }
        Class<?> sw = H.cls(cl, "cn.nubia.gamehelper.MacroSwitch");
        if (sw != null) H.constant(sw, "isSpeedModeEnable", boolean.class, H.types(), true, () -> Cfg.on(Features.GAME_COMBO_SPEED));
        Class<?> du = H.cls(cl, "cn.nubia.gamehelper.utils.DeviceUtil");
        if (du != null) H.constant(du, "isUI85", boolean.class, H.types(), true, () -> Cfg.on(Features.GAME_COMBO_SPEED));
        Lx.i("GameHelper: macro hooks ready");
    }
}
