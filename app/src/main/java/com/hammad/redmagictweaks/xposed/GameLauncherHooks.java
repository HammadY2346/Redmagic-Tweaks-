package com.hammad.redmagictweaks.xposed;

import android.content.ContentResolver;
import android.content.Context;

import com.hammad.redmagictweaks.Features;

import de.robv.android.xposed.XC_MethodHook;

/** cn.nubia.gamelauncher — Game Space, trigger-key (TGK) mapping. */
final class GameLauncherHooks {
    private GameLauncherHooks() {}

    private static final String GFAV = "cn.nubia.gamelauncher.gamecontrolpanel.GameFunctionAllocationView";

    static void install(ClassLoader cl) {
        Class<?> tgk = H.cls(cl, "cn.nubia.tgk.TgkHelper");
        if (tgk != null) {
            H.constant(tgk, "disableTgkFunction", boolean.class, H.types(String.class), false, () -> Cfg.on(Features.GAME_TGK_ALL));
            H.constant(tgk, "getTgkDisableOpt", int.class, H.types(ContentResolver.class, String.class), 0, () -> Cfg.on(Features.GAME_TGK_ALL));
        }
        Class<?> pc = H.cls(cl, "cn.nubia.gamelauncher.gamecontrolpanel.config.PluginConfig");
        if (pc != null) {
            H.hook(H.find(pc, "getBlackList", Context.class, String.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (Cfg.on(Features.GAME_TGK_ALL)) p.setResult(new String[0]);
                }
            });
        }
        Class<?> view = H.cls(cl, GFAV);
        if (view != null) installRedMagicTime(view);
        Lx.i("GameLauncher: hooks ready");
    }

    /**
     * RedMagic Time (auto-record) in the Game Space panel. Stock behaviour: in Diablo mode
     * (performance_mode_value == 4) the panel greys the RedMagic Time controls (updateUI(true)) and the
     * performance-mode switch calls disable/enableRedMagicTime by itself. With GAME_KEEP_RMTIME:
     *  - updateUI(true) becomes updateUI(false) while in Diablo mode, so the controls stay usable;
     *  - automatic disable/enableRedMagicTime calls are dropped (the user's own tap still works);
     *  - the user's choice is remembered per game (setChoiceGameSettings) and restored in initView,
     *    because the stock code re-derives it from the performance mode.
     */
    private static void installRedMagicTime(final Class<?> view) {
        XC_MethodHook blockAuto = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(Features.GAME_KEEP_RMTIME)) return;
                if (!H.calledFromPrefix(GFAV + "$")) {
                    Lx.i("GameSpace: blocked automatic " + p.method.getName());
                    p.setResult(null);
                }
            }
        };
        H.hook(H.find(view, "disableRedMagicTime"), blockAuto);
        H.hook(H.find(view, "enableRedMagicTime"), blockAuto);

        H.hook(H.find(view, "updateUI", boolean.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(Features.GAME_KEEP_RMTIME) || !Boolean.TRUE.equals(p.args[0])) return;
                if (perfMode(p.thisObject) == 4) { p.args[0] = false; Lx.i("GameSpace: RedMagic Time controls kept enabled in Diablo mode"); }
            }
        });

        H.hook(H.find(view, "setChoiceGameSettings", boolean.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!Cfg.on(Features.GAME_KEEP_RMTIME)) return;
                Context c = ctx(p.thisObject);
                String pkg = str(p.thisObject, "mCurrentPackageName");
                if (c == null || pkg == null) return;
                c.getSharedPreferences(PREFS, 0).edit().putBoolean("diablo_" + pkg, Boolean.TRUE.equals(p.args[0])).apply();
            }
        });

        H.hook(H.find(view, "initView"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!Cfg.on(Features.GAME_KEEP_RMTIME)) return;
                try {
                    Object v = p.thisObject;
                    Context c = ctx(v);
                    if (c == null || perfMode(v) != 4) return;
                    String pkg = str(v, "mCurrentPackageName");
                    boolean want = pkg != null && c.getSharedPreferences(PREFS, 0).getBoolean("diablo_" + pkg, false);
                    boolean open = de.robv.android.xposed.XposedHelpers.getBooleanField(v, "isRedmagicTimeCheckboxOpen");
                    if (!want || open) return;
                    de.robv.android.xposed.XposedHelpers.setBooleanField(v, "isRedmagicTimeCheckboxOpen", true);
                    Object cb = de.robv.android.xposed.XposedHelpers.getObjectField(v, "redmagicTimeCheckbox");
                    de.robv.android.xposed.XposedHelpers.callMethod(v, "setChecked", cb, true);
                    de.robv.android.xposed.XposedHelpers.callMethod(v, "isShowMoreVideo", true);
                    Lx.i("GameSpace: restored RedMagic Time = on for " + pkg + " (Diablo mode)");
                } catch (Throwable t) { Lx.e("GameSpace: initView restore", t); }
            }
        });
    }

    private static final String PREFS = "rmtweaks_redmagic_time";

    private static Context ctx(Object view) {
        try { Object c = de.robv.android.xposed.XposedHelpers.getObjectField(view, "mContext"); return c instanceof Context ? (Context) c : null; }
        catch (Throwable t) { return view instanceof android.view.View ? ((android.view.View) view).getContext() : null; }
    }
    private static String str(Object o, String field) {
        try { Object s = de.robv.android.xposed.XposedHelpers.getObjectField(o, field); return s == null ? null : s.toString(); } catch (Throwable t) { return null; }
    }
    private static int perfMode(Object view) {
        Context c = ctx(view);
        return c == null ? -1 : android.provider.Settings.Global.getInt(c.getContentResolver(), "performance_mode_value", 2);
    }
}
