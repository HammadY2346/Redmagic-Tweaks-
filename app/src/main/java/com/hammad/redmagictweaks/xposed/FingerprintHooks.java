package com.hammad.redmagictweaks.xposed;

import android.content.Context;

import com.hammad.redmagictweaks.Features;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;

/**
 * com.zte.fingerprints (style picker) and com.fingerprint.sensorservice (renders the animation).
 * Both gate the collaboration styles through a static OperatorConfig; we open the gates per toggle
 * and, for "unlock all", append every shipped style to the picker list.
 */
final class FingerprintHooks {
    private FingerprintHooks() {}

    /** OperatorConfig method -> feature key that unlocks it. */
    private static final String[][] GATES = {
        {"isCustomizeForChisa", Features.FP_CHISA},
        {"isCustomizeForFighting", Features.FP_FIGHTING},
        {"isCustomizeForWutheringWaves", Features.FP_WW},
        {"isCustomizeForLTY", Features.FP_LTY},
        {"isCustomizeForCN_IP_PB", Features.FP_SAGA},
    };

    /** Every style class shipped in the firmware's picker package; appended by "unlock all". */
    private static final String[] STYLES = {
        "Chisa", "LTYIPAnimation", "FightingVictoriousBuddha", "WutheringWaves", "ForceMatrix", "ForceMatrixTen",
        "FlameCipher", "RubikCube", "FollowLight", "ShowingOff", "SkyRoad", "VastSeaSmoke", "Ambilight", "AgainstWind",
        "GravitationalField", "BlackHoleGravity", "WaterRipples", "Infinity", "StarLight", "Hexagon", "Ripple"
    };

    private static void gates(ClassLoader cl, String operatorConfig) {
        Class<?> oc = H.cls(cl, operatorConfig);
        if (oc == null) return;
        for (final String[] g : GATES) {
            H.hook(H.find(oc, g[0]), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (Cfg.on(g[1]) || Cfg.on(Features.FP_UNLOCK_ALL)) p.setResult(true);
                }
            });
        }
    }

    static void installThemeApp(final ClassLoader cl) {
        gates(cl, "com.zte.fingerprint.config.OperatorConfig");
        Class<?> proc = H.cls(cl, "com.zte.fingerprint.theme.FingerprintAnimProcess");
        if (proc != null) {
            int n = H.hookAll(proc, "getAllFpAnim", new XC_MethodHook() {
                @SuppressWarnings({"unchecked", "rawtypes"})
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(Features.FP_UNLOCK_ALL)) return;
                    Object r = p.getResult();
                    if (!(r instanceof ArrayList)) return;
                    Context ctx = null;
                    for (Object a : p.args) if (a instanceof Context) { ctx = (Context) a; break; }
                    if (ctx == null) return;
                    ArrayList list = (ArrayList) r;
                    Set<String> present = new HashSet<>();
                    for (Object o : list) if (o != null) present.add(o.getClass().getName());
                    int added = 0;
                    for (String s : STYLES) {
                        String cn = "com.zte.fingerprint.theme.normal." + s;
                        if (present.contains(cn)) continue;
                        try {
                            Class<?> c = cl.loadClass(cn);
                            Object style = c.getMethod("create", Context.class).invoke(c.getDeclaredConstructor().newInstance(), ctx);
                            if (style != null) { list.add(style); present.add(cn); added++; }
                        } catch (Throwable t) {
                            Lx.w("fingerprint style " + s + " unavailable: " + t);
                        }
                    }
                    Lx.i("Fingerprint: picker list now " + list.size() + " styles (+" + added + ")");
                }
            });
            Lx.i("Fingerprint: hooked " + n + " getAllFpAnim overload(s)");
        }
    }

    static void installSensorService(ClassLoader cl) {
        gates(cl, "com.fingerprint.sensorservice.config.OperatorConfig");
        // the renderer also has a reflective LTY check in Utils
        Class<?> u = H.cls(cl, "com.fingerprint.sensorservice.utils.Utils");
        if (u != null) {
            H.hook(H.find(u, "isCustomizeForLTY"), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (Cfg.on(Features.FP_LTY) || Cfg.on(Features.FP_UNLOCK_ALL)) p.setResult(true);
                }
            });
        }
        Lx.i("Fingerprint: sensor-service gates ready");
    }
}
