package com.hammad.redmagictweaks;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/**
 * Picture of each Game view choice (Features.GAME_VIEWS): a landscape phone at the real panel shape with a simple game
 * scene, drawn the way the choice shows it, plus estimate lines. No reboot or game launch needed to see the difference.
 *
 * Model ("PUBG-style"): the game keeps its side-to-side view and shows more or less top-to-bottom with the shape it
 * renders at, which is why 4:3 ("iPad view") shows more around you. The world is 1 unit wide and 1 / ratio units tall.
 *  - Stock: Game Space's ratio (16:9 as the example) in the middle of the panel, bars at the sides, nothing stretched.
 *  - Full screen: the panel's own shape, no bars, no stretch.
 *  - Stretched views: the game renders at that shape (short side kept: the size Game view sets) and the panel stretches
 *    it to the full width, so the extra height stays and everything, the HUD too, looks wider by panel / ratio.
 * Games that keep the top-to-bottom view instead (most shooters on PC) would show less at the sides; the lines say
 * "estimate" for that reason.
 */
public final class GameViewArt {
    private GameViewArt() {}

    /** Example Game Space ratio drawn for Stock. */
    private static final float STOCK_RATIO = 16f / 9f;

    /** Panel size in pixels, landscape (long, short). */
    public static int[] panel(Context ctx) {
        int w = 0, h = 0;
        try {
            android.view.WindowManager wm = ctx.getSystemService(android.view.WindowManager.class);
            android.graphics.Rect b = wm.getMaximumWindowMetrics().getBounds();
            w = b.width(); h = b.height();
        } catch (Throwable ignored) { }
        if (w <= 0 || h <= 0) { w = 1216; h = 2688; }
        return new int[]{Math.max(w, h), Math.min(w, h)};
    }

    /** Shape the game renders at for a choice; full screen = the panel's own. */
    static float renderRatio(int index, float panelRatio) {
        if (index == 0) return STOCK_RATIO;
        if (index == 1 || index >= Features.GAME_VIEW_RATIO.length || Features.GAME_VIEW_RATIO[index] <= 0) return panelRatio;
        return Features.GAME_VIEW_RATIO[index];
    }

    /** Estimate lines for a choice. */
    public static String[] lines(Context ctx, int index) {
        String[] l = lines0(ctx, index);
        if (photoOf(index) != null) l[l.length - 1] = "Real PUBG screenshot, shown the way the screen stretches it";
        return l;
    }

    private static String[] lines0(Context ctx, int index) {
        int[] p = panel(ctx);
        float pr = (float) p[0] / p[1];
        float r = renderRatio(index, pr);
        int rw = Math.round(p[1] * r);
        int more = Math.round((pr / r - 1f) * 100f);
        if (index == 0) {
            return new String[]{
                    "Game Space ratio (16:9 here): " + rw + " × " + p[1] + ", black bars at the sides",
                    (more > 0 ? "+" + more + " % top-to-bottom vs Full screen" : "Same view as Full screen") + ", same side-to-side, nothing stretched",
                    "Without a Game Space ratio the game fills the screen"};
        }
        if (index == 1) {
            return new String[]{
                    "Renders " + p[0] + " × " + p[1] + " (the screen's own shape), no bars",
                    "Nothing stretched: the reference for the views below",
                    "Even when a ratio is picked in Game Space"};
        }
        return new String[]{
                "Renders " + rw + " × " + p[1] + ", stretched to " + p[0] + " × " + p[1],
                (more > 0 ? "+" + more + " % top-to-bottom" : more < 0 ? more + " % top-to-bottom" : "Same top-to-bottom") + " vs Full screen, same side-to-side",
                more > 0 ? "Everything looks " + more + " % wider (the HUD too)" : "Hardly stretched"};
    }

    /** The picture for one choice, with the estimate lines under it when there is room. */
    /** Real PUBG screenshots (what the game draws) for the choices that have one: Stock (Game Space 16:9), 16:10, 16:9. */
    private static String photoOf(int index) {
        switch (index) {
            case 0: case 5: return "gameview/16x9.jpg";
            case 4: return "gameview/16x10.jpg";
            default: return null;
        }
    }

    private static final java.util.Map<String, android.graphics.Bitmap> PHOTOS = new java.util.HashMap<>();

