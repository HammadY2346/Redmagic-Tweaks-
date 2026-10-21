package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * The One UI 8 volume panel and power menu options, shared by the System UI hooks (SurfaceHooks) and the app's
 * previews, so what the app draws is what the phone draws.
 *
 * Volume bar geometry is in the slider's own (unrotated) coordinates: ZTE's ColorSeekBarZTE is a horizontal bar
 * rotated 270 degrees, so its left end is the bottom of what you see.
 */
public final class OneUiVolumeArt {
    private OneUiVolumeArt() {}

    // ============================================================================================ keys and choices

    public static final String VOL_WIDTH = "vol_oneui_width", VOL_HEIGHT = "vol_oneui_height", VOL_SHAPE = "vol_oneui_shape",
            VOL_FILL = "vol_oneui_color", VOL_TRACK = "vol_oneui_track", VOL_ICON = "vol_oneui_icon", VOL_LEVEL = "vol_oneui_level";
    public static final String PWR_SIZE = "pwr_oneui_size", PWR_SHAPE = "pwr_oneui_shape", PWR_COLORS = "pwr_oneui_colors", PWR_LABELS = "pwr_oneui_labels";

    /** The switch that picks the One UI 8 panel; with it off the options restyle RedMagic's own bar. */
    public static final String VOL_ONEUI = "vol_oneui8";

    // index 0 is "Default": the One UI value with the One UI panel on, RedMagic's own with it off (stock stays stock)
    public static final String[] WIDTHS = {"Default (One UI 40 dp, RedMagic 20 dp)", "Slim (32 dp)", "Narrow (36 dp)", "Medium (44 dp)", "Wide (52 dp)",
            "Thin (14 dp)", "Hairline (8 dp)", "Bold (28 dp)"};
    private static final int[] WIDTH_DP = {40, 32, 36, 44, 52, 14, 8, 28};
    public static final String[] HEIGHTS = {"Default (One UI 80 dp taller, RedMagic stock)", "Stock height", "40 dp taller", "120 dp taller", "160 dp taller"};
    private static final int[] HEIGHT_DP = {80, 0, 40, 120, 160};
    public static final String[] SHAPES = {"Pill (One UI)", "Rounded", "Soft square", "Square", "Leaf", "Pointed", "Thin line", "Segments",
            "Dots", "Outline", "Notched"};
    /** Shapes whose panel and ringer button stay round (the drawn bar has its own look). */
    private static final int SHAPE_POINTED = 5, SHAPE_THIN = 6, SHAPE_SEGMENTS = 7, SHAPE_DOTS = 8, SHAPE_OUTLINE = 9, SHAPE_NOTCHED = 10;
    public static final String[] FILLS = {"Default (light)", "Wallpaper colour", "RedMagic red", "Blue", "Green", "Purple", "Orange", "Pink",
            "Teal", "Gold", "Sunset gradient", "Ocean gradient", "RedMagic gradient", "Aurora gradient"};
    private static final int[][] FILL_RGB = {null, null, {0xFFE8343B}, {0xFF3E8BFF}, {0xFF34C759}, {0xFF9B6BFF}, {0xFFFF9F0A}, {0xFFFF5C93},
            {0xFF2EC4B6}, {0xFFE6B84C}, {0xFFFF9F0A, 0xFFFF2D55}, {0xFF2EC4B6, 0xFF3E5BFF}, {0xFFFF5A3C, 0xFFC8102E}, {0xFF34C759, 0xFF9B6BFF}};
    public static final String[] TRACKS = {"Default (glass)", "Clear", "Dim", "Solid", "Tinted"};
    public static final String[] ICONS = {"Default (One UI inside, RedMagic under)", "Under the bar", "Inside the bar"};

    public static final String[] PWR_SIZES = {"One UI (72 dp)", "Stock (60 dp)", "Medium (66 dp)", "Large (80 dp)", "Huge (88 dp)"};
    private static final int[] PWR_SIZE_DP = {72, 60, 66, 80, 88};
    public static final String[] PWR_SHAPES = {"Circle (One UI)", "Squircle", "Rounded square", "Square"};
    public static final String[] PWR_COLOR_SETS = {"One UI (red, green, grey)", "Wallpaper colour", "RedMagic red", "All grey", "Glass", "White"};
    public static final String[] PWR_LABEL_STYLES = {"Bold (One UI)", "Normal", "Large bold"};

