package com.hammad.redmagictweaks;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.util.Log;

import com.hammad.redmagictweaks.xposed.CcGlyph;
import com.hammad.redmagictweaks.xposed.CosmeticsArt;
import com.hammad.redmagictweaks.xposed.QsArt;
import com.hammad.redmagictweaks.xposed.SurfaceArt;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phone-sized pictures of what the System UI tweaks look like: lock screen, notifications page (with the classic Quick
 * Settings panel when the split shade is off), Control Center, volume pop-up and power menu, plus the status bar strip.
 * Everything is drawn from a {@link Looks.V} (live settings, a preset laid over them, a saved layout, or one choice the
 * user is trying), in phone dp (412 x 912, the NX789J screen) and scaled into the card. Schematic, not a screenshot:
 * shapes, colours, positions, fonts and sizes follow the same numbers the hooks use.
 */
public final class LookPreview {
    private LookPreview() {}

    private static final String TAG = "RMTweaksPreview";
    public static final float SW = 412f, SH = 912f;
    public static final String LOCK = "lock", SHADE = "shade", CC = "cc", VOLUME = "volume", POWER = "power", BAR = "bar", RECENTS = "recents";
    /** Every screen, in the order the enlarged view shows them. */
    public static final String[] ALL = {LOCK, SHADE, CC, VOLUME, POWER, RECENTS};

    /** Keys the painters have read (a choice of one of these changes the picture). */
    private static final Set<String> USED = Collections.synchronizedSet(new HashSet<>());
    public static boolean uses(String key) { return USED.contains(key); }

    /** Screens shown at the top of a settings section; null = no preview there. */
    public static String[] screensFor(String section) {
        if (section == null) return null;
        switch (section) {
            case "qs": case "quickpanel": return new String[]{CC, SHADE};
            case "surface": return new String[]{SHADE, VOLUME, POWER};
            case "notifications": return new String[]{SHADE};
            case "volume": return new String[]{VOLUME, POWER};
            case "lockscreen": return new String[]{LOCK};
            case "presets": case "looks": return new String[]{LOCK, SHADE, CC};
            case "statusbar": case "sbclock": case "sbicons": case "cosmetics": return new String[]{BAR};
            case "recents": case "home": return new String[]{RECENTS};
            default: return null;
        }
    }

    public static String title(String screen) {
        switch (screen) {
            case LOCK: return "Lock screen";
            case SHADE: return "Notifications";
            case CC: return "Control Center";
            case VOLUME: return "Volume keys";
            case POWER: return "Power menu";
            case RECENTS: return "Recent apps";
            default: return "Status bar";
        }
    }

    // ============================================================================================ drawables

    /** The header picture of a section, {@code widthDp} wide; null when the section has no preview. */
    public static Drawable section(Context ctx, String sectionId, Looks.V v, int widthDp) {
        String[] s = screensFor(sectionId);
        if (s == null) return null;
        return s.length == 1 && BAR.equals(s[0]) ? strip(ctx, v, widthDp) : row(ctx, v, s, widthDp);
    }

    /** Phones side by side. */
    public static Drawable row(Context ctx, Looks.V v, final String[] screens, int widthDp) {
        final int n = screens.length;
        final float gapDp = 10f;
        float phoneW = Math.min(n == 1 ? 170f : 150f, (widthDp - gapDp * (n - 1)) / n);
        int totalW = Math.round(phoneW * n + gapDp * (n - 1));
        int h = Math.round(phoneW * SH / SW);
        final R r = new R(ctx, v);
        return new QsFeatures.Sketch(ctx, totalW, h, (c, w, hh, d) -> {
            float gap = gapDp * d;
            float pw = (w - gap * (n - 1)) / n, ph = pw * SH / SW;
            for (int i = 0; i < n; i++) {
                int save = c.save();
                c.translate(i * (pw + gap), Math.max(0, (hh - ph) / 2));
                phoneFrame(c, r, screens[i], pw, ph, d);
                c.restoreToCount(save);
            }
        });
    }

    /** One phone. */
    public static Drawable phone(Context ctx, Looks.V v, String screen, int widthDp) {
        return row(ctx, v, new String[]{screen}, widthDp);
    }

    /** The status bar on a dark and on a light wallpaper, the width of the card. */
    public static Drawable strip(Context ctx, Looks.V v, int widthDp) {
        final R r = new R(ctx, v);
        final float barH = 36f, gapDp = 8f;
        int h = Math.round(barH * 2 * widthDp / SW + gapDp);
        return new QsFeatures.Sketch(ctx, widthDp, h, (c, w, hh, d) -> {
            float s = w / SW, one = barH * s;
            for (int i = 0; i < 2; i++) {
                boolean dark = i == 0;
                int save = c.save();
                c.translate(0, i * (one + gapDp * d));
                RectF b = new RectF(0, 0, w, one);
                Path clip = new Path(); clip.addRoundRect(b, 10 * d, 10 * d, Path.Direction.CW);
                c.clipPath(clip);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setShader(dark ? new LinearGradient(0, 0, w, 0, 0xFF1D2C52, 0xFF4A3F7E, Shader.TileMode.CLAMP)
                                 : new LinearGradient(0, 0, w, 0, 0xFFF3E7D3, 0xFFCFE3F2, Shader.TileMode.CLAMP));
                c.drawRect(b, p);
                c.scale(s, s);
                try { statusBar(c, r, 3, dark ? 0xFFFFFFFF : 0xFF1C1C1E, true); } catch (Throwable t) { Log.w(TAG, "strip", t); }
                c.restoreToCount(save);
            }
        });
    }

