package com.hammad.redmagictweaks.xposed;

import com.hammad.redmagictweaks.Features;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.zte.beautify (Theme Store) — present the phone as a collaboration edition.
 *
 * The Theme Store picks its local theme / lock-screen list (one xml in com.zte.mifavor.theme.resource,
 * see XmlParseResource.generatePriorityList) and the folder of the theme packs
 * (/system/etc/custom_config/themes/[variant-id]/) from exactly two values:
 * Utilities.getThemeResourceFeature() ("ro.vendor.feature.zte_feature_theme_resource" through ZteFeature)
 * and Utilities.getStringSystemProperties("persist.vendor.custom.variant.id"). Both are spoofed here.
 *
 * This class is the ONLY place that hooks those two methods: ThemeStoreHooks needs a per-thread
 * override of the same values while it re-parses the config of another edition, and two independent
 * after-hooks on one method run in an order we do not control (the last one wins), so the thread-local
 * override is consulted here first and the user's variant second.
 */
final class ThemeHooks {
    private ThemeHooks() {}

    private static final String UTIL = "com.zte.beautify.view.common.tools.Utilities";
    static final String VARIANT_PROP = "persist.vendor.custom.variant.id";

    /** What the phone really reports (first un-spoofed result), for diagnostics and the stock merge. */
    static volatile String realFeature;
    static volatile String realVariant;
    private static volatile boolean reported;

    static String[] variant() {
        int i = Cfg.choice(Features.THEME_VARIANT);
        if (i <= 0 || i >= Features.THEME_VARIANTS.length) return null;
        return Features.THEME_VARIANTS[i];
    }

    static void install(ClassLoader cl) {
        final Class<?> u = H.cls(cl, UTIL);
        if (u == null) return;
        boolean a = H.hook(H.find(u, "getThemeResourceFeature"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object r = p.getResult();
                    if (realFeature == null && r instanceof String) realFeature = (String) r;
                    String[] f = ThemeStoreHooks.forced();
                    if (f != null) { p.setResult(f[0]); return; }
                    String[] v = variant();
                    if (v != null) p.setResult(v[1]);
                    reportOnce(u);
                } catch (Throwable t) { Lx.e("Theme: feature hook", t); }
            }
        });
        boolean b = H.hook(H.find(u, "getStringSystemProperties", String.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (!VARIANT_PROP.equals(p.args[0])) return;
                    Object r = p.getResult();
                    if (realVariant == null && r instanceof String) realVariant = (String) r;
                    String[] f = ThemeStoreHooks.forced();
                    if (f != null) { p.setResult(f[1]); return; }
                    String[] v = variant();
                    if (v != null) p.setResult(v[2]);
                } catch (Throwable t) { Lx.e("Theme: variant-id hook", t); }
            }
        });
        if (!a || !b) Lx.w("Theme: Utilities hooks incomplete (feature=" + a + ", variantId=" + b + ")");
        Lx.i("Theme: variant hooks ready (" + (variant() == null ? "off" : variant()[0]) + ")");
    }

    /** One line with what the phone really is, so the log tells us which config/folders the stock app uses. */
    private static void reportOnce(Class<?> u) {
        if (reported) return;
        reported = true;
        try {
            XposedHelpers.callStaticMethod(u, "getStringSystemProperties", VARIANT_PROP);   // records realVariant
            String product = str(XposedHelpers.callStaticMethod(u, "getStringSystemProperties", "ro.product.name"));
            String launcher = str(XposedHelpers.callStaticMethod(u, "getLauncherResourceFeature"));
            String[] v = variant();
            Lx.i("Theme: phone reports theme feature '" + realFeature + "', variant id '" + realVariant
                    + "', ro.product.name '" + product + "', launcher feature '" + launcher + "'"
                    + (v == null ? "" : " -> spoofed as feature '" + v[1] + "', variant id '" + v[2] + "'"));
        } catch (Throwable t) {
            Lx.w("Theme: report: " + t);
        }
    }

    private static String str(Object o) { return o instanceof String ? (String) o : String.valueOf(o); }
}