    static synchronized android.graphics.Bitmap photo(Context ctx, int index) {
        String path = photoOf(index);
        if (path == null) return null;
        if (PHOTOS.containsKey(path)) return PHOTOS.get(path);
        android.graphics.Bitmap b = null;
        try (java.io.InputStream in = ctx.getAssets().open(path)) {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            b = android.graphics.BitmapFactory.decodeStream(in, null, o);
        } catch (Throwable ignored) { }
        PHOTOS.put(path, b);
        return b;
    }

    public static Drawable preview(final Context ctx, final int index) {
        final android.graphics.Bitmap shot = photo(ctx, index);
        final int[] p = panel(ctx);
        final float pr = (float) p[0] / p[1];
        final String[] text = lines(ctx, index);
        return new QsFeatures.Sketch(ctx, 300, Math.round(300 / pr) + 58, (c, w, h, d) -> {
            boolean words = w >= 220 * d;
            float textH = words ? 14 * d * text.length + 8 * d : 0;
            float phoneW = Math.min(w, (h - textH) * pr);
            float phoneH = phoneW / pr;
            float left = (w - phoneW) / 2;
            paintPhone(c, new RectF(left, 0, left + phoneW, phoneH), index, pr, d, shot);
            if (!words) return;
            Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
            t.setColor(0xFFE6E6E6);
            t.setTextSize(11.5f * d);
            float y = phoneH + 8 * d + 11 * d;
            for (int i = 0; i < text.length; i++) {
                t.setColor(i == 0 ? 0xFFFFFFFF : 0xFFBDBDBD);
                t.setTypeface(i == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                c.drawText(fit(text[i], t, w), 0, y, t);
                y += 14 * d;
            }
        });
    }

    private static String fit(String s, Paint p, float w) {
        if (p.measureText(s) <= w) return s;
        String e = s;
        while (e.length() > 4 && p.measureText(e + "…") > w) e = e.substring(0, e.length() - 1);
        return e + "…";
    }

    /** Landscape phone: frame, screen, the choice's picture. */
    public static void paintPhone(Canvas c, RectF b, int index, float panelRatio, float d, android.graphics.Bitmap shot) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float rad = b.height() * 0.12f;
        p.setColor(0xFF2B2B2E);
        c.drawRoundRect(b, rad, rad, p);
        float in = Math.max(2 * d, b.height() * 0.035f);
        RectF s = new RectF(b.left + in, b.top + in, b.right - in, b.bottom - in);
        int save = c.save();
        Path clip = new Path();
        clip.addRoundRect(s, rad * 0.8f, rad * 0.8f, Path.Direction.CW);
        c.clipPath(clip);
        p.setColor(0xFF000000);
        c.drawRect(s, p);
        float r = renderRatio(index, panelRatio);
        RectF dst = s;
        if (index == 0) {                                    // Game Space ratio: true proportions, bars at the sides
            float gw = s.height() * r;
            dst = new RectF(s.centerX() - gw / 2, s.top, s.centerX() + gw / 2, s.bottom);
        }
        c.save();
        c.clipRect(dst);
        if (shot != null) {                                  // a real screenshot: drawn into the same box, stretched like on the phone
            Paint f = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            c.drawBitmap(shot, null, dst, f);
        } else {
            scene(c, dst, 1f / r, d);
            hud(c, dst, r, d);
        }
        c.restore();
        c.restoreToCount(save);
    }

