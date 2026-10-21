package com.hammad.redmagictweaks.xposed;

import android.content.pm.ApplicationInfo;

import com.hammad.redmagictweaks.SettingsFeatures;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XC_MethodHook;

/**
 * com.android.permissioncontroller — ZTE's Default home list only offers system apps (ApplicationInfo
 * FLAG_SYSTEM / FLAG_UPDATED_SYSTEM_APP) or CTS packages. The filter is the static helper
 * K2.D.n(ApplicationInfo) (R8-renamed; firmware 11.0.11) used by role.ui.DefaultAppChildFragment.
 */
final class PermissionHooks {
    private PermissionHooks() {}

    /** Known names of the obfuscated utility class holding isSystemOrCtsApp(ApplicationInfo). */
    private static final String[] UTIL_CLASSES = {"K2.D", "com.android.permissioncontroller.zte.Utils", "com.zte.permission.utils.Utils"};

    static void install(ClassLoader cl) {
        Method m = null;
        for (String n : UTIL_CLASSES) {
            Class<?> c = de.robv.android.xposed.XposedHelpers.findClassIfExists(n, cl);
            if (c == null) continue;
            for (Method cand : c.getDeclaredMethods()) {
                if (!Modifier.isStatic(cand.getModifiers()) || cand.getReturnType() != boolean.class) continue;
                Class<?>[] pt = cand.getParameterTypes();
                if (pt.length == 1 && pt[0] == ApplicationInfo.class) { cand.setAccessible(true); m = cand; break; }
            }
            if (m != null) break;
        }
        if (m == null) { Lx.w("Permission: system-or-cts app filter not found (obfuscated name changed?)"); return; }
        final String found = m.getDeclaringClass().getName() + "#" + m.getName();
        H.hook(m, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!Cfg.on(SettingsFeatures.SET_ANY_LAUNCHER)) return;
                if (Boolean.FALSE.equals(p.getResult()) && H.calledFromPrefix("com.android.permissioncontroller.role.")) p.setResult(true);
            }
        });
        Lx.i("Permission: default-home filter hooked (" + found + ")");
    }
}