    private static void phoneFrame(Canvas c, R r, String screen, float pw, float ph, float d) {
        RectF b = new RectF(0, 0, pw, ph);
        float rad = pw * 0.075f;
        Path clip = new Path(); clip.addRoundRect(b, rad, rad, Path.Direction.CW);
        int save = c.save();
        c.clipPath(clip);
        c.scale(pw / SW, ph / SH);
        try {
            switch (screen) {
                case LOCK: paintLock(c, r); break;
                case SHADE: paintShade(c, r); break;
                case CC: paintCc(c, r); break;
                case VOLUME: paintVolume(c, r); break;
                case POWER: paintPower(c, r); break;
                case RECENTS: paintRecents(c, r); break;
                default: break;
            }
        } catch (Throwable t) { Log.w(TAG, "preview " + screen, t); }
        c.restoreToCount(save);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, d)); p.setColor(0x40FFFFFF);
        b.inset(0.5f, 0.5f);
        c.drawRoundRect(b, rad, rad, p);
    }

    // ============================================================================================ shared state

    /** Values, fonts and glyph cache of one picture. */
    static final class R {
        final Context ctx;
        final Looks.V v;
        final boolean h24;
        final boolean interFont;
        final int pack;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), tp = new Paint(Paint.ANTI_ALIAS_FLAG), gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<String, Drawable> glyphs = new HashMap<>();
        private final Map<Integer, Typeface> fonts = new HashMap<>();

        R(Context ctx, final Looks.V values) {
            this.ctx = ctx;
            this.v = new Looks.V() {   // remembers which keys the pictures depend on
                @Override public boolean on(String key) { USED.add(key); return values.on(key); }
                @Override public int choice(String key) { USED.add(key); return values.choice(key); }
            };
            h24 = DateFormat.is24HourFormat(ctx);
            interFont = v.choice(CosmeticsFeatures.COS_SYSUI_FONT) == CosmeticsFeatures.SYSUI_FONT_ONE_UI && hasAsset(ctx, "fonts/oneui_sans.ttf");
            pack = v.choice(IconPackFeatures.SBI_PACK);
        }

        /** The System UI font at {@code weight} (Inter when that option is on, else the system sans). */
        Typeface font(int weight) {
            Typeface t = fonts.get(weight);
            if (t != null) return t;
            if (interFont) {
                try { t = new Typeface.Builder(ctx.getAssets(), "fonts/oneui_sans.ttf").setFontVariationSettings("'wght' " + weight).setWeight(weight).build(); } catch (Throwable ignored) { }
            }
            if (t == null) t = Typeface.create(Typeface.SANS_SERIF, weight, false);
            fonts.put(weight, t);
            return t;
        }

        Drawable glyph(String key) {
            if (glyphs.containsKey(key)) return glyphs.get(key);
            Drawable d = null;
            int pk = pack > 0 && pack < IconPackFeatures.PACKS.length ? pack : IconPackFeatures.PACK_ONE_UI8;
            // the chosen pack, then One UI 8, then any pack that has the glyph (One UI 8 has no fan / lighting / game...)
            int[] order = new int[IconPackFeatures.PACKS.length + 1];
            order[0] = pk; order[1] = IconPackFeatures.PACK_ONE_UI8;
            for (int i = 1, k = 2; i < IconPackFeatures.PACKS.length && k < order.length; i++) if (i != IconPackFeatures.PACK_ONE_UI8) order[k++] = i;
            for (int p : order) {
                if (p <= 0) continue;
                int id = ctx.getResources().getIdentifier(IconPackFeatures.packArtName(p, key), "drawable", ctx.getPackageName());
                if (id == 0) continue;
                try { d = ctx.getDrawable(id); if (d != null) { d = d.mutate(); break; } } catch (Throwable ignored) { }
            }
            glyphs.put(key, d);
            return d;
        }
    }

    private static boolean hasAsset(Context ctx, String path) {
        try (java.io.InputStream in = ctx.getAssets().open(path)) { return in.read() >= 0; } catch (Throwable t) { return false; }
    }

    private static void text(Canvas c, R r, String s, float x, float y, float size, int weight, int color, Paint.Align align) {
        Paint p = r.tp;
        p.setTypeface(r.font(weight)); p.setTextSize(size); p.setColor(color); p.setTextAlign(align); p.setLetterSpacing(0f);
        c.drawText(s, x, y, p);
    }

    /** Text that wraps onto at most {@code lines} lines (ellipsised), centred on x. */
    private static void label(Canvas c, R r, String s, float cx, float y, float size, float maxW, int lines, int color) {
        Paint p = r.tp;
        p.setTypeface(r.font(400)); p.setTextSize(size); p.setColor(color); p.setTextAlign(Paint.Align.CENTER); p.setLetterSpacing(0f);
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        int drawn = 0;
        for (int i = 0; i < words.length; i++) {
            String next = line.length() == 0 ? words[i] : line + " " + words[i];
            if (p.measureText(next) <= maxW || line.length() == 0) { line = new StringBuilder(next); continue; }
            if (drawn == lines - 1) { line.append(" ").append(words[i]); for (int k = i + 1; k < words.length; k++) line.append(" ").append(words[k]); break; }
            c.drawText(line.toString(), cx, y + drawn * size * 1.2f, p);
            drawn++;
            line = new StringBuilder(words[i]);
        }
        if (line.length() > 0) {
            String t = line.toString();
            if (p.measureText(t) > maxW) { while (t.length() > 1 && p.measureText(t + "…") > maxW) t = t.substring(0, t.length() - 1); t = t + "…"; }
            c.drawText(t, cx, y + drawn * size * 1.2f, p);
        }
    }

    private static void glyph(Canvas c, R r, String key, float cx, float cy, float size, int color) {
        Drawable d = r.glyph(key);
        if (d == null) { r.gp.setStyle(Paint.Style.FILL); r.gp.setColor(color); c.drawCircle(cx, cy, size * 0.22f, r.gp); return; }
        d.setTint(color);
        int half = Math.round(size / 2f);
        d.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
        d.draw(c);
    }

    private static void ccGlyph(Canvas c, int type, float cx, float cy, float size, int color) {
        int save = c.save();
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(size / 24f, size / 24f);
        CcGlyph.paint(c, type, color, new Paint(Paint.ANTI_ALIAS_FLAG));
        c.restoreToCount(save);
    }

    private static Drawable wallCache;
    private static long wallAt;
    private static Drawable wallpaper(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - wallAt < 30_000) return wallCache;
        wallAt = now;
        try {
            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            Drawable d = wm.getDrawable(WallpaperManager.FLAG_LOCK);
            if (d == null) d = wm.getDrawable();
            wallCache = d;
        } catch (Throwable t) { wallCache = null; }
        return wallCache;
    }

    /** Wallpaper (or a gradient) over the whole phone, then {@code scrim}. */
    private static void background(Canvas c, R r, int scrim) {
        Drawable wall = wallpaper(r.ctx);
        Paint p = r.p;
        p.setShader(null);
        if (wall != null) {
            int iw = Math.max(1, wall.getIntrinsicWidth()), ih = Math.max(1, wall.getIntrinsicHeight());
            float s = Math.max(SW / iw, SH / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            int l = Math.round((SW - dw) / 2f), t = Math.round((SH - dh) / 2f);
            wall.setBounds(l, t, l + dw, t + dh);
            wall.draw(c);
        } else {
            p.setShader(new LinearGradient(0, 0, 0, SH, 0xFF1D2C52, 0xFF4A3F7E, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, SW, SH, p);
            p.setShader(null);
        }
        if (scrim != 0) { p.setColor(scrim); c.drawRect(0, 0, SW, SH, p); }
    }

    private static Calendar sampleTime() {
        Calendar t = Calendar.getInstance();
        t.set(2026, Calendar.OCTOBER, 21, 9, 18, 42);
        return t;
    }

    // ============================================================================================ status bar

    private static String clockText(R r) {
        Looks.V v = r.v;
        Calendar t = sampleTime();
        int fmt = v.choice(StatusBarFeatures.SB_CLOCK_FORMAT);
        String time;
        if (fmt > 0 && fmt < StatusBarFeatures.CLOCK_PATTERNS.length) time = new SimpleDateFormat(StatusBarFeatures.CLOCK_PATTERNS[fmt], Locale.getDefault()).format(t.getTime());
        else time = new SimpleDateFormat(r.h24 ? (v.on(StatusBarFeatures.SB_CLOCK_SECONDS) ? "HH:mm:ss" : "HH:mm") : (v.on(StatusBarFeatures.SB_CLOCK_SECONDS) ? "h:mm:ss" : "h:mm"), Locale.getDefault()).format(t.getTime());
        int di = v.choice(ClockFeatures.CLK_DATE);
        if (di > 0 && di < ClockFeatures.DATE_PATTERNS.length) {
            String date = new SimpleDateFormat(ClockFeatures.DATE_PATTERNS[di], Locale.getDefault()).format(t.getTime());
            int cs = v.choice(ClockFeatures.CLK_DATE_CASE);
            if (cs == 1) date = date.toUpperCase(Locale.getDefault()); else if (cs == 2) date = date.toLowerCase(Locale.getDefault());
            int si = v.choice(ClockFeatures.CLK_DATE_SEP);
            String sep = si >= 0 && si < ClockFeatures.DATE_SEPARATOR_TEXT.length ? ClockFeatures.DATE_SEPARATOR_TEXT[si] : " ";
            time = v.choice(ClockFeatures.CLK_DATE_POS) == 1 ? date + sep + time : time + sep + date;
        }
        return time;
    }

    private static float percent(String label) {
        try { return Integer.parseInt(label.replaceAll("[^0-9]", "")) / 100f; } catch (Throwable t) { return 1f; }
    }

    /** Status bar from {@code top}: clock (with every clock option) on the left, Bluetooth / signal / Wi-Fi / battery on the right. */
    private static void statusBar(Canvas c, R r, float top, int fg, boolean showClock) {
        Looks.V v = r.v;
        float base = top + 22f;
        float x = 18f;
        if (showClock && !v.on(ClockFeatures.CLK_HIDE)) {
            Paint p = r.tp;
            int fi = v.choice(ClockFeatures.CLK_FONT);
            Typeface tf;
            if (fi > 0 && fi < ClockFeatures.FONT_DEFS.length) {
                int style = 0;
                try { style = Integer.parseInt(ClockFeatures.FONT_DEFS[fi][2]); } catch (Throwable ignored) { }
                tf = Typeface.create(ClockFeatures.FONT_DEFS[fi][1], style);
            } else tf = r.font(500);
            int st = (v.on(ClockFeatures.CLK_BOLD) ? Typeface.BOLD : 0) | (v.on(ClockFeatures.CLK_ITALIC) ? Typeface.ITALIC : 0);
            if (st != 0) tf = Typeface.create(tf, st | tf.getStyle());
            int si = v.choice(ClockFeatures.CLK_SIZE);
            float size = 14.5f * (si > 0 && si < ClockFeatures.SIZES.length ? percent(ClockFeatures.SIZES[si]) : 1f);
            int ci = v.choice(ClockFeatures.CLK_COLOR);
            int color = ci > 0 && ci < ClockFeatures.COLOR_VALUES.length ? ClockFeatures.COLOR_VALUES[ci] : fg;
            float[] spacing = {0f, -0.05f, -0.02f, 0.02f, 0.05f, 0.10f, 0.15f};
            int sp = v.choice(ClockFeatures.CLK_SPACING);
            p.setTypeface(tf); p.setTextSize(size); p.setColor(color); p.setTextAlign(Paint.Align.LEFT);
            p.setLetterSpacing(sp > 0 && sp < spacing.length ? spacing[sp] : 0f);
            String s = clockText(r);
            c.drawText(s, x, base, p);
            x += p.measureText(s) + 10f;
            p.setLetterSpacing(0f);
        }
        if (showClock) {   // two notification icons after the clock
            glyph(c, r, "screenshot", x + 7, base - 5, 14, fg);
            r.gp.setColor(fg); r.gp.setStyle(Paint.Style.FILL);
            c.drawCircle(x + 26, base - 5, 5, r.gp);
        }
        // right side, from the edge inwards
        float right = SW - 16f;
        int bs = v.choice(StatusBarFeatures.SB_BATTERY_STYLE);
        if (bs != 5) {
            int style = v.choice(CosmeticsFeatures.COS_BATTERY_STYLE);
            float scale = 1f;
            int sc = v.choice(CosmeticsFeatures.COS_BATTERY_SCALE);
            if (sc > 0 && sc < IconPackFeatures.SCALE_PERCENT.length) scale = IconPackFeatures.SCALE_PERCENT[sc] / 100f;
            float bw = 25f * scale, bh = 12.5f * scale;
            RectF bb = new RectF(right - bw, base - 5 - bh / 2, right, base - 5 + bh / 2);
            boolean drawn = false;
            if (CosmeticsArt.batteryStyleValid(style)) {
                boolean circle = style == 2 || style == 3 || style == 4 || style == 9 || style == 10;
                if (circle) bb.left = right - bh * 1.3f;
                try {
                    drawn = CosmeticsArt.drawBattery(style, c, bb, 76, false, false,
                            v.on(CosmeticsFeatures.COS_BATTERY_PERCENT) || style == CosmeticsFeatures.BATTERY_ONE_UI8, fg, (fg & 0x00FFFFFF) | 0x99000000);
                } catch (Throwable ignored) { }
            }
            if (!drawn) bb = stockBattery(c, r, bb, fg, bs, v.on(CosmeticsFeatures.COS_BATTERY_STOCK_READABLE));
            right = bb.left - 7f;
        }
        int sc = v.choice(IconPackFeatures.SBI_SCALE);
        float is = 15f * (sc > 0 && sc < IconPackFeatures.SCALE_PERCENT.length ? IconPackFeatures.SCALE_PERCENT[sc] / 100f : 1f);
        if (!v.on(StatusBarFeatures.SB_HIDE_WIFI)) { glyph(c, r, "wifi_4", right - is / 2, base - 5, is, fg); right -= is + 4; }
        if (!v.on(StatusBarFeatures.SB_HIDE_MOBILE)) { glyph(c, r, "signal_4", right - is / 2, base - 5, is, fg); right -= is + 4; }
        if (!v.on(StatusBarFeatures.SB_HIDE_BLUETOOTH)) glyph(c, r, "bluetooth", right - is / 2, base - 5, is, fg);
    }

    /** RedMagic's pill battery with the number inside (style: StatusBarFeatures.SB_BATTERY_STYLE). Returns the drawn bounds. */
    private static RectF stockBattery(Canvas c, R r, RectF b, int fg, int style, boolean readable) {
        Paint p = r.gp;
        if (style == 4) {   // percent only
            r.tp.setTypeface(r.font(600)); r.tp.setTextSize(12f); r.tp.setColor(fg); r.tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText("76%", b.right, b.centerY() + 4.2f, r.tp);
            return new RectF(b.right - r.tp.measureText("76%"), b.top, b.right, b.bottom);
        }
        float rad = b.height() / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor((fg & 0x00FFFFFF) | 0x55000000);
        c.drawRoundRect(b, rad, rad, p);
        int save = c.save();
        c.clipRect(b.left, b.top, b.left + b.width() * 0.76f, b.bottom);
        p.setColor(fg);
        c.drawRoundRect(b, rad, rad, p);
        c.restoreToCount(save);
        if (style == 0 || style == 2) {
            r.tp.setTypeface(r.font(700)); r.tp.setTextSize(b.height() * 0.78f); r.tp.setTextAlign(Paint.Align.CENTER);
            boolean light = (fg & 0xFF) > 0x80;
            r.tp.setColor(readable ? (light ? 0xFF000000 : 0xFFFFFFFF) : ((fg & 0x00FFFFFF) | 0x66000000) ^ 0x00202020);
            c.drawText("76", b.centerX(), b.centerY() + b.height() * 0.28f, r.tp);
        } else if (style == 3) {
            r.tp.setTypeface(r.font(600)); r.tp.setTextSize(12f); r.tp.setColor(fg); r.tp.setTextAlign(Paint.Align.RIGHT);
            c.drawText("76%", b.left - 4, b.centerY() + 4.2f, r.tp);
            return new RectF(b.left - 4 - r.tp.measureText("76%"), b.top, b.right, b.bottom);
        }
        return b;
    }

    // ============================================================================================ tiles

    /** {bg, icon, label, secondary} for a tile in the chosen finish; state 2 = on, 1 = off. */
    private static int[] tileColors(R r, int state, boolean wide) {
        int style = r.v.choice(QsFeatures.QS_TILE_STYLE);
        if (style == QsArt.ONE_UI_TILE)
            return new int[]{QsArt.oneUiBackground(state, true), QsArt.oneUiIcon(state, true), QsArt.oneUiLabel(state, wide, true), QsArt.oneUiSecondaryLabel(state, true)};
        if (style == QsArt.ONE_UI8_TILE)
            return new int[]{QsArt.oneUi8Background(state, wide, true), QsArt.oneUi8Icon(state, true), QsArt.oneUi8Label(state, true), QsArt.oneUi8SecondaryLabel(state, true)};
        // RedMagic stock (also under the decorative finishes)
        return state == 2 ? new int[]{0xFFE9E9EE, 0xFF1C1C1E, wide ? 0xFF1C1C1E : 0xFFE6E6EA, wide ? 0x991C1C1E : 0x99FFFFFF}
                          : new int[]{0x2EFFFFFF, 0xFFFFFFFF, 0xFFE6E6EA, 0x99FFFFFF};
    }

    /** A tile background in the chosen shape and finish. */
    private static void tileShape(final Canvas c, R r, final RectF b, int color, boolean wide) {
        int shapeChoice = r.v.choice(wide ? QsFeatures.QS_SHAPE_LARGE : QsFeatures.QS_SHAPE_SMALL);
        float[] radii = QsArt.shapeCorners(QsArt.shapeId(shapeChoice), Math.round(b.width()), Math.round(b.height()));
        final GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        if (radii != null) { gd.setShape(GradientDrawable.RECTANGLE); gd.setCornerRadii(radii); }
        else if (wide) gd.setCornerRadius(Math.min(b.height() * 0.3f, 20f));
        else gd.setShape(GradientDrawable.OVAL);
        gd.setBounds(Math.round(b.left), Math.round(b.top), Math.round(b.right), Math.round(b.bottom));
        int style = r.v.choice(QsFeatures.QS_TILE_STYLE);
        boolean done = false;
        if (style >= 1 && style <= 7) { try { done = QsArt.drawTile(style, c, gd, 1f, () -> gd.draw(c)); } catch (Throwable ignored) { } }
        if (!done) gd.draw(c);
    }

    private static final Object[][] BIG = {
        {"Wi-Fi", "wifi_4", "Mine", 2}, {"Ring", "ring", "Sound mode", 2}, {"Bluetooth", "bluetooth", "On", 2}, {"Auto-rotate", "rotate", "Off", 1},
        {"Mobile data", "data", "On", 2}, {"Hotspot", "hotspot", "Off", 1},
    };
    private static final Object[][] SMALL = {
        {"Airplane mode", "airplane", 1}, {"Charge SEP", "battery_saver", 1}, {"Light Strip", "lighting", 1}, {"Light Play Mode", "game", 1},
        {"Power-saver", "battery_saver", 1}, {"Ultra PowerSaver", "battery_saver", 1}, {"Cooling fan", "fan", 1}, {"Flashlight", "flashlight", 1},
        {"Data", "data", 1}, {"Personal Hotspot", "hotspot", 1}, {"Record screen", "screenrecord", 1}, {"Device Center", "cast", 1},
        {"Location", "location", 2}, {"Game Space", "game", 1}, {"Screenshot", "screenshot", 1}, {"NFC", "nfc", 1},
        {"Sync", "sync", 1}, {"Dark mode", "dark", 1}, {"Eye comfort", "night", 1}, {"Refresh rate", "Hz", 2},
    };

    /** A wide pill: icon, title, subtitle. */
    private static void bigTile(Canvas c, R r, RectF b, Object[] t) {
        int state = (Integer) t[3];
        int[] col = tileColors(r, state, true);
        tileShape(c, r, b, col[0], true);
        float iconX = b.left + Math.min(28f, b.height() * 0.45f), cy = b.centerY();
        if (r.v.choice(QsFeatures.QS_TILE_STYLE) == QsArt.ONE_UI8_TILE) {
            r.gp.setStyle(Paint.Style.FILL); r.gp.setColor(QsArt.oneUi8IconCircle(state, true));
            c.drawCircle(iconX + 4, cy, b.height() * 0.32f, r.gp);
            iconX += 4;
        }
        glyph(c, r, (String) t[1], iconX, cy, 22, col[1]);
        float tx = iconX + 22;
        if (b.width() < 110) return;
        text(c, r, (String) t[0], tx, cy - 2, 15f, 600, col[2], Paint.Align.LEFT);
        text(c, r, (String) t[2], tx, cy + 15, 11.5f, 400, col[3], Paint.Align.LEFT);
    }

    /** Round tiles from {@code top} until {@code bottom}; returns where they ended. */
    private static float tileGrid(Canvas c, R r, float left, float right, float top, float bottom, int cols, int maxRows, boolean cc, int startAt) {
        Looks.V v = r.v;
        int labels = v.choice(QsFeatures.QS_LABEL_POS);
        int lines = v.choice(QsFeatures.QS_LABEL_LINES);
        int maxLines = lines > 0 && lines <= 3 ? lines : 2;
        int gap = cc ? Math.max(0, Math.min(3, v.choice(QsFeatures.QS_CC_TILE_GAP))) : 0;
        float cellW = (right - left) / cols;
        float d = Math.min(60f, cellW * 0.7f) - gap * 4f;
        float pitch = (labels == 2 ? d + 24 : d + 26 + maxLines * 14f) + gap * 4f;
        float y = top;
        int k = startAt;
        for (int row = 0; row < maxRows && y + (labels == 2 ? d : d + 30) <= bottom; row++) {
            float cy = y + (labels == 1 ? 18 + maxLines * 14f : 0) + d / 2;
            for (int col = 0; col < cols; col++, k++) {
                Object[] t = SMALL[k % SMALL.length];
                int state = (Integer) t[2];
                int[] colr = tileColors(r, state, false);
                float cx = left + cellW * (col + 0.5f);
                tileShape(c, r, new RectF(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2), colr[0], false);
                if ("Hz".equals(t[1])) {
                    r.tp.setTypeface(Typeface.create(r.font(700), Typeface.BOLD_ITALIC)); r.tp.setTextSize(d * 0.32f); r.tp.setColor(colr[1]); r.tp.setTextAlign(Paint.Align.CENTER);
                    c.drawText("Hz", cx, cy + d * 0.11f, r.tp);
                } else glyph(c, r, (String) t[1], cx, cy, d * 0.4f, colr[1]);
                if (labels == 0) label(c, r, (String) t[0], cx, cy + d / 2 + 18, 12f, cellW - 6, maxLines, colr[2]);
                else if (labels == 1) label(c, r, (String) t[0], cx, y + 14, 12f, cellW - 6, maxLines, colr[2]);
            }
            y += pitch;
        }
        return y;
    }

    private static int cols(int choice, int stock) { return choice > 0 && choice < QsFeatures.COLS.length ? choice + 1 : stock; }

    // ============================================================================================ sliders

    /**
     * Brightness (volume = false) or volume bar in its chosen style; {@code vertical} bars fill from the bottom. The
     * start icon (sun / speaker) sits in the fill.
     */
    private static void slider(final Canvas c, R r, RectF bar, boolean vertical, boolean volume, float fraction) {
        int style = r.v.choice(volume ? QsFeatures.QS_CC_VOLUME_STYLE : QsFeatures.QS_BRIGHTNESS_STYLE);
        int save = c.save();
        final RectF b;
        if (vertical) {   // draw it horizontally, rotated so the start is at the bottom
            c.rotate(-90f, bar.centerX(), bar.centerY());
            b = new RectF(bar.centerX() - bar.height() / 2, bar.centerY() - bar.width() / 2, bar.centerX() + bar.height() / 2, bar.centerY() + bar.width() / 2);
        } else b = new RectF(bar);
        float thick = b.height(), iconCx = b.left + thick / 2f, iconCy = b.centerY();
        boolean iconDark;
        if (style == QsArt.ONE_UI8_BRIGHTNESS) {
            QsArt.drawOneUi8Slider(c, b, fraction, true);
            iconDark = true;
        } else if (style == QsArt.ONE_UI_BRIGHTNESS) {
            Paint p = r.gp; p.setStyle(Paint.Style.FILL);
            p.setColor(QsArt.oneUiTrack(true)); c.drawRoundRect(b, thick / 2, thick / 2, p);
            int s2 = c.save(); c.clipRect(b.left, b.top, b.left + b.width() * fraction, b.bottom);
            p.setColor(QsArt.oneUiFill(true)); c.drawRoundRect(b, thick / 2, thick / 2, p); c.restoreToCount(s2);
            iconDark = b.width() * fraction > thick;
        } else {
            final float rad = Math.min(thick / 2f, 18f);
            final GradientDrawable track = new GradientDrawable(), fill = new GradientDrawable();
            track.setColor(0x3DFFFFFF); track.setCornerRadius(rad);
            fill.setColor(0xFFF4F4F6); fill.setCornerRadius(rad);
            track.setBounds(Math.round(b.left), Math.round(b.top), Math.round(b.right), Math.round(b.bottom));
            fill.setBounds(track.getBounds());
            boolean done = false;
            if (style >= 1 && style <= 3) { try { done = QsArt.drawBrightness(style, c, track, 1f, false, true, () -> track.draw(c)); } catch (Throwable ignored) { } }
            if (!done) track.draw(c);
            int s2 = c.save(); c.clipRect(b.left, b.top, b.left + b.width() * fraction, b.bottom);
            done = false;
            if (style >= 1 && style <= 3) { try { done = QsArt.drawBrightness(style, c, fill, 1f, true, true, () -> fill.draw(c)); } catch (Throwable ignored) { } }
            if (!done) fill.draw(c);
            c.restoreToCount(s2);
            iconDark = b.width() * fraction > thick;
        }
        int ic = iconDark ? QsArt.oneUiIconOverFill(true) : 0xFFE6E6EA;
        float isz = Math.min(24f, thick * 0.46f);
        if (!volume && !(r.v.on(QsFeatures.QS_BRIGHTNESS_NO_SUN) && !vertical && style != QsArt.ONE_UI8_BRIGHTNESS && style != QsArt.ONE_UI_BRIGHTNESS)) {
            sun(c, iconCx, iconCy, isz, ic);
        } else if (volume) {
            int s3 = c.save(); c.rotate(vertical ? 90f : 0f, iconCx, iconCy);
            ccGlyph(c, CcGlyph.SPEAKER, iconCx, iconCy, isz, ic);
            c.restoreToCount(s3);
        }
        c.restoreToCount(save);
    }

    private static void sun(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        float r = size * 0.2f;
        c.drawCircle(cx, cy, r, p);
        p.setStrokeWidth(Math.max(1.5f, size * 0.08f)); p.setStrokeCap(Paint.Cap.ROUND);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            c.drawLine((float) (cx + Math.cos(a) * r * 1.55), (float) (cy + Math.sin(a) * r * 1.55), (float) (cx + Math.cos(a) * r * 2.2), (float) (cy + Math.sin(a) * r * 2.2), p);
        }
    }

    private static void dots(Canvas c, float cx, float cy, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color);
        for (int i = -1; i <= 1; i++) c.drawCircle(cx, cy + i * 6.5f, 2.2f, p);
    }

    private static void gear(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size * 0.11f);
        c.drawCircle(cx, cy, size * 0.22f, p);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(size * 0.14f);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            c.drawLine((float) (cx + Math.cos(a) * size * 0.32), (float) (cy + Math.sin(a) * size * 0.32), (float) (cx + Math.cos(a) * size * 0.42), (float) (cy + Math.sin(a) * size * 0.42), p);
        }
    }

    private static void search(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size * 0.11f); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawCircle(cx - size * 0.06f, cy - size * 0.06f, size * 0.26f, p);
        c.drawLine(cx + size * 0.14f, cy + size * 0.14f, cx + size * 0.36f, cy + size * 0.36f, p);
    }

    private static void pencil(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size * 0.11f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        Path path = new Path();
        path.moveTo(cx - size * 0.32f, cy + size * 0.32f); path.lineTo(cx - size * 0.28f, cy + size * 0.12f); path.lineTo(cx + size * 0.18f, cy - size * 0.34f);
        path.lineTo(cx + size * 0.34f, cy - size * 0.18f); path.lineTo(cx - size * 0.12f, cy + size * 0.28f); path.close();
        c.drawPath(path, p);
    }

    // ============================================================================================ lock screen

    private static void paintLock(Canvas c, R r) {
        Looks.V v = r.v;
        background(c, r, 0x33000000);
        boolean oneUi = v.on(LockFeatures.ONEUI_CLOCK);
        boolean tiles = v.on(LockFeatures.ONEUI_TILES);
        boolean shortcuts = v.on(LockFeatures.ONEUI_SHORTCUTS);
        boolean lockIcon = !oneUi || v.on(LockFeatures.ONEUI_LOCK_ICON);
        int face = Math.max(0, Math.min(ClockFaces.COUNT - 1, v.choice(LockFeatures.ONEUI_FACE)));
        int sizeIdx = Math.max(0, Math.min(LockFeatures.ONEUI_SIZE_DP.length - 1, v.choice(LockFeatures.ONEUI_SIZE)));
        int pos = v.choice(LockFeatures.ONEUI_POSITION);
        statusBar(c, r, 0, 0xFFFFFFFF, false);
        text(c, r, "Carrier", 18, 22, 12.5f, 500, 0xFFFFFFFF, Paint.Align.LEFT);
        Paint p = r.gp;
        p.setColor(0xFFFFFFFF);
        if (lockIcon) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f);
            c.drawRoundRect(new RectF(SW / 2 - 7, 52, SW / 2 + 7, 64), 2.5f, 2.5f, p);
            c.drawArc(new RectF(SW / 2 - 5, 43, SW / 2 + 5, 56), 180, 180, false, p);
            p.setStyle(Paint.Style.FILL);
        }
        float y;
        if (oneUi) {
            ClockFaces.Spec s = new ClockFaces.Spec();
            s.density = 1f;
            s.digits = ClockFaces.previewDigits(r.ctx);
            s.h24 = r.h24;
            s.size = LockFeatures.ONEUI_SIZE_DP[sizeIdx];
            s.time = sampleTime();
            float top = pos == 1 ? 72 : pos == 2 ? 150 : pos == 3 ? 130 : 100;
            float fh = ClockFaces.height(face, s, SW);
            int save = c.save();
            c.translate(0, top);
            ClockFaces.draw(c, face, s, SW);
            c.restoreToCount(save);
            y = top + fh + 12;
            if (tiles) {
                y += 20;
                float tw = 62, gap = 12, x0 = (SW - (3 * tw + 2 * gap)) / 2f;
                String[] lab = {"76", "21", "Off"};
                for (int i = 0; i < 3; i++) {
                    RectF t = new RectF(x0 + i * (tw + gap), y, x0 + i * (tw + gap) + tw, y + tw);
                    p.setColor(0x47000000); c.drawRoundRect(t, 20, 20, p);
                    p.setColor(0xFFFFFFFF);
                    if (i == 0) {
                        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3);
                        c.drawArc(new RectF(t.left + 9, t.top + 9, t.right - 9, t.bottom - 9), 135, 270 * 0.76f, false, p);
                        p.setStyle(Paint.Style.FILL);
                    }
                    text(c, r, lab[i], t.centerX(), t.centerY() + (i == 2 ? 18 : 4), 11.5f, 500, 0xFFFFFFFF, Paint.Align.CENTER);
                }
                y += tw;
            }
        } else {
            text(c, r, r.h24 ? "09:18" : "9:18", SW / 2, 190, 64, 200, 0xFFFFFFFF, Paint.Align.CENTER);
            text(c, r, "Wed, October 21", SW / 2, 222, 15, 500, 0xFFFFFFFF, Paint.Align.CENTER);
            y = 236;
        }
        // first notification just below the clock (what the stack padding hooks do)
        y += 14;
        notificationCard(c, r, new RectF(14, y, SW - 14, y + 74), 0, true);
        if (shortcuts) {
            p.setColor(0x47000000);
            c.drawCircle(46, SH - 64, 26, p);
            c.drawCircle(SW - 46, SH - 64, 26, p);
            p.setColor(0xFFFFFFFF); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
            c.drawRoundRect(new RectF(38, SH - 74, 54, SH - 54), 3, 3, p);
            c.drawRoundRect(new RectF(SW - 57, SH - 72, SW - 35, SH - 56), 3, 3, p);
            c.drawCircle(SW - 46, SH - 64, 4.5f, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            p.setColor(0xCCFFFFFF);
            c.drawRoundRect(new RectF(SW / 2 - 60, SH - 18, SW / 2 + 60, SH - 13), 3, 3, p);
        }
    }

    // ============================================================================================ notifications page

    private static final String[][] NOTES = {
        {"Messages", "now", "Sara", "See you at 8!"},
        {"Gmail", "9:02", "Your order has shipped", "It arrives tomorrow"},
        {"WhatsApp", "8:47", "Family", "3 new messages"},
        {"Calendar", "8:30", "Team call", "10:00 – 10:30"},
    };
    private static final int[] NOTE_COLORS = {0xFF34A853, 0xFFEA4335, 0xFF25D366, 0xFF4285F4};

    /** One card with the card finish, corners and opacity applied. */
    private static void notificationCard(Canvas c, R r, RectF card, int i, boolean compact) {
        Looks.V v = r.v;
        int style = v.choice(SurfaceFeatures.NOTIF_STYLE);
        int corner = SurfaceArt.cornerDp(v.choice(SurfaceFeatures.NOTIF_CORNERS));
        int opacity = SurfaceArt.opacity(v.choice(SurfaceFeatures.NOTIF_OPACITY));
        float rad = corner > 0 ? corner : 20f;
        float[] radii = new float[8]; Arrays.fill(radii, rad);
        int layer = c.saveLayerAlpha(card, opacity == 0 ? 255 : opacity * 255 / 100);
        Paint p = r.gp; p.setStyle(Paint.Style.FILL);
        p.setColor(0xD92B2C31);
        c.drawRoundRect(card, rad, rad, p);
        if (style > 0) { try { SurfaceArt.drawCard(style, c, card, radii, 1f, true); } catch (Throwable ignored) { } }
        c.restoreToCount(layer);
        String[] n = NOTES[i % NOTES.length];
        p.setColor(NOTE_COLORS[i % NOTE_COLORS.length]);
        float ix = card.left + 26, iy = card.top + (compact ? card.height() / 2 : 30);
        c.drawCircle(ix, iy, compact ? 14 : 12, p);
        float tx = card.left + 50;
        if (compact) {
            text(c, r, n[2], tx, card.top + 32, 14.5f, 600, 0xFFFFFFFF, Paint.Align.LEFT);
            text(c, r, n[3], tx, card.top + 53, 13f, 400, 0xCCFFFFFF, Paint.Align.LEFT);
            text(c, r, n[1], card.right - 16, card.top + 32, 11.5f, 400, 0x99FFFFFF, Paint.Align.RIGHT);
            return;
        }
        text(c, r, n[0] + " · " + n[1], tx, card.top + 34, 12f, 500, 0xB3FFFFFF, Paint.Align.LEFT);
        text(c, r, n[2], card.left + 18, card.top + 64, 15f, 600, 0xFFFFFFFF, Paint.Align.LEFT);
        text(c, r, n[3], card.left + 18, card.top + 85, 13.5f, 400, 0xCCFFFFFF, Paint.Align.LEFT);
    }

    private static void paintShade(Canvas c, R r) {
        Looks.V v = r.v;
        background(c, r, 0xD9101114);
        statusBar(c, r, 0, 0xFFFFFFFF, true);
        float y;
        if (v.on(QsFeatures.QS_CC_ENABLE)) {
            text(c, r, r.h24 ? "09:18" : "9:18", 22, 102, 52, 300, 0xFFFFFFFF, Paint.Align.LEFT);
            text(c, r, "Wednesday, October 21", 24, 132, 15, 400, 0xCCFFFFFF, Paint.Align.LEFT);
            gear(c, SW - 70, 120, 22, 0xFFFFFFFF);
            dots(c, SW - 30, 120, 0xFFFFFFFF);
            y = 158;
        } else {
            y = classicQs(c, r, 50) + 14;
        }
        for (int i = 0; i < 4 && y + 104 <= SH - 30; i++) {
            notificationCard(c, r, new RectF(12, y, SW - 12, y + 100), i, false);
            y += 108;
        }
    }

    /** RedMagic's single pull-down panel (split shade off): big tiles, brightness, small tiles in the chosen layout. */
    private static float classicQs(Canvas c, R r, float top) {
        Looks.V v = r.v;
        int topLayout = v.choice(QsFeatures.QS_TOP_LAYOUT), bPos = v.choice(QsFeatures.QS_BRIGHTNESS_POS);
        int cols = cols(v.choice(QsFeatures.QS_COLS), 4);
        int rowsChoice = v.choice(QsFeatures.QS_ROWS);
        int rows = rowsChoice > 0 && rowsChoice < QsFeatures.ROWS.length ? rowsChoice : 2;
        float L = 16, Rr = SW - 16;
        float y = top;
        for (int k = 0; k < 3; k++) {
            int block = bPos == 1 ? new int[]{1, 0, 2}[k] : bPos == 2 ? new int[]{0, 2, 1}[k] : k;
            if (block == 0) {
                int tc = topLayout == 3 ? 1 : 2, tr = topLayout == 1 ? 1 : topLayout == 2 ? 3 : topLayout == 3 ? 4 : 2;
                float gw = 10, cw = (Rr - L - gw * (tc - 1)) / tc, ch = 60;
                for (int i = 0; i < tc * tr; i++) {
                    float x = L + (i % tc) * (cw + gw), ty = y + (i / tc) * (ch + 10);
                    bigTile(c, r, new RectF(x, ty, x + cw, ty + ch), BIG[i % BIG.length]);
                }
                y += tr * (ch + 10) + 4;
            } else if (block == 1) {
                slider(c, r, new RectF(L, y, Rr, y + 44), false, false, 0.55f);
                y += 58;
            } else {
                y = tileGrid(c, r, L, Rr, y, SH * 0.62f, cols, rows, false, 0) + 2;
            }
        }
        Paint p = r.gp; p.setColor(0x66FFFFFF);
        c.drawRoundRect(new RectF(SW / 2 - 18, y, SW / 2 + 18, y + 4), 2, 2, p);
        return y + 6;
    }

    // ============================================================================================ Control Center

    private static void paintCc(Canvas c, R r) {
        Looks.V v = r.v;
        background(c, r, 0xD9101114);
        if (v.choice(QsFeatures.QS_CC_LAYOUT) == 1) { paintCcOneUi(c, r); return; }
        statusBar(c, r, 0, 0xFFFFFFFF, false);
        text(c, r, r.h24 ? "09:18" : "9:18", 24, 106, 62, 300, 0xFFFFFFFF, Paint.Align.LEFT);
        if (!v.on(QsFeatures.QS_CC_HIDE_DATE)) text(c, r, "Wednesday, October 21", 26, 138, 16, 400, 0xE6FFFFFF, Paint.Align.LEFT);
        float hx = SW - 30;
        dots(c, hx, 126, 0xFFFFFFFF);
        hx -= 46;
        if (!v.on(QsFeatures.QS_CC_HIDE_SETTINGS)) { gear(c, hx, 126, 24, 0xFFFFFFFF); hx -= 46; }
        if (!v.on(QsFeatures.QS_CC_HIDE_SEARCH)) search(c, hx, 126, 24, 0xFFFFFFFF);

        int pos = v.choice(QsFeatures.QS_CC_SLIDER_POS);
        float barH = QsFeatures.sliderSizeDp(v.choice(QsFeatures.QS_CC_SLIDER_SIZE));
        float L = 30, Rr = SW - 30, colGap = 22, colW = (Rr - L - colGap) / 2, rowH = 62, rowGap = 18;
        int cols = cols(v.choice(QsFeatures.QS_CC_COLS), 4);
        float y = 168;
        if (pos == 0 || pos == 1) {
            boolean mirror = pos == 1;
            float pillX = mirror ? L + colW + colGap : L, otherX = mirror ? L : L + colW + colGap;
            for (int i = 0; i < 3; i++) bigTile(c, r, new RectF(pillX, y + i * (rowH + rowGap), pillX + colW, y + i * (rowH + rowGap) + rowH), BIG[i]);
            float bw = (colW - colGap) / 2, bh = rowH * 2 + rowGap;
            slider(c, r, new RectF(otherX, y, otherX + bw, y + bh), true, false, 0.55f);
            slider(c, r, new RectF(otherX + bw + colGap, y, otherX + colW, y + bh), true, true, 0.35f);
            bigTile(c, r, new RectF(otherX, y + 2 * (rowH + rowGap), otherX + colW, y + 2 * (rowH + rowGap) + rowH), BIG[3]);
            y += 3 * rowH + 2 * rowGap + 26;
            tileGrid(c, r, L - 6, Rr + 6, y, SH - 24, cols, 9, true, 0);
            return;
        }
        if (pos == 3) { y = bars(c, r, L, Rr, y, barH); }
        for (int i = 0; i < 4; i++) {
            float x = L + (i % 2) * (colW + colGap), ty = y + (i / 2) * (rowH + rowGap);
            bigTile(c, r, new RectF(x, ty, x + colW, ty + rowH), BIG[i]);
        }
        y += 2 * rowH + rowGap + 20;
        if (pos == 2) y = bars(c, r, L, Rr, y, barH);
        if (pos == 4) {
            float bottom = SH - 24 - (barH * 2 + 12);
            tileGrid(c, r, L - 6, Rr + 6, y + 6, bottom - 8, cols, 9, true, 0);
            bars(c, r, L, Rr, bottom, barH);
        } else {
            tileGrid(c, r, L - 6, Rr + 6, y + 6, SH - 24, cols, 9, true, 0);
        }
    }

    private static float bars(Canvas c, R r, float L, float Rr, float y, float barH) {
        slider(c, r, new RectF(L, y, Rr, y + barH), false, false, 0.55f);
        slider(c, r, new RectF(L, y + barH + 12, Rr, y + 2 * barH + 12), false, true, 0.35f);
        return y + 2 * barH + 12 + 18;
    }

    /** The One UI 8 page (QsFeatures.QS_CC_LAYOUT = 1, xposed/CcOneUiHooks). */
    private static void paintCcOneUi(Canvas c, R r) {
        Looks.V v = r.v;
        float L = 16, Rr = SW - 16, gap = 12;
        float y;
        if (v.on(QsFeatures.QS_CC_ONEUI_BIG_CLOCK)) {
            statusBar(c, r, 0, 0xFFFFFFFF, false);
            text(c, r, r.h24 ? "09:18" : "9:18", 24, 100, 56, 300, 0xFFFFFFFF, Paint.Align.LEFT);
            text(c, r, "Wednesday, October 21", 26, 128, 15, 400, 0xE6FFFFFF, Paint.Align.LEFT);
            y = 148;
        } else {
            statusBar(c, r, 0, 0xFFFFFFFF, true);
            y = 52;
        }
        if (!v.on(QsFeatures.QS_CC_ONEUI_NO_HEADER)) {
            float hx = Rr - 14;
            gear(c, hx, y + 14, 24, 0xFFFFFFFF); hx -= 48;
            ccGlyph(c, CcGlyph.POWER, hx, y + 14, 24, 0xFFFFFFFF); hx -= 48;
            pencil(c, hx, y + 14, 24, 0xFFFFFFFF);
            y += 40;
        } else {
            gear(c, Rr - 50, y + 14, 22, 0xFFFFFFFF); dots(c, Rr - 14, y + 14, 0xFFFFFFFF);
            y += 40;
        }
        float colW = (Rr - L - gap) / 2, pillH = 64;
        bigTile(c, r, new RectF(L, y, L + colW, y + pillH), BIG[0]);
        bigTile(c, r, new RectF(L + colW + gap, y, Rr, y + pillH), BIG[2]);
        y += pillH + gap;
        int card = QsArt.oneUi8Card(true), soft = QsArt.oneUi8Soft(true);
        Paint p = r.gp; p.setStyle(Paint.Style.FILL);
        int cols = cols(v.choice(QsFeatures.QS_CC_COLS), 4);
        float cardTop = y;
        float cardH = v.choice(QsFeatures.QS_LABEL_POS) == 2 ? 196 : 270;
        p.setColor(card); c.drawRoundRect(new RectF(L, cardTop, Rr, cardTop + cardH), 28, 28, p);
        tileGrid(c, r, L + 4, Rr - 4, cardTop + 16, cardTop + cardH - 14, cols, 2, true, 0);
        p.setColor(0x66FFFFFF); c.drawRoundRect(new RectF(SW / 2 - 16, cardTop + cardH - 10, SW / 2 + 16, cardTop + cardH - 6), 2, 2, p);
        y += cardH + gap;
        float barH = 48, sliderCardH = barH * 2 + 3 * 12;
        p.setColor(card); c.drawRoundRect(new RectF(L, y, Rr, y + sliderCardH), 28, 28, p);
        for (int i = 0; i < 2; i++) {
            float by = y + 12 + i * (barH + 12);
            slider(c, r, new RectF(L + 12, by, Rr - 12 - barH - 12, by + barH), false, i == 1, i == 0 ? 0.55f : 0.35f);
            p.setColor(i == 0 ? 0xFFF4F4F6 : soft); c.drawCircle(Rr - 12 - barH / 2, by + barH / 2, barH / 2, p);
            ccGlyph(c, i == 0 ? CcGlyph.MOON : CcGlyph.SPEAKER, Rr - 12 - barH / 2, by + barH / 2, 22, i == 0 ? 0xFF1C1C1E : 0xFFFFFFFF);
        }
        y += sliderCardH + gap;
        if (!v.on(QsFeatures.QS_CC_ONEUI_NO_MEDIA) && y + 60 < SH - 20) {
            p.setColor(card); c.drawRoundRect(new RectF(L, y, Rr, y + 60), 30, 30, p);
            ccGlyph(c, CcGlyph.NOTE, L + 32, y + 30, 22, 0xFFFFFFFF);
            text(c, r, "Play music", L + 56, y + 36, 15, 500, 0xFFFFFFFF, Paint.Align.LEFT);
            p.setColor(soft); c.drawRoundRect(new RectF(Rr - 150, y + 10, Rr - 10, y + 50), 20, 20, p);
            text(c, r, "Media output", Rr - 80, y + 35, 12.5f, 500, 0xFFFFFFFF, Paint.Align.CENTER);
            y += 60 + gap;
        }
        if (!v.on(QsFeatures.QS_CC_ONEUI_NO_SHORTCUTS)) {
            String[] names = {"Device Center", "Screen cast", "Modes", "Share"};
            int[] g = {CcGlyph.DEVICES, CcGlyph.CAST, CcGlyph.MODES, CcGlyph.SHARE};
            for (int i = 0; i < 4 && y + 52 <= SH - 16; i++) {
                float x = L + (i % 2) * (colW + gap), sy = y + (i / 2) * (52 + gap);
                if (sy + 52 > SH - 16) break;
                p.setColor(card); c.drawRoundRect(new RectF(x, sy, x + colW, sy + 52), 26, 26, p);
                p.setColor(soft); c.drawCircle(x + 26, sy + 26, 17, p);
                ccGlyph(c, g[i], x + 26, sy + 26, 18, 0xFFFFFFFF);
                text(c, r, names[i], x + 52, sy + 31, 13, 500, 0xFFFFFFFF, Paint.Align.LEFT);
            }
        }
    }

    // ============================================================================================ volume pop-up

    private static void paintVolume(Canvas c, R r) {
        Looks.V v = r.v;
        background(c, r, 0x1F000000);
        statusBar(c, r, 0, 0xFFFFFFFF, true);
        homeIcons(c, r);
        boolean oneUi = v.on(SurfaceFeatures.VOL_ONEUI);
        float right = SW - 20, w = 56, left = right - w, top = 230, mainH = 248;
        float cx = left + w / 2;
        Paint p = r.gp; p.setStyle(Paint.Style.FILL);
        p.setColor(0xB3303036);
        c.drawRoundRect(new RectF(left, top, right, top + mainH), 28, 28, p);
        c.drawCircle(cx, top + mainH + 6 + 28, 28, p);
        glyph(c, r, "ring", cx, top + mainH + 34, 22, 0xFFFFFFFF);
        // expand chevron
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG); s.setColor(0xFFFFFFFF); s.setStyle(Paint.Style.STROKE); s.setStrokeWidth(2.4f); s.setStrokeCap(Paint.Cap.ROUND); s.setStrokeJoin(Paint.Join.ROUND);
        Path ch = new Path(); ch.moveTo(cx + 4, top + 12); ch.lineTo(cx - 3, top + 19); ch.lineTo(cx + 4, top + 26);
        c.drawPath(ch, s);
        float sliderTop = top + 37 + (171 - 146) / 2f, sliderBottom = sliderTop + 146;
        float fraction = 0.45f;
        if (oneUi) {
            float thick = 52;
            RectF track = new RectF(cx - thick / 2, sliderTop, cx + thick / 2, sliderBottom);
            int save = c.save();
            c.rotate(-90f, track.centerX(), track.centerY());
            RectF hb = new RectF(track.centerX() - track.height() / 2, track.centerY() - track.width() / 2, track.centerX() + track.height() / 2, track.centerY() + track.width() / 2);
            p.setColor(0x40FFFFFF); c.drawRoundRect(hb, thick / 2, thick / 2, p);
            p.setColor(QsArt.oneUiFill(true));
            c.drawRoundRect(new RectF(hb.left, hb.top, hb.left + QsArt.oneUi8FillLength(hb, fraction), hb.bottom), thick / 2, thick / 2, p);
            c.restoreToCount(save);
            ccGlyph(c, CcGlyph.SPEAKER, cx, sliderBottom - thick / 2, 22, QsArt.oneUiIconOverFill(true));
        } else {
            final float thick = 20;
            final RectF track = new RectF(cx - thick / 2, sliderTop, cx + thick / 2, sliderBottom);
            final Canvas cv = c;
            final Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
            com.hammad.redmagictweaks.xposed.QsArt.NativeDraw stock = () -> {
                pp.setColor(0x4DFFFFFF); cv.drawRoundRect(track, thick / 2, thick / 2, pp);
                int s2 = cv.save(); cv.clipRect(track.left, track.bottom - track.height() * fraction, track.right, track.bottom);
                pp.setColor(0xFFF4F4F6); cv.drawRoundRect(track, thick / 2, thick / 2, pp); cv.restoreToCount(s2);
            };
            int style = v.choice(SurfaceFeatures.VOL_STYLE);
            float[] radii = new float[8]; Arrays.fill(radii, thick / 2);
            boolean done = false;
            if (style > 0) { try { done = SurfaceArt.drawVolume(style, c, track, radii, 1f, stock); } catch (Throwable ignored) { } }
            if (!done) { try { stock.draw(); } catch (Throwable ignored) { } }
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx, track.bottom - track.height() * fraction, 6, p);
            ccGlyph(c, CcGlyph.SPEAKER, cx, top + 37 + 171 + 12, 22, 0xFFFFFFFF);
        }
    }

    /** A few app icons and a dock so the volume / power screens read as "over the home screen". */
    private static void homeIcons(Canvas c, R r) {
        Paint p = r.gp; p.setStyle(Paint.Style.FILL);
        int[] colors = {0xFFEA4335, 0xFF4285F4, 0xFF34A853, 0xFFFBBC05, 0xFF8E44AD, 0xFF00ACC1, 0xFFFF7043, 0xFF5C6BC0};
        for (int i = 0; i < 8; i++) {
            float x = 52 + (i % 4) * 90, y = SH - 250 + (i / 4) * 100;
            p.setColor(colors[i]); c.drawRoundRect(new RectF(x - 27, y - 27, x + 27, y + 27), 16, 16, p);
        }
        p.setColor(0x33FFFFFF); c.drawRoundRect(new RectF(16, SH - 100, SW - 16, SH - 30), 30, 30, p);
    }

    // ============================================================================================ power menu

    // ============================================================================================ recent apps

    static com.hammad.redmagictweaks.xposed.RecentsArt.Look recentsLook(Looks.V v) {
        return com.hammad.redmagictweaks.xposed.RecentsArt.resolve(v.choice(com.hammad.redmagictweaks.xposed.RecentsArt.KEY_STYLE),
                v.choice(com.hammad.redmagictweaks.xposed.RecentsArt.KEY_HEADER), v.choice(com.hammad.redmagictweaks.xposed.RecentsArt.KEY_CLEAR),
                v.choice(com.hammad.redmagictweaks.xposed.RecentsArt.KEY_CORNERS), v.choice(com.hammad.redmagictweaks.xposed.RecentsArt.KEY_SIZE));
    }

    private static void paintRecents(Canvas c, R r) {
        background(c, r, 0);
        Paint p = r.gp; p.setStyle(Paint.Style.FILL);
        p.setColor(0x8C10131B); c.drawRect(0, 0, SW, SH, p);   // the launcher's dim over the wallpaper
        com.hammad.redmagictweaks.xposed.RecentsArt.paintScreen(c, recentsLook(r.v), r::font, false);
        statusBar(c, r, 0, 0xFFFFFFFF, true);
    }

    /** A picker card: the part of the recent apps screen that option {@code key} changes. */
    public static Drawable recentsCrop(Context ctx, Looks.V v, String key) {
        final R r = new R(ctx, v);
        final RectF crop = com.hammad.redmagictweaks.xposed.RecentsArt.cropFor(key);
        return new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> {
            Path clip = new Path(); clip.addRoundRect(new RectF(0, 0, w, h), 12 * d, 12 * d, Path.Direction.CW);
            c.clipPath(clip);
            float s = Math.max(w / crop.width(), h / crop.height());
            c.scale(s, s);
            c.translate(-crop.left, -crop.top);
            try { paintRecents(c, r); } catch (Throwable t) { Log.w(TAG, "recents", t); }
        });
    }

    private static void paintPower(Canvas c, R r) {
        Looks.V v = r.v;
        background(c, r, 0);
        homeIcons(c, r);
        Paint p = r.gp; p.setStyle(Paint.Style.FILL);
        p.setColor(0x26FFFFFF); c.drawRect(0, 0, SW, SH, p);   // the blur, roughly
        p.setColor(0xB3000000); c.drawRect(0, 0, SW, SH, p);
        boolean oneUi = v.on(SurfaceFeatures.PWR_ONEUI);
        int finish = v.choice(SurfaceFeatures.PWR_STYLE);
        float size = oneUi ? 72 : 60, cy = SH / 2f + 4;
        float[] xs = {34 + 46, SW / 2, SW - 34 - 46};
        String[] names = {"Power off", "Restart", "Lockdown"};
        int[] fills = oneUi ? new int[]{0xFFE5484D, 0xFF2FA866, 0xFF55555C} : new int[]{0x2EFFFFFF, 0x2EFFFFFF, 0x2EFFFFFF};
        for (int i = 0; i < 3; i++) {
            float x = xs[i];
            RectF b = new RectF(x - size / 2, cy - size / 2, x + size / 2, cy + size / 2);
            p.setColor(fills[i]); c.drawOval(b, p);
            if (!oneUi && finish > 0) { float[] corners = new float[8]; Arrays.fill(corners, size / 2); try { SurfaceArt.drawCard(finish, c, b, corners, 1f); } catch (Throwable ignored) { } }
            int gc = oneUi ? 0xFFFFFFFF : 0xE6FFFFFF;
            if (i == 0) ccGlyph(c, CcGlyph.POWER, x, cy, size * 0.42f, gc);
            else if (i == 1) restart(c, x, cy, size * 0.42f, gc);
            else padlock(c, x, cy, size * 0.42f, gc);
            if (oneUi) text(c, r, names[i], x, cy + size / 2 + 12 + 15, 14f, 700, 0xFFFFFFFF, Paint.Align.CENTER);
            else text(c, r, names[i], x, cy + size / 2 + 10 + 12, 12f, 400, 0xCCFFFFFF, Paint.Align.CENTER);
        }
    }

    private static void restart(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size * 0.12f); p.setStrokeCap(Paint.Cap.ROUND);
        float rr = size * 0.36f;
        c.drawArc(new RectF(cx - rr, cy - rr, cx + rr, cy + rr), -60, 300, false, p);
        p.setStyle(Paint.Style.FILL);
        Path a = new Path();
        float ax = cx + rr * 0.5f, ay = cy - rr * 0.87f;
        a.moveTo(ax + size * 0.18f, ay); a.lineTo(ax - size * 0.06f, ay - size * 0.17f); a.lineTo(ax - size * 0.06f, ay + size * 0.17f); a.close();
        c.drawPath(a, p);
    }

    private static void padlock(Canvas c, float cx, float cy, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color);
        c.drawRoundRect(new RectF(cx - size * 0.32f, cy - size * 0.05f, cx + size * 0.32f, cy + size * 0.38f), size * 0.08f, size * 0.08f, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size * 0.11f);
        c.drawArc(new RectF(cx - size * 0.21f, cy - size * 0.38f, cx + size * 0.21f, cy + size * 0.08f), 180, 180, false, p);
    }
}