    // ============================================================================================ the chosen look

    /** Where the values come from (the module settings in System UI, the live or previewed settings in the app). */
    public interface Src { int choice(String key); boolean on(String key); }

    public static final class Look {
        public int width, height, shape, fill, track, icon, pwrSize, pwrShape, pwrColors, pwrLabels;
        public boolean level;
        /** The One UI 8 panel is on (else the options restyle RedMagic's own bar). */
        public boolean oneUi;
    }

    private static int clamp(int v, int n) { return v < 0 || v >= n ? 0 : v; }

    public static Look look(Src s) {
        Look l = new Look();
        l.width = clamp(s.choice(VOL_WIDTH), WIDTHS.length);
        l.height = clamp(s.choice(VOL_HEIGHT), HEIGHTS.length);
        l.shape = clamp(s.choice(VOL_SHAPE), SHAPES.length);
        l.fill = clamp(s.choice(VOL_FILL), FILLS.length);
        l.track = clamp(s.choice(VOL_TRACK), TRACKS.length);
        l.icon = clamp(s.choice(VOL_ICON), ICONS.length);
        l.level = s.on(VOL_LEVEL);
        l.oneUi = s.on(VOL_ONEUI);
        l.pwrSize = clamp(s.choice(PWR_SIZE), PWR_SIZES.length);
        l.pwrShape = clamp(s.choice(PWR_SHAPE), PWR_SHAPES.length);
        l.pwrColors = clamp(s.choice(PWR_COLORS), PWR_COLOR_SETS.length);
        l.pwrLabels = clamp(s.choice(PWR_LABELS), PWR_LABEL_STYLES.length);
        return l;
    }

    /** Thickness of the bar (RedMagic's own bar is 20 dp). */
    public static int thickDp(Look l) { return l.width == 0 ? (l.oneUi ? 40 : 20) : WIDTH_DP[l.width]; }
    /** Width of the drawn panel around it: One UI 2 dp wider than the bar, RedMagic's own 56 dp column. */
    public static int columnDp(Look l) { return l.oneUi ? thickDp(l) + 4 : 56; }
    /** How much taller than stock the panel gets. */
    public static int extraDp(Look l) { return l.height == 0 ? (l.oneUi ? 80 : 0) : HEIGHT_DP[l.height]; }
    /** The speaker icon sits in the circle at the bottom of the bar (needs a bar at least 24 dp thick). */
    public static boolean iconInside(Look l) { return (l.icon == 0 ? l.oneUi : l.icon == 2) && thickDp(l) >= 24; }
    /** With the One UI panel off: an option changes how RedMagic's own bar is drawn (otherwise RedMagic draws it). */
    public static boolean stockDraw(Look l) { return !l.oneUi && (l.width != 0 || l.shape != 0 || l.fill != 0 || l.track != 0 || l.level || l.icon == 2); }
    /** The bar is drawn by the module (One UI panel, or a restyled RedMagic bar). */
    public static boolean custom(Look l) { return l.oneUi || stockDraw(l); }
    /** The panel's size changes (One UI panel, a restyled RedMagic bar, or a taller RedMagic panel). */
    public static boolean customLayout(Look l) { return custom(l) || extraDp(l) > 0; }

    // ============================================================================================ colours

