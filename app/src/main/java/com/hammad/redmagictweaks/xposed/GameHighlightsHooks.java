package com.hammad.redmagictweaks.xposed;

import com.hammad.redmagictweaks.Features;

import de.robv.android.xposed.XC_MethodHook;

/** cn.nubia.gamehighlights — Red Magic Moments recording. */
final class GameHighlightsHooks {
    private GameHighlightsHooks() {}

    static void install(ClassLoader cl) {
        // MainActivity$g$a implements TopService.q; c(int type) == onAbnormalHandling. Type 10 aborts the recording.
        Class<?> cb = H.cls(cl, "cn.nubia.gamehighlights.Activity.MainActivity$g$a");
        if (cb == null) return;
        int n = 0;
        for (java.lang.reflect.Method m : cb.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length == 1 && pt[0] == int.class && m.getReturnType() == void.class) {
                m.setAccessible(true);
                if (H.hook(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (Cfg.on(Features.GAME_RECORD_FREE) && Integer.valueOf(10).equals(p.args[0])) {
                            Lx.i("Highlights: ignored abnormal-handling type 10");
                            p.setResult(null);
                        }
                    }
                })) n++;
            }
        }
        Lx.i("GameHighlights: hooked " + n + " abnormal-handling callback(s)");
    }
}
