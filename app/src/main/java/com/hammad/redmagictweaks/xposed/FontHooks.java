package com.hammad.redmagictweaks.xposed;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.XModuleResources;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontStyle;

import com.hammad.redmagictweaks.CosmeticsFeatures;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * System UI font (CosmeticsFeatures.COS_SYSUI_FONT): every sans-serif family of the System UI process (the entries of
 * Typeface.sSystemFontMap such as "sans-serif", "sans-serif-medium", "google-sans-text", plus Typeface.DEFAULT and the
 * native default) becomes one font family built from the module's assets/fonts/oneui_sans.ttf (Inter, variable weight
 * axis, SIL OFL), with real 300..800 weights so bold and medium text pick the right instance. Missing glyphs (CJK,
 * Arabic, emoji...) fall back to the system fonts. Only this process changes; apps keep their fonts.
 * Applied when the hooks load, again once the app has its font map (Application.attach) and whenever
 * Typeface.setSystemFontMap installs a new one.
 */
final class FontHooks {
    private FontHooks() {}

    static final String ASSET = "fonts/oneui_sans.ttf";
    private static FontFamily family;
    private static final Map<Integer, Typeface> byWeight = new java.util.HashMap<>();
    private static boolean failed;

    static void install(ClassLoader cl) {
        if (Cfg.choice(CosmeticsFeatures.COS_SYSUI_FONT) != CosmeticsFeatures.SYSUI_FONT_ONE_UI) return;
        if (!load()) return;
        apply("load");
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { apply("attach"); }
        });
        for (java.lang.reflect.Method m : Typeface.class.getDeclaredMethods()) {
            if (!m.getName().equals("setSystemFontMap")) continue;
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { apply("font map"); }
            });
        }
    }

    private static boolean load() {
        if (family != null) return true;
        if (failed) return false;
        failed = true;
        if (Entry.modulePath == null) { Lx.w("Font: module path unknown"); return false; }
        try {
            AssetManager am = XModuleResources.createInstance(Entry.modulePath, null).getAssets();
            java.nio.ByteBuffer data;
            try (InputStream in = am.open(ASSET)) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(1 << 20);
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                byte[] bytes = out.toByteArray();
                if (bytes.length < 1024) throw new java.io.IOException("empty");
                data = java.nio.ByteBuffer.allocateDirect(bytes.length).order(java.nio.ByteOrder.nativeOrder());
                data.put(bytes); data.rewind();
            } catch (Throwable t) { Lx.w("Font: " + ASSET + " is not in this build of the module (" + t + ")"); return false; }
            // one copy of the font data in memory, one Font per weight on the variable 'wght' axis
            FontFamily.Builder fb = null;
            for (int w = 300; w <= 800; w += 100) {
                Font f = new Font.Builder(data.duplicate()).setWeight(w).setSlant(FontStyle.FONT_SLANT_UPRIGHT)
                        .setFontVariationSettings("'wght' " + w).build();
                if (fb == null) fb = new FontFamily.Builder(f); else fb.addFont(f);
            }
            family = fb.build();
            failed = false;
            return true;
        } catch (Throwable t) {
            Lx.e("Font: could not build the One UI style font", t);
            return false;
        }
    }

    private static Typeface weight(int w) {
        Typeface t = byWeight.get(w);
        if (t == null) {
            t = new Typeface.CustomFallbackBuilder(family).setStyle(new FontStyle(w, FontStyle.FONT_SLANT_UPRIGHT)).setSystemFallback("sans-serif").build();
            byWeight.put(w, t);
        }
        return t;
    }

    /** Weight for a system family name, or 0 when the family is not a sans-serif text face we should replace. */
    static int weightFor(String family) {
        String f = family.toLowerCase(Locale.ROOT);
        if (f.contains("mono") || f.equals("serif") || f.startsWith("serif-") || f.contains("cursive") || f.contains("casual")
                || f.contains("emoji") || f.contains("condensed") || f.contains("clock") || f.contains("symbol") || f.contains("smallcaps")) return 0;
        boolean sans = f.startsWith("sans-serif") || f.startsWith("google-sans") || f.startsWith("roboto") || f.equals("arial")
                || f.equals("helvetica") || f.equals("tahoma") || f.equals("verdana") || f.startsWith("source-sans-pro")
                || f.startsWith("zte") || f.startsWith("mifavor") || f.startsWith("nubia") || f.startsWith("redmagic");
        if (!sans) return 0;
        if (f.contains("black")) return 900;
        if (f.contains("extrabold") || f.contains("extra-bold")) return 800;
        if (f.contains("semibold") || f.contains("semi-bold")) return 600;
        if (f.contains("bold")) return 700;
        if (f.contains("medium")) return 500;
        if (f.contains("light")) return 300;
        if (f.contains("thin")) return 200;
        return 400;
    }

    @SuppressWarnings("unchecked")
    private static synchronized void apply(String when) {
        if (family == null) return;
        try {
            Map<String, Typeface> map = (Map<String, Typeface>) XposedHelpers.getStaticObjectField(Typeface.class, "sSystemFontMap");
            List<String> done = new ArrayList<>();
            if (map != null) {
                synchronized (map) {
                    for (String k : new ArrayList<>(map.keySet())) {
                        int w = weightFor(k);
                        if (w == 0) continue;
                        Typeface t = weight(w);
                        if (map.get(k) != t) { map.put(k, t); done.add(k); }
                    }
                }
            }
            Typeface regular = weight(400), bold = weight(700);
            try { XposedHelpers.setStaticObjectField(Typeface.class, "DEFAULT", regular); } catch (Throwable ignored) { }
            try { XposedHelpers.setStaticObjectField(Typeface.class, "SANS_SERIF", regular); } catch (Throwable ignored) { }
            try { XposedHelpers.setStaticObjectField(Typeface.class, "DEFAULT_BOLD", bold); } catch (Throwable ignored) { }
            try {
                Object defaults = XposedHelpers.getStaticObjectField(Typeface.class, "sDefaults");
                if (defaults instanceof Typeface[] && ((Typeface[]) defaults).length >= 2) { ((Typeface[]) defaults)[0] = regular; ((Typeface[]) defaults)[1] = bold; }
            } catch (Throwable ignored) { }
            try { XposedHelpers.callStaticMethod(Typeface.class, "setDefault", regular); } catch (Throwable t) { Lx.w("Font: Typeface.setDefault: " + t); }
            if (!done.isEmpty() || "load".equals(when)) Lx.i("Font: One UI style font (" + when + "), families: " + done);
        } catch (Throwable t) {
            Lx.e("Font: apply (" + when + ")", t);
        }
    }
}
