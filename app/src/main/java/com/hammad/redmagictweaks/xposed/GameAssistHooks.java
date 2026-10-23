package com.hammad.redmagictweaks.xposed;

import android.content.ContentResolver;
import android.content.Context;
import android.os.BaseBundle;
import android.provider.Settings;

import com.hammad.redmagictweaks.Features;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** cn.nubia.gameassist — Game Assist overlay, plugins, performance modes, Super Resolution. */
final class GameAssistHooks {
    private GameAssistHooks() {}

    private static final String PMC = "cn.nubia.gameassist.performance.PerformanceModeController";
    private static final String SRVC = "cn.nubia.plugin.superresolution.SuperResolutionViewController";
    private static final String SR_TILE = "cn.nubia.gameassist.plugin.tiles.SuperResolutionTile";
    private static final String BIABLO_TILE = "cn.nubia.gameassist.plugin.tiles.BiabloTile";
    private static final String PLUGIN_CONFIG = "cn.nubia.gameassist.plugin.config.PluginConfig";

    static void install(ClassLoader cl) {
        unfreeze(cl);
        superResolution(cl);
        dialogs(cl);
        activeMode(cl);
        deferHeavyListeners(cl);
        gameRatio(cl);
    }

    // ------------------------------------------------------------ Free display (Game Space ratio): more ratios, no arrows

    /** Extra Free display ratios: Game Space ids 0..4 are Original, 4:3, 16:9, 21:9, 32:9 (GameRatioDataMgr.n / j). */
    private static final int[] RATIO_ORDER = {0, 1, 6, 5, 2, 7, 3, 4, 8};
    private static final int CUSTOM_ID = 8;

    /** The custom ratio (GAME_VIEW_GS_CUSTOM), 0 = none. */
    private static float customRatio() {
        if (!Cfg.on(Features.GAME_VIEW_GS)) return 0f;
        if (Cfg.hasNum(Features.GAME_VIEW_GS_CUSTOM)) {
            int v = Cfg.num(Features.GAME_VIEW_GS_CUSTOM, 0);
            return v >= 100 && v <= 250 ? v / 100f : 0f;
        }
        int i = Cfg.choice(Features.GAME_VIEW_GS_CUSTOM);
        return i > 0 && i < Features.GS_CUSTOM_RATIO.length ? Features.GS_CUSTOM_RATIO[i] : 0f;
    }

    private static String customValue() {
        float r = customRatio();
        return r > 0f ? String.format(java.util.Locale.ROOT, "%.2f", r) : null;
    }

    /** "Custom 17:10" when the ratio is a small whole-number pair, else "Custom 1.72:1". */
    private static String customLabel() {
        float r = customRatio();
        for (int h = 1; h <= 20; h++) {
            int w = Math.round(r * h);
            if (Math.abs(w / (float) h - r) < 0.006f) return "Custom " + w + ":" + h;
        }
        return "Custom " + String.format(java.util.Locale.ROOT, "%.2f", r) + ":1";
    }
    private static final java.util.Map<Integer, String> EXTRA_VALUE = new java.util.HashMap<>();
    private static final java.util.Map<Integer, String> EXTRA_LABEL = new java.util.HashMap<>();
    static {
        EXTRA_VALUE.put(5, "1.60"); EXTRA_LABEL.put(5, "16:10");
        EXTRA_VALUE.put(6, "1.50"); EXTRA_LABEL.put(6, "3:2");
        EXTRA_VALUE.put(7, "2.00"); EXTRA_LABEL.put(7, "2:1");
    }