    /** The wallpaper (Monet) accent: light in dark mode, deeper in light mode. */
    public static int accent(Context ctx, boolean dark) {
        try { return ctx.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_500); } catch (Throwable t) { return 0xFF3E8BFF; }
    }

    /** Fill colours {start (bottom), end (top)}; equal for a plain colour. */
    public static int[] fill(Context ctx, Look l, boolean dark) {
        if (l.fill == 1) { int a = accent(ctx, dark); return new int[]{a, a}; }
        int[] rgb = FILL_RGB[l.fill];
        if (rgb == null) { int f = dark ? (l.oneUi ? 0xFFEDEDF0 : 0xFFF4F4F6) : 0xFFFFFFFF; return new int[]{f, f}; }
        return rgb.length == 1 ? new int[]{rgb[0], rgb[0]} : rgb.clone();
    }

    public static int track(Look l, int[] fill, boolean dark) {
        switch (l.track) {
            case 1: return 0;
            case 2: return dark ? 0x59000000 : 0x33000000;
            case 3: return dark ? 0xFF2C2C30 : 0xFFE4E4EA;
            case 4: return (fill[1] & 0x00FFFFFF) | 0x4D000000;
            default: return l.oneUi ? (dark ? 0x40FFFFFF : 0x26000000) : (dark ? 0x4DFFFFFF : 0x33000000);
        }
    }

    private static double lum(int c) {
        return (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0;
    }

    /** Icon / text colour that reads over a colour. */
    public static int over(int color) { return lum(color) > 0.55 ? 0xFF2C2C2E : 0xFFFFFFFF; }

    // ============================================================================================ shapes

    /** Corner radii of the bar, in its own unrotated coordinates (Leaf: what you see top-left and bottom-right). */
    public static float[] barRadii(Look l, float thick, float d) {
        float half = thick / 2f, r;
        switch (l.shape) {
            case 1: r = Math.min(16 * d, half); break;
            case 2: r = Math.min(10 * d, half); break;
            case 3: r = Math.min(4 * d, half); break;
            case 4: { float s = Math.min(6 * d, half); return new float[]{s, s, half, half, s, s, half, half}; }
            case SHAPE_NOTCHED: r = Math.min(12 * d, half); break;
            default: r = half;
        }
        return new float[]{r, r, r, r, r, r, r, r};
    }

    /** Corner radii of the panel around a bar (and of the ringer button), as you see it; null = leave stock (pill). */
    public static float[] panelRadii(Look l, float width, float d) {
        if (l.shape == 0 || (l.shape >= SHAPE_POINTED && l.shape != SHAPE_NOTCHED)) return null;
        if (l.shape == SHAPE_NOTCHED) { float r = Math.min(14 * d, width / 2f); return new float[]{r, r, r, r, r, r, r, r}; }
        float half = width / 2f, r;
        switch (l.shape) {
            case 1: r = Math.min(18 * d, half); break;
            case 2: r = Math.min(12 * d, half); break;
            case 3: r = Math.min(6 * d, half); break;
            default: { float s = Math.min(8 * d, half); return new float[]{half, half, s, s, half, half, s, s}; }
        }
        return new float[]{r, r, r, r, r, r, r, r};
    }

    // ============================================================================================ drawing

    /**
     * The bar: track, fill (a circle at 0 % growing to the whole bar at 100 %, like Samsung's) and, if wanted, the level
     * number at the far end, upright on screen. {@code t} is horizontal; the caller's canvas carries the 270-degree turn.
     * Returns the fill length.
     */
    public static float drawBar(Canvas c, RectF t, float fraction, Look l, int[] fill, boolean dark, boolean enabled, float d) {
        float thick = t.height();
        float[] radii = barRadii(l, thick, d);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int trackColor = track(l, fill, dark);
        float f = Float.isFinite(fraction) ? Math.max(0f, Math.min(1f, fraction)) : 0f;
        float len = com.hammad.redmagictweaks.xposed.QsArt.oneUi8FillLength(t, f);
        Path path = new Path();
        switch (l.shape) {
            case SHAPE_POINTED: {
                if (trackColor != 0) { pointed(path, t); p.setColor(trackColor); c.drawPath(path, p); }
                path.reset(); pointed(path, new RectF(t.left, t.top, t.left + len, t.bottom));
                fillPaint(p, t, fill); c.drawPath(path, p);
                break;
            }
            case SHAPE_THIN: {
                // a thin line with a knob at the level; the circle at the start holds the speaker icon
                float lt = Math.max(4 * d, thick * 0.22f), cy = t.centerY(), start = t.left + thick / 2f, knob = Math.max(start, t.left + len - thick / 2f);
                if (trackColor != 0) { p.setColor(trackColor); c.drawRoundRect(new RectF(start, cy - lt / 2, t.right - thick * 0.25f, cy + lt / 2), lt / 2, lt / 2, p); }
                fillPaint(p, t, fill);
                c.drawRoundRect(new RectF(start, cy - lt / 2, knob, cy + lt / 2), lt / 2, lt / 2, p);
                c.drawCircle(start, cy, thick / 2f, p);
                c.drawCircle(knob, cy, thick * 0.3f, p);
                break;
            }
            case SHAPE_SEGMENTS: case SHAPE_DOTS: {
                // the start circle (icon), then blocks or dots that light up to the level
                float cy = t.centerY(), gap = Math.max(3 * d, thick * 0.1f);
                fillPaint(p, t, fill);
                c.drawCircle(t.left + thick / 2f, cy, thick / 2f, p);
                float from = t.left + thick + gap, room = t.right - from;
                boolean dots = l.shape == SHAPE_DOTS;
                float step = dots ? thick * 0.55f : thick * 0.6f;
                int n = Math.max(3, (int) ((room + gap) / step));
                float seg = dots ? 0 : (room - gap * (n - 1)) / n;
                int lit = Math.round(f * n);
                int off = trackColor != 0 ? trackColor : (dark ? 0x40FFFFFF : 0x26000000);
                for (int i = 0; i < n; i++) {
                    if (i < lit) fillPaint(p, t, fill); else { p.setShader(null); p.setColor(off); }
                    if (dots) {
                        float cx = from + (room / n) * (i + 0.5f);
                        c.drawCircle(cx, cy, thick * 0.17f, p);
                    } else {
                        float x = from + i * (seg + gap), hh = thick * 0.36f, r = Math.min(4 * d, Math.min(seg, hh * 2) / 2f);
                        c.drawRoundRect(new RectF(x, cy - hh, x + seg, cy + hh), r, r, p);
                    }
                }
                break;
            }
            case SHAPE_OUTLINE: {
                float sw = Math.max(2 * d, thick * 0.06f), half = thick / 2f;
                Paint o = new Paint(Paint.ANTI_ALIAS_FLAG); o.setStyle(Paint.Style.STROKE); o.setStrokeWidth(sw);
                o.setColor(trackColor != 0 ? (trackColor | 0x80000000) : (dark ? 0x99FFFFFF : 0x80000000));
                RectF in = new RectF(t); in.inset(sw / 2f, sw / 2f);
                c.drawRoundRect(in, half, half, o);
                RectF fr = new RectF(t.left, t.top, t.left + len, t.bottom); fr.inset(sw * 1.75f, sw * 1.75f);
                float fh = fr.height() / 2f;
                fillPaint(p, t, fill); c.drawRoundRect(fr, fh, fh, p);
                break;
            }
            default: {
                if (trackColor != 0) {
                    path.addRoundRect(t, radii, Path.Direction.CW);
                    p.setColor(trackColor);
                    c.drawPath(path, p);
                }
                path.reset();
                path.addRoundRect(new RectF(t.left, t.top, t.left + len, t.bottom), radii, Path.Direction.CW);
                fillPaint(p, t, fill);
                c.drawPath(path, p);
                if (l.shape == SHAPE_NOTCHED) {
                    // notches every tenth of the way, cut through track and fill
                    Paint n = new Paint(Paint.ANTI_ALIAS_FLAG); n.setColor(dark ? 0x59000000 : 0x40FFFFFF);
                    float w = Math.max(1.5f * d, thick * 0.04f);
                    for (int i = 1; i < 10; i++) {
                        float x = t.left + thick + (t.width() - thick) * i / 10f;
                        c.drawRect(x - w / 2, t.top + thick * 0.3f, x + w / 2, t.bottom - thick * 0.3f, n);
                    }
                }
            }
        }
        p.setShader(null);
        if (!l.oneUi && l.shape != SHAPE_THIN && l.shape != SHAPE_SEGMENTS && l.shape != SHAPE_DOTS) {
            // RedMagic's bar: a dot at the level
            Paint k = new Paint(Paint.ANTI_ALIAS_FLAG);
            k.setColor((over(fill[1]) & 0x00FFFFFF) | 0xA6000000);
            c.drawCircle(Math.max(t.left + thick / 2f, t.left + len - thick / 2f), t.centerY(), thick * 0.26f, k);
        }
        if (!enabled) {
            path.reset();
            path.addRoundRect(t, thick / 2f, thick / 2f, Path.Direction.CW);
            p.setColor(dark ? 0x66000000 : 0x55FFFFFF);
            c.drawPath(path, p);
        }
        if (l.level) {
            float cx = t.right - thick / 2f, cy = t.centerY();
            boolean onFill = len >= t.width() - thick * 0.75f;
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            tp.setTextSize(Math.max(9 * d, thick * 0.32f));
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setColor(onFill ? over(fill[1]) : dark ? 0xE6FFFFFF : 0xCC1C1C1E);
            int save = c.save();
            c.rotate(90f, cx, cy);
            Paint.FontMetrics fm = tp.getFontMetrics();
            c.drawText(Math.round(f * 100) + "", cx, cy - (fm.ascent + fm.descent) / 2f, tp);
            c.restoreToCount(save);
        }
        return len;
    }

    /** Fill colour or gradient (bottom to top of the whole bar); the paint stays opaque so a gradient is not faded. */
    private static void fillPaint(Paint p, RectF t, int[] fill) {
        if (fill[0] != fill[1]) { p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(t.left, 0, t.right, 0, fill[0], fill[1], Shader.TileMode.CLAMP)); }
        else { p.setShader(null); p.setColor(fill[0]); }
    }

    /** A bar with pointed ends (a long hexagon), slightly softened. */
    private static void pointed(Path path, RectF r) {
        float a = Math.min(r.height() / 2f, r.width() / 2f), cy = r.centerY();
        path.moveTo(r.left, cy);
        path.lineTo(r.left + a, r.top); path.lineTo(r.right - a, r.top);
        path.lineTo(r.right, cy);
        path.lineTo(r.right - a, r.bottom); path.lineTo(r.left + a, r.bottom);
        path.close();
    }

    /**
     * A small picture of the panel for an option card ({@code w} x {@code h} px): the lower part of the bar (where the
     * icon and the fill start), or the whole panel at a fixed scale when {@code whole} (the height option), so cards of
     * different heights compare.
     */
    public static void paintCard(Canvas c, Context ctx, float w, float h, float d, Look l, boolean whole) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF26304F, 0xFF4A3B6E, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        float pillH = 248 + extraDp(l);
        float scale, top;
        if (whole) { scale = (h - 10 * d) / (248f + 160f + 6f + 56f); top = 5 * d / scale; }
        else { scale = d * 0.5f; top = (h / scale) - pillH - 4; }
        int save = c.save();
        c.scale(scale, scale);
        paintPanel(c, ctx, (w / scale) / 2f, top, l, whole ? 0.3f : 0.12f, true, scale);
        c.restoreToCount(save);
    }

    /** The collapsed panel (blurred column, bar, icon, expand caret, ringer button) in dp units, its top at {@code top}. */
    public static void paintPanel(Canvas c, Context ctx, float cx, float top, Look l, float fraction, boolean dark, float pxPerDp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float col = columnDp(l), thick = thickDp(l), extra = extraDp(l);
        float pillH = 248 + extra;
        RectF pill = new RectF(cx - col / 2, top, cx + col / 2, top + pillH);
        p.setColor(0xB3303036);
        float[] pr = panelRadii(l, col, 1f);
        Path path = new Path();
        if (pr == null) path.addRoundRect(pill, col / 2, col / 2, Path.Direction.CW); else path.addRoundRect(pill, pr, Path.Direction.CW);
        c.drawPath(path, p);
        float ringTop = pill.bottom + 6 + Math.max(0, (56 - col) / 2f);   // RedMagic's 56 dp button, drawn as wide as the panel
        RectF ring = new RectF(cx - col / 2, ringTop, cx + col / 2, ringTop + col);
        path.reset();
        if (pr == null) path.addOval(ring, Path.Direction.CW); else path.addRoundRect(ring, pr, Path.Direction.CW);
        c.drawPath(path, p);
        bell(c, cx, ring.centerY(), 20, 0xFFFFFFFF);
        // expand caret
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG); s.setColor(0xFFFFFFFF); s.setStyle(Paint.Style.STROKE); s.setStrokeWidth(2.4f);
        s.setStrokeCap(Paint.Cap.ROUND); s.setStrokeJoin(Paint.Join.ROUND);
        Path ch = new Path(); ch.moveTo(cx + 4, top + 21); ch.lineTo(cx - 3, top + 28); ch.lineTo(cx + 4, top + 35);
        c.drawPath(ch, s);
        // the bar: the frame starts 37 dp down; the slider fills it but 3 dp at each end
        boolean inside = iconInside(l);
        float len = 165 + extra + (inside ? 34 : 0);
        float barTop = top + 37 + 3, barBottom = barTop + len;
        RectF bar = new RectF(cx - thick / 2, barTop, cx + thick / 2, barBottom);
        int save = c.save();
        c.rotate(-90f, bar.centerX(), bar.centerY());
        RectF hb = new RectF(bar.centerX() - bar.height() / 2, bar.centerY() - bar.width() / 2, bar.centerX() + bar.height() / 2, bar.centerY() + bar.width() / 2);
        int[] fill = fill(ctx, l, dark);
        drawBar(c, hb, fraction, l, fill, dark, true, 1f);
        c.restoreToCount(save);
        if (inside) speaker(c, cx, barBottom - thick / 2, Math.min(22, thick * 0.55f), over(fill[0]));
        else speaker(c, cx, top + 37 + 171 + extra + 12, 22, 0xFFFFFFFF);
    }

    /** Speaker glyph. */
    public static void speaker(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color);
        float u = size / 24f;
        Path b = new Path();
        b.moveTo(cx - 9 * u, cy - 3.5f * u); b.lineTo(cx - 5 * u, cy - 3.5f * u); b.lineTo(cx, cy - 8 * u); b.lineTo(cx, cy + 8 * u);
        b.lineTo(cx - 5 * u, cy + 3.5f * u); b.lineTo(cx - 9 * u, cy + 3.5f * u); b.close();
        c.drawPath(b, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.8f * u); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(new RectF(cx - 2 * u, cy - 4.5f * u, cx + 5 * u, cy + 4.5f * u), -55, 110, false, p);
        c.drawArc(new RectF(cx - 2 * u, cy - 8.5f * u, cx + 9 * u, cy + 8.5f * u), -55, 110, false, p);
    }

    private static void bell(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color);
        float u = size / 24f;
        Path b = new Path();
        b.moveTo(cx - 7 * u, cy + 5 * u);
        b.cubicTo(cx - 7 * u, cy - 9 * u, cx + 7 * u, cy - 9 * u, cx + 7 * u, cy + 5 * u);
        b.lineTo(cx + 9 * u, cy + 7 * u); b.lineTo(cx - 9 * u, cy + 7 * u); b.close();
        c.drawPath(b, p);
        c.drawCircle(cx, cy + 9.5f * u, 2 * u, p);
    }

    // ============================================================================================ power menu

    public static int pwrSizeDp(Look l) { return PWR_SIZE_DP[l.pwrSize]; }

    /** Corner radius of a power-menu button of {@code size} px; negative = a circle. */
    public static float pwrCorner(Look l, float size) {
        switch (l.pwrShape) {
            case 1: return size * 0.38f;
            case 2: return size * 0.24f;
            case 3: return size * 0.10f;
            default: return -1f;
        }
    }

    /** {background, icon, outline (0 = none)} of the button with resource id name {@code id}. */
    public static int[] pwrColors(Context ctx, Look l, String id) {
        switch (l.pwrColors) {
            case 1: { int a = accent(ctx, true); return new int[]{a, over(a), 0}; }
            case 2: return new int[]{0xFFE8343B, 0xFFFFFFFF, 0};
            case 3: return new int[]{0xFF55555C, 0xFFFFFFFF, 0};
            case 4: return new int[]{0x33FFFFFF, 0xFFFFFFFF, 0x66FFFFFF};
            case 5: return new int[]{0xFFF2F2F4, 0xFF1C1C1E, 0};
            default: {
                int bg = "power".equals(id) ? 0xFFE5484D : "reset".equals(id) ? 0xFF2FA866
                        : id != null && (id.contains("emergency") || id.contains("sos")) ? 0xFFF2711C : 0xFF55555C;
                return new int[]{bg, 0xFFFFFFFF, 0};
            }
        }
    }

    public static float pwrLabelSp(Look l) { return l.pwrLabels == 1 ? 13f : l.pwrLabels == 2 ? 16f : 14f; }
    public static boolean pwrLabelBold(Look l) { return l.pwrLabels != 1; }
    public static int pwrLabelColor(Look l) { return l.pwrLabels == 1 ? 0xE6FFFFFF : 0xFFFFFFFF; }

    /** Draws a power-menu button (background only) in {@code b}. */
    public static void pwrButton(Canvas c, RectF b, Look l, int[] colors) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colors[0]);
        float r = pwrCorner(l, b.width());
        if (r < 0) c.drawOval(b, p); else c.drawRoundRect(b, r, r, p);
        if (colors[2] != 0) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, b.width() / 60f)); p.setColor(colors[2]);
            if (r < 0) c.drawOval(b, p); else c.drawRoundRect(b, r, r, p);
        }
    }
}