    /** World 1 unit wide and vs tall, centred; drawn into dst (stretched if dst's shape differs). */
    private static void scene(Canvas c, RectF dst, float vs, float d) {
        final float W = dst.width(), H = dst.height();
        final float sx = W, sy = H / vs, ox = dst.left + W / 2, oy = dst.top + H / 2;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float horizon = oy + 0.03f * sy;
        p.setShader(new LinearGradient(0, dst.top, 0, horizon, 0xFF2F64A8, 0xFFA9CFEC, Shader.TileMode.CLAMP));
        c.drawRect(dst.left, dst.top, dst.right, horizon, p);
        p.setShader(new LinearGradient(0, horizon, 0, dst.bottom, 0xFF7B9A55, 0xFF3F5E2C, Shader.TileMode.CLAMP));
        c.drawRect(dst.left, horizon, dst.right, dst.bottom, p);
        p.setShader(null);
        // mountains on the horizon
        Path m = new Path();
        m.moveTo(ox - 0.6f * sx, horizon);
        float[][] peaks = {{-0.42f, -0.07f}, {-0.30f, -0.03f}, {-0.16f, -0.09f}, {0.02f, -0.04f}, {0.18f, -0.08f}, {0.34f, -0.035f}, {0.47f, -0.075f}};
        for (float[] k : peaks) m.lineTo(ox + k[0] * sx, oy + (0.03f + k[1]) * sy);
        m.lineTo(ox + 0.6f * sx, horizon);
        m.close();
        p.setColor(0xFF5F7C8F);
        c.drawPath(m, p);
        // buildings: one tall tower that only taller shapes show to the top
        float[][] b = {{-0.40f, 0.10f, 0.10f}, {-0.27f, 0.07f, 0.17f}, {0.22f, 0.08f, 0.13f}, {0.33f, 0.06f, 0.36f}};
        for (float[] k : b) {
            p.setColor(0xFF8D8A86);
            c.drawRect(ox + k[0] * sx, horizon - k[2] * sy, ox + (k[0] + k[1]) * sx, horizon, p);
            p.setColor(0xFF5E5B58);
            for (float y = 0.03f; y < k[2] - 0.02f; y += 0.045f)
                c.drawRect(ox + (k[0] + 0.015f) * sx, horizon - (k[2] - y) * sy, ox + (k[0] + k[1] - 0.015f) * sx, horizon - (k[2] - y - 0.018f) * sy, p);
        }
        // a plane high up and crates close to the player: in view only with taller shapes
        p.setColor(0xFFF2F2F2);
        float px = ox - 0.12f * sx, py = oy - 0.33f * sy;
        c.drawRect(px - 0.04f * sx, py - 0.006f * sy, px + 0.04f * sx, py + 0.006f * sy, p);
        c.drawRect(px - 0.008f * sx, py - 0.025f * sy, px + 0.008f * sx, py + 0.025f * sy, p);
        p.setColor(0xFFB07A3C);
        for (float[] k : new float[][]{{-0.30f, 0.28f}, {0.26f, 0.31f}, {0.06f, 0.34f}})
            c.drawRect(ox + k[0] * sx, oy + k[1] * sy, ox + (k[0] + 0.05f) * sx, oy + (k[1] + 0.05f) * sy, p);
        // a tree and the player
        p.setColor(0xFF2E5B26);
        c.drawCircle(ox - 0.16f * sx, horizon - 0.05f * sy, 0.035f * sx, p);
        p.setColor(0xFF4A3420);
        c.drawRect(ox - 0.165f * sx, horizon - 0.02f * sy, ox - 0.155f * sx, horizon + 0.03f * sy, p);
        p.setColor(0xFF2A2A2A);
        RectF body = new RectF(ox + 0.04f * sx, oy + 0.07f * sy, ox + 0.10f * sx, oy + 0.21f * sy);
        c.drawRoundRect(body, 0.01f * sx, 0.01f * sx, p);
        c.drawOval(new RectF(ox + 0.05f * sx, oy + 0.03f * sy, ox + 0.09f * sx, oy + 0.075f * sy), p);
        // crosshair (world space: stretches like the rest)
        p.setColor(0xFFFFFFFF);
        p.setStrokeWidth(Math.max(1f, 1.2f * d));
        float cl = 0.02f * sx;
        c.drawLine(ox - cl, oy, ox - cl / 3, oy, p);
        c.drawLine(ox + cl / 3, oy, ox + cl, oy, p);
        c.drawLine(ox, oy - cl * sy / sx, ox, oy - cl * sy / sx / 3, p);
        c.drawLine(ox, oy + cl * sy / sx / 3, ox, oy + cl * sy / sx, p);
    }

    /** HUD laid out in the game's own pixels (H tall, H * ratio wide) and stretched with the picture: circles turn into
     *  ovals when the picture is stretched. */
    private static void hud(Canvas c, RectF dst, float ratio, float d) {
        float H = dst.height();
        float r = H * 0.11f;
        float rx = r * dst.width() / (H * ratio);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x66FFFFFF);                                   // joystick, bottom left
        c.drawOval(new RectF(dst.left + rx * 0.6f, dst.bottom - r * 2.6f, dst.left + rx * 2.6f, dst.bottom - r * 0.6f), p);
        p.setColor(0x88E53935);                                   // fire, bottom right
        c.drawOval(new RectF(dst.right - rx * 2.4f, dst.bottom - r * 2.6f, dst.right - rx * 0.6f, dst.bottom - r * 0.8f), p);
        p.setColor(0x55FFFFFF);                                   // minimap, top right
        c.drawRect(dst.right - rx * 2.6f, dst.top + r * 0.4f, dst.right - rx * 0.5f, dst.top + r * 2.3f, p);
    }
}
