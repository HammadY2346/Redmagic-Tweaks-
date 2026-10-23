package com.hammad.redmagictweaks;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Custom colours for the colour options: besides its presets, a colour option takes any colour (wheel, sliders or a
 * hex code), or two colours for a gradient where the option already has gradients.
 *
 * Stored next to the choice like a typed value: "<key>_cc" = a custom colour is in use, "<key>_cv" = the colour
 * (ARGB), "<key>_cg" = it is a gradient, "<key>_cv2" = the gradient's second colour. While a custom colour is in use
 * the choice itself holds the nearest preset (proxy), so code that only knows the presets still does something close.
 * Hooks read it with xposed.Cfg.color(key), previews with {@link #of(Looks.V, String)}; both return {c1} or {c1, c2}.
 */
public final class ColorPick {
    private ColorPick() {}

    public static final String ON = "_cc", C1 = "_cv", GRAD = "_cg", C2 = "_cv2";

    /** What a colour option allows. */
    public static final class Spec {
        public final boolean gradient, alpha;
        Spec(boolean gradient, boolean alpha) { this.gradient = gradient; this.alpha = alpha; }
    }

    /** Built on first use (hooked processes only use the constants and {@link #pair}). */
    private static Map<String, Spec> specs;
    private static synchronized Map<String, Spec> specs() {
        if (specs != null) return specs;
        Map<String, Spec> SPECS = new HashMap<>();
        SPECS.put(ClockFeatures.CLK_COLOR, new Spec(false, false));
        SPECS.put(IconPackFeatures.SBI_COLOR, new Spec(false, false));
        SPECS.put(StatusBarFeatures.SB_NET_COLOR, new Spec(false, false));
        SPECS.put(AodFeatures.CLOCK_COLOR, new Spec(true, false));
        SPECS.put(LockFeatures.CLOCK_COLOR, new Spec(true, false));
        SPECS.put(LockFeatures.DATE_COLOR, new Spec(true, false));
        SPECS.put(QsFeatures.QS_TILE_COLOR, new Spec(true, false));
        SPECS.put(QsFeatures.QS_SLIDER_COLOR, new Spec(true, false));
        SPECS.put(SurfaceFeatures.NOTIF_TINT, new Spec(true, false));
        SPECS.put(SurfaceFeatures.VOL_ONEUI_COLOR, new Spec(true, false));
        SPECS.put(SurfaceFeatures.PWR_ONEUI_COLORS, new Spec(false, true));
        SPECS.put(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_COLOR, new Spec(false, true));
        SPECS.put(FingerprintCustomFeatures.FP_ICON_COLOR, new Spec(false, false));
        return specs = SPECS;
    }

    /** True for a colour option (its sheet offers "Custom colour…"). */
    public static boolean is(String key) { return key != null && specs().containsKey(key); }
    public static Spec spec(String key) { Spec s = specs().get(key); return s != null ? s : new Spec(false, false); }

    /** The custom colours of {@code key} in {@code v}: {c1} or {c1, c2}, or null when a preset is in use. */
    public static int[] of(Looks.V v, String key) {
        if (v == null || key == null || !v.on(key + ON)) return null;
        int c1 = v.choice(key + C1);
        return v.on(key + GRAD) ? new int[]{c1, v.choice(key + C2)} : new int[]{c1};
    }

    /** {start, end}: a plain colour twice, a gradient's two colours. */
    public static int[] pair(int[] c) {
        if (c == null || c.length == 0) return null;
        return new int[]{c[0], c.length > 1 ? c[1] : c[0]};
    }

    // ============================================================================================ presets, with the custom colour on top

    /** AOD / lock clock and date colours {top, bottom} (AodFeatures.CLOCK_COLORS), or null for stock. */
    public static int[] clock(Context ctx, Looks.V v, String key) {
        int[] c = of(v, key);
        return c != null ? pair(c) : AodFeatures.clockColors(ctx, v.choice(key));
    }

    /** Panel colours {start, end} (QsArt.PANEL_COLORS), or null for stock. */
    public static int[] panel(Context ctx, Looks.V v, String key, boolean dark) {
        int[] c = of(v, key);
        return c != null ? pair(c) : com.hammad.redmagictweaks.xposed.QsArt.panelColors(ctx, v.choice(key), dark);
    }

    /** A single colour from ClockFeatures.COLOR_VALUES (status bar clock, status icons), or {@code fallback} for stock. */
    public static int named(Looks.V v, String key, int fallback) {
        int[] c = of(v, key);
        if (c != null) return c[0];
        int i = v.choice(key);
        return i > 0 && i < ClockFeatures.COLOR_VALUES.length ? ClockFeatures.COLOR_VALUES[i] : fallback;
    }

    // ============================================================================================ nearest preset (stored as the choice)

    /** The preset closest to a custom colour, stored as the choice while the custom colour is in use (never 0 = stock). */
    public static int proxy(String key, int c1, boolean gradient) {
        if (key == null) return 1;
        int[][] plain;
        int grad = -1;
        if (key.equals(ClockFeatures.CLK_COLOR) || key.equals(IconPackFeatures.SBI_COLOR) || key.equals(StatusBarFeatures.SB_NET_COLOR)) {
            plain = range(1, ClockFeatures.COLOR_VALUES.length - 1, i -> ClockFeatures.COLOR_VALUES[i]);
            int best = nearest(plain, c1, 3);
            return key.equals(StatusBarFeatures.SB_NET_COLOR) ? best + 1 : best;   // NET_COLORS = stock, same as clock, then COLOR_VALUES[1..]
        } else if (key.equals(AodFeatures.CLOCK_COLOR) || key.equals(LockFeatures.CLOCK_COLOR) || key.equals(LockFeatures.DATE_COLOR)) {
            plain = new int[10][];
            int n = 0;
            for (int i = 1; i <= 10; i++) { if (i == 2) continue; int[] c = AodFeatures.clockColors(null, i); if (c != null) plain[n++] = new int[]{i, c[0]}; }
            plain = java.util.Arrays.copyOf(plain, n);
            grad = 11;
        } else if (key.equals(QsFeatures.QS_TILE_COLOR) || key.equals(QsFeatures.QS_SLIDER_COLOR) || key.equals(SurfaceFeatures.NOTIF_TINT)) {
            plain = range(2, 11, i -> com.hammad.redmagictweaks.xposed.QsArt.panelColors(null, i, true)[0]);
            grad = 12;
        } else if (key.equals(SurfaceFeatures.VOL_ONEUI_COLOR)) {
            plain = range(2, 9, i -> com.hammad.redmagictweaks.xposed.OneUiVolumeArt.fillPreset(i)[0]);
            grad = 10;
        } else if (key.equals(SurfaceFeatures.PWR_ONEUI_COLORS)) {
            return 1;                                                    // "Wallpaper colour": one colour for every button
        } else if (key.equals(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_COLOR)) {
            plain = range(1, 11, com.hammad.redmagictweaks.xposed.DrawerArt::colorArgb);
        } else if (key.equals(FingerprintCustomFeatures.FP_ICON_COLOR)) {
            plain = range(1, FingerprintCustomFeatures.ICON_COLOR_ARGB.length - 1, i -> FingerprintCustomFeatures.ICON_COLOR_ARGB[i]);
        } else return 1;
        if (gradient && grad > 0) return grad;
        return nearest(plain, c1, plain.length > 0 ? plain[0][0] : 1);
    }

    private interface IntMap { int at(int i); }

    private static int[][] range(int from, int to, IntMap m) {
        int[][] out = new int[Math.max(0, to - from + 1)][];
        for (int i = from; i <= to; i++) out[i - from] = new int[]{i, m.at(i)};
        return out;
    }

    private static int nearest(int[][] plain, int c, int fallback) {
        int best = fallback; long bd = Long.MAX_VALUE;
        for (int[] e : plain) {
            int dr = ((e[1] >> 16) & 0xff) - ((c >> 16) & 0xff), dg = ((e[1] >> 8) & 0xff) - ((c >> 8) & 0xff), db = (e[1] & 0xff) - (c & 0xff);
            long d = 3L * dr * dr + 4L * dg * dg + 2L * db * db;
            if (d < bd) { bd = d; best = e[0]; }
        }
        return best;
    }

    /** The colour(s) of a preset (to start the picker from), or null for stock / wallpaper / a set. */
    public static int[] preset(Context ctx, String key, int i) {
        if (i <= 0) return null;
        if (key.equals(ClockFeatures.CLK_COLOR) || key.equals(IconPackFeatures.SBI_COLOR))
            return i < ClockFeatures.COLOR_VALUES.length ? new int[]{ClockFeatures.COLOR_VALUES[i]} : null;
        if (key.equals(StatusBarFeatures.SB_NET_COLOR))
            return i >= 2 && i - 1 < ClockFeatures.COLOR_VALUES.length ? new int[]{ClockFeatures.COLOR_VALUES[i - 1]} : null;
        if (key.equals(AodFeatures.CLOCK_COLOR) || key.equals(LockFeatures.CLOCK_COLOR) || key.equals(LockFeatures.DATE_COLOR))
            return single(AodFeatures.clockColors(ctx, i));
        if (key.equals(QsFeatures.QS_TILE_COLOR) || key.equals(QsFeatures.QS_SLIDER_COLOR) || key.equals(SurfaceFeatures.NOTIF_TINT))
            return single(com.hammad.redmagictweaks.xposed.QsArt.panelColors(ctx, i, true));
        if (key.equals(SurfaceFeatures.VOL_ONEUI_COLOR)) return i == 1 ? null : com.hammad.redmagictweaks.xposed.OneUiVolumeArt.fillPreset(i);
        if (key.equals(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_COLOR)) return new int[]{com.hammad.redmagictweaks.xposed.DrawerArt.colorArgb(i)};
        if (key.equals(FingerprintCustomFeatures.FP_ICON_COLOR))
            return i < FingerprintCustomFeatures.ICON_COLOR_ARGB.length ? new int[]{FingerprintCustomFeatures.ICON_COLOR_ARGB[i]} : null;
        return null;
    }

    /** {a, a} -> {a}; a real gradient stays two colours. */
    private static int[] single(int[] c) { return c == null ? null : c.length > 1 && c[0] != c[1] ? c : new int[]{c[0]}; }

    // ============================================================================================ text and pictures

    public static String hex(int c, boolean alpha) {
        return alpha && (c >>> 24) != 0xFF ? String.format(Locale.US, "#%08X", c) : String.format(Locale.US, "#%06X", c & 0xFFFFFF);
    }

    /** "#FF8800" or "#FF8800 → #3366FF". */
    public static String label(int[] c, boolean alpha) {
        if (c == null) return "";
        return c.length > 1 ? hex(c[0], alpha) + " → " + hex(c[1], alpha) : hex(c[0], alpha);
    }

    /** #RGB, #RRGGBB or #AARRGGBB (the # is optional); null when it isn't a colour. */
    public static Integer parse(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 3) {
                StringBuilder b = new StringBuilder();
                for (char ch : s.toCharArray()) b.append(ch).append(ch);
                s = b.toString();
            }
            if (s.length() == 6) return 0xFF000000 | Integer.parseInt(s, 16);
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) { }
        return null;
    }

    /** A round swatch of a colour or a two-colour gradient (top to bottom). */
    public static Drawable swatch(Context ctx, int[] c, int sizeDp) {
        int[] p = pair(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, p == null ? new int[]{0, 0} : p);
        g.setShape(GradientDrawable.OVAL);
        int px = Math.round(sizeDp * ctx.getResources().getDisplayMetrics().density);
        g.setSize(px, px);
        g.setStroke(Math.max(1, px / 24), 0x55FFFFFF);
        return g;
    }
}