    /**
     * With "Game Space ratio fills the screen": Free display offers 3:2, 16:10 and 2:1 too (stored like Game Space's own,
     * "pkg:1.60" in nubia_gameratio_size_pkgs, which GameRatioStub reads as the window ratio), and the move-left / move-right
     * arrows of GameRatioIndicatorWindow stay hidden (the stretched game has no bars to move into).
     */
    private static void gameRatio(ClassLoader cl) {
        final String p = "cn.nubia.plugin.gameratio.";
        Class<?> mgr = H.cls(cl, p + "GameRatioDataMgr");
        Class<?> panel = H.cls(cl, p + "GameRatioSettingsPanel");
        Class<?> adapter = H.cls(cl, p + "ChoiceGridView$OptionAdapter");
        Class<?> ind = H.cls(cl, p + "GameRatioIndicatorWindow");
        int n = 0;
        // option id -> stored ratio, and back (always: a saved 1.60 keeps its option)
        if (H.hook(H.find(mgr, "n", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam m) {
                String v = (Integer) m.args[0] == CUSTOM_ID ? customValue() : EXTRA_VALUE.get((Integer) m.args[0]);
                if (v != null) m.setResult(v);
            }
        })) n++;
        if (H.hook(H.find(mgr, "j", String.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam m) {
                for (java.util.Map.Entry<Integer, String> e : EXTRA_VALUE.entrySet())
                    if (e.getValue().equals(m.args[0])) { m.setResult(e.getKey()); return; }
                String c = customValue();
                if (c != null && c.equals(m.args[0]) && Integer.valueOf(0).equals(m.getResult())) m.setResult(CUSTOM_ID);
            }
        })) n++;
        if (H.hook(H.find(mgr, "B", String.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam m) {
                for (java.util.Map.Entry<Integer, String> e : EXTRA_VALUE.entrySet())
                    if (e.getValue().equals(m.args[0])) { m.setResult(EXTRA_LABEL.get(e.getKey())); return; }
                String c = customValue();
                if (c != null && c.equals(m.args[0])) m.setResult(customLabel());
            }
        })) n++;
        // the ratio grid: Original, 4:3, 3:2, 16:10, 16:9, 2:1, 21:9, 32:9
        if (H.hook(H.find(panel, "setData", H.cls(cl, p + "GameRatioData")), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam m) {
                try {
                    if (!Cfg.on(Features.GAME_VIEW_GS)) return;
                    Object data = m.args[0];
                    if ("com.tencent.tmgp.sgame".equals(XposedHelpers.callMethod(data, "b"))) return;
                    Object grid = XposedHelpers.getObjectField(m.thisObject, "mSizeChoiceView");
                    java.util.List<?> items = (java.util.List<?>) XposedHelpers.getObjectField(grid, "mOptionItems");
                    java.util.Map<Integer, Integer> res = new java.util.HashMap<>();
                    for (Object it : items) res.put((Integer) XposedHelpers.callMethod(it, "a"), (Integer) XposedHelpers.callMethod(it, "b"));
                    Integer placeholder = res.get(0);
                    if (placeholder == null) return;
                    int[] ids = new int[RATIO_ORDER.length], labels = new int[RATIO_ORDER.length];
                    int k = 0;
                    boolean custom = customRatio() > 0f;
                    for (int id : RATIO_ORDER) {
                        Integer r = res.get(id);
                        if (id == CUSTOM_ID ? !custom : r == null && !EXTRA_VALUE.containsKey(id)) continue;
                        ids[k] = id;
                        labels[k] = r != null ? r : placeholder;          // label replaced in getView below
                        k++;
                    }
                    XposedHelpers.callMethod(grid, "b", java.util.Arrays.copyOf(ids, k), java.util.Arrays.copyOf(labels, k), XposedHelpers.callMethod(data, "c"));
                } catch (Throwable t) { Lx.e("GameAssist: Free display ratios", t); }
            }
        })) n++;
        if (H.hook(H.find(adapter, "getView", int.class, android.view.View.class, android.view.ViewGroup.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam m) {
                try {
                    Object item = XposedHelpers.callMethod(m.thisObject, "getItem", m.args[0]);
                    int id = (Integer) XposedHelpers.callMethod(item, "a");
                    String label = id == CUSTOM_ID ? customLabel() : EXTRA_LABEL.get(id);
                    if (label == null) return;
                    android.widget.RadioButton rb = radio((android.view.View) m.getResult());
                    if (rb != null) rb.setText(label);
                } catch (Throwable t) { Lx.e("GameAssist: Free display label", t); }
            }
        })) n++;
        // no move arrows while the ratio is stretched to the full screen
        if (H.hook(H.find(ind, "v", float.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam m) {
                if (Boolean.TRUE.equals(m.getResult()) && Cfg.on(Features.GAME_VIEW_GS)) {
                    m.setResult(false);
                    Lx.i("GameAssist: Free display arrows hidden (ratio " + m.args[0] + " is stretched)");
                }
            }
        })) n++;
        Lx.i("GameAssist: Free display hooks " + n + "/6");
    }

    private static android.widget.RadioButton radio(android.view.View v) {
        if (v instanceof android.widget.RadioButton) return (android.widget.RadioButton) v;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) { android.widget.RadioButton r = radio(g.getChildAt(i)); if (r != null) return r; }
        }
        return null;
    }

    // ------------------------------------------------------------ experimental: lighter Game Assist at game start

    /**
     * GameMonitor (com.zte.gameassist.common, R8 name of the dispatch: c()) calls onGameStart() on ~33 callbacks the moment a
     * game launches. These are the heavy ones (AI model, OCR, prediction, monitors); each declares onGameStart() itself.
     * Same names on the global EU build.
     */
    private static final String[] HEAVY = {
            "cn.nubia.gameassist.plugin.tiles.AiDetectTile", "cn.nubia.gameassist.plugin.tiles.CombatPowerTile",
            "cn.nubia.gameassist.plugin.tiles.GamePredictionTile", "cn.nubia.gameassist.plugin.tiles.CardAssistTile",
            "cn.nubia.gameassist.plugin.tiles.CounterTile", "cn.nubia.plugin.screenextraction.ScreenExtractionManager",
            "cn.nubia.gameassist.dessert.policy.performancemonitor.PerformanceMonitorController"};
    private static final String GAME_MONITOR = "com.zte.gameassist.common.GameMonitor";
    private static final long DEFER_MS = 8000;
    /** callback -> its postponed onGameStart (identity keys: these are long-lived singletons / tiles) */
    private static final java.util.Map<Object, Runnable> deferred = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static volatile android.os.Handler mainHandler;
    private static volatile boolean callerLogged;

    private static void deferHeavyListeners(ClassLoader cl) {
        if (android.os.Looper.getMainLooper() == null) { Lx.w("GameAssist: no main looper, game-start delay off"); return; }
        mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        int n = 0;
        XC_MethodHook start = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(final MethodHookParam p) {
                if (!Cfg.on(Features.GAME_ASSIST_DEFER)) return;
                // only the launch fan-out (GameMonitor.c); a tile registering mid-game (GameMonitor.h) starts at once
                if (!H.calledFrom(GAME_MONITOR, "c")) {
                    if (!callerLogged) { callerLogged = true; Lx.i("GameAssist: onGameStart of " + p.thisObject.getClass().getSimpleName() + " not from the launch dispatch, not delayed"); }
                    return;
                }
                final Object self = p.thisObject;
                final java.lang.reflect.Member method = p.method;
                final Object[] args = p.args;
                android.os.Looper lp = android.os.Looper.myLooper();
                final android.os.Handler h = lp == null || lp == android.os.Looper.getMainLooper() ? mainHandler : new android.os.Handler(lp);
                deferred.remove(self);                                   // a pending older start no longer runs (see run())
                Runnable r = new Runnable() {
                    @Override public void run() {
                        if (deferred.get(self) != this) return;                // cancelled by onGameStop / a newer start
                        deferred.remove(self);
                        try { de.robv.android.xposed.XposedBridge.invokeOriginalMethod(method, self, args); }
                        catch (Throwable t) { Lx.e("GameAssist: delayed onGameStart of " + self.getClass().getSimpleName(), t); }
                    }
                };
                deferred.put(self, r);
                h.postDelayed(r, DEFER_MS);
                Lx.i("GameAssist: " + self.getClass().getSimpleName() + ".onGameStart delayed " + (DEFER_MS / 1000) + " s");
                p.setResult(null);
            }
        };
        // a game stop cancels every start that hasn't run yet (GameMonitor tells all callbacks; any heavy one that
        // declares onGameStop is enough to see it)
        XC_MethodHook stop = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                synchronized (deferred) {
                    if (deferred.isEmpty()) return;
                    Lx.i("GameAssist: game stopped, " + deferred.size() + " delayed onGameStart call(s) dropped");
                    deferred.clear();
                }
            }
        };
        for (String name : HEAVY) {
            Class<?> c = de.robv.android.xposed.XposedHelpers.findClassIfExists(name, cl);
            if (c == null) { Lx.w("GameAssist: " + name + " missing, its start is not delayed"); continue; }
            Method s = null, e = null;
            try { s = c.getDeclaredMethod("onGameStart"); } catch (NoSuchMethodException ignored) { }
            try { e = c.getDeclaredMethod("onGameStop"); } catch (NoSuchMethodException ignored) { }
            if (s != null && H.hook(s, start)) n++;
            else Lx.w("GameAssist: " + c.getSimpleName() + "#onGameStart not declared");
            if (e != null) H.hook(e, stop);
        }
        Lx.i("GameAssist: game-start delay hooks " + n + "/" + HEAVY.length);
    }

    /**
     * ActiveModeTile resets Active Mode for a game on its first launch (onLauncherFirstPackage -> F0 ->
     * ActiveModeController.m(pkg)) and clears every entry at startup (p() -> q()). Keep the user's choice:
     * drop those two automatic calls; the tile's own handleClick (S) and uninstall (x) paths still work.
     * Names are the R8 names of firmware 11.0.11 (m = removeActiveModeSharedPreKey, q = resetAllKeys).
     */
    private static void activeMode(ClassLoader cl) {
        Class<?> c = H.cls(cl, "cn.nubia.gameassist.dessert.policy.ActiveModeController");
        if (c == null) { Lx.w("GameAssist: ActiveModeController missing"); return; }
        final String TILE = "cn.nubia.gameassist.dessert.tiles.ActiveModeTile";
        int n = 0;
        if (H.hook(H.resolveAny(c, void.class, H.types(String.class), "removeActiveModeSharedPreKey", "m"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(Features.GAME_ACTIVE_KEEP)) return;
                if (H.calledFrom(TILE, "S") || H.calledFrom(TILE, "handleClick") || H.calledFrom(TILE, "x")) return; // user tap / uninstall
                Lx.i("GameAssist: kept Active Mode for " + p.args[0] + " (blocked automatic reset)");
                p.setResult(null);
            }
        })) n++;
        if (H.hook(H.resolveAny(c, void.class, H.types(), "resetActiveModeSharedPreAllKey", "q"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(Features.GAME_ACTIVE_KEEP)) return;
                Lx.i("GameAssist: blocked Active Mode reset-all");
                p.setResult(null);
            }
        })) n++;
        Lx.i("GameAssist: active-mode hooks " + n);
    }

    /** Every plugin / overlay feature enabled for every package. */
    private static void unfreeze(ClassLoader cl) {
        Class<?> pc = H.cls(cl, PLUGIN_CONFIG);
        if (pc != null) {
            // isPluginEnable(Context, String)            -> firmware 11.0.11: k(Context, String)
            H.constant(pc, "isPluginEnable", boolean.class, H.types(Context.class, String.class), true, () -> Cfg.on(Features.GAME_UNFREEZE));
            // isPluginEnable(Context, String, String)    -> l(Context, String, String)
            H.constant(pc, "isPluginEnable", boolean.class, H.types(Context.class, String.class, String.class), true, () -> Cfg.on(Features.GAME_UNFREEZE));
        }
        // Bundle flags exchanged between Game Assist and its plugins.
        XC_MethodHook bundleHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!Cfg.on(Features.GAME_UNFREEZE)) return;
                Object k = p.args[0];
                if ("is_need_show_linkview".equals(k) || "isMacroEnable".equals(k) || "isPackageEnable".equals(k)) p.setResult(true);
            }
        };
        H.hook(H.find(BaseBundle.class, "getBoolean", String.class), bundleHook);
        H.hook(H.find(BaseBundle.class, "getBoolean", String.class, boolean.class), bundleHook);
        // Panel bottom bar: always show the shoulder-key button (SubViewController.initView -> o(ViewGroup))
        Class<?> svc = H.cls(cl, "cn.nubia.gameassist.operation.SubViewController");
        if (svc != null) {
            H.hook(H.resolveAny(svc, void.class, H.types(android.view.ViewGroup.class), "initView", "o"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(Features.GAME_UNFREEZE)) return;
                    try {
                        android.view.ViewGroup root = (android.view.ViewGroup) p.args[0];
                        Context c = root.getContext();
                        int id = c.getResources().getIdentifier("game_assist_button_key", "id", c.getPackageName());
                        android.view.View keys = id == 0 ? null : root.findViewById(id);
                        if (keys != null) { keys.setVisibility(android.view.View.VISIBLE); Lx.i("GameAssist: shoulder-key button forced visible"); }
                    } catch (Throwable t) { Lx.e("GameAssist: keys button", t); }
                }
            });
        }
        Lx.i("GameAssist: unfreeze hooks ready");
    }

    /** Super Resolution + Diablo coexistence, no auto-disable. */
    private static void superResolution(ClassLoader cl) {
        Class<?> pmc = H.cls(cl, PMC);
        Class<?> sr = H.cls(cl, SRVC);
        if (pmc != null) {
            // getPerformanceMode(String): inside the SR tile click, report 3 instead of 5/1 so the tile does not refuse.
            H.hook(H.find(pmc, "getPerformanceMode", String.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(Features.GAME_SR_COEXIST) && !Cfg.on(Features.GAME_SR_BALANCE)) return;
                    Object r = p.getResult();
                    if (!(r instanceof Integer) || !H.calledFrom(SR_TILE, null)) return;
                    int mode = (Integer) r;
                    boolean coexist = Cfg.on(Features.GAME_SR_COEXIST) && mode == 5;
                    boolean balance = Cfg.on(Features.GAME_SR_BALANCE) && (mode == 1 || mode == 2);
                    if (coexist || balance) {
                        Lx.i("SR: getPerformanceMode " + mode + " -> 3 (" + (coexist ? "coexist" : "balance") + ")");
                        p.setResult(3);
                    }
                }
            });
            // setBiabloModeEnable(String, boolean, boolean) -> C0(...) : block the automatic (false,false) shutdown.
            final Method setBiablo = H.resolveAny(pmc, void.class, H.types(String.class, boolean.class, boolean.class), "setBiabloModeEnable", "C0");
            H.hook(setBiablo, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    boolean enable = Boolean.TRUE.equals(p.args[1]);
                    boolean dialog = Boolean.TRUE.equals(p.args[2]);
                    if (!enable && !dialog && Cfg.on(Features.GAME_SR_COEXIST) && H.calledFrom(PMC, null) && !H.calledFrom(BIABLO_TILE, null)) {
                        Lx.i("SR: blocked automatic Diablo shutdown for " + p.args[0]);
                        p.setResult(null);
                        return;
                    }
                    if (enable && dialog && Cfg.on(Features.GAME_NO_DIALOGS)) {
                        Lx.i("Diablo: enabling without confirmation dialog for " + p.args[0]);
                        setBiablo.invoke(p.thisObject, p.args[0], true, false);
                        p.setResult(null);
                    }
                }
            });
        }
        if (sr != null) {
            // updateEnableSwitchPkg(String, boolean) -> L(String, boolean): block auto-off coming from the controller itself.
            H.hook(H.resolveAny(sr, void.class, H.types(String.class, boolean.class), "updateEnableSwitchPkg", "L"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(Features.GAME_SR_COEXIST)) return;
                    if (Boolean.FALSE.equals(p.args[1]) && H.calledFrom(SRVC, null) && !H.calledFrom(SR_TILE, null)
                            && !H.calledFrom("cn.nubia.plugin.superresolution.SuperResolutionSettingWindowManager", null)) {
                        Lx.i("SR: blocked automatic Super Resolution off for " + p.args[0]);
                        p.setResult(null);
                    }
                }
            });
            // isSuperResolutionOn(String) -> w(String): Diablo tile / controller must not see SR as on.
            H.hook(H.resolveAny(sr, boolean.class, H.types(String.class), "isSuperResolutionEnabledForPkg", "isOpenSuperResolution", "w"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(Features.GAME_SR_COEXIST)) return;
                    if (Boolean.TRUE.equals(p.getResult()) && (H.calledFrom(BIABLO_TILE, null) || H.calledFrom(PMC, null))) {
                        p.setResult(false);
                    }
                }
            });
        }
        // low_power gate inside the SR tile click
        H.hook(H.find(Settings.Global.class, "getInt", ContentResolver.class, String.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if ("low_power".equals(p.args[1]) && Cfg.on(Features.GAME_SR_COEXIST) && H.calledFrom(SR_TILE, null)) p.setResult(0);
            }
        });
        Lx.i("GameAssist: super-resolution hooks ready");
    }

    /** Diablo confirmation dialog: run the confirm action straight away. */
    private static void dialogs(ClassLoader cl) {
        Class<?> pmc = H.cls(cl, PMC);
        if (pmc == null) return;
        H.hook(H.resolveAny(pmc, void.class, H.types(Runnable.class), "showBiabloModeDialog", "F0"), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(Features.GAME_NO_DIALOGS)) return;
                try { ((Runnable) p.args[0]).run(); } catch (Throwable t) { Lx.e("Diablo confirm runnable", t); }
                p.setResult(null);
            }
        });
    }
}
