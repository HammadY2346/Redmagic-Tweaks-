package com.hammad.redmagictweaks.xposed;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;

import java.util.Arrays;

/**
 * Pure drawing code for notification cards, the power-menu buttons and the volume sliders, ported from nubia-ly
 * (NotificationEffects, VolumeEffects, NotificationStylePreview, PowerStylePreview, VolumeStylePreview).
 * Used by SurfaceHooks inside System UI and by SurfaceFeatures for the picker previews.
 */
public final class SurfaceArt {
    private SurfaceArt() {}

    /** Finishes shared by the power-menu buttons (overlays only). */
    public static final String[] CARD_STYLES = {"Stock", "Duoline", "CyberPunk", "Fluid", "iOS", "Layers"};
    /** Notification card finishes: the overlays above plus index 6 "One UI" (opaque dark card with a hairline outline). */
    public static final String[] NOTIF_STYLES = {"Stock", "Duoline", "CyberPunk", "Fluid", "iOS", "Layers", "One UI"};
    public static final int ONE_UI_CARD = 6;
    public static final String[] VOLUME_STYLES = {"Stock", "Outline", "Gradient", "Neumorph", "Neumorph Outline", "Double Layer", "Shaded Layer"};
    /** Notification corner choices in dp (nubia-ly NotificationControls: 0 stock, 12, 24, 32; 26 = One UI 7/8, appended to keep indexes). */
    public static final String[] CORNERS = {"Stock", "12 dp", "24 dp", "32 dp", "26 dp (One UI)"};
    public static final int[] CORNER_DP = {0, 12, 24, 32, 26};
    public static final int ONE_UI_CORNER = 4;
    public static final String[] OPACITIES = {"Stock", "95 %", "90 %", "85 %", "80 %", "75 %", "70 %", "65 %", "60 %", "55 %", "50 %"};
    public static final int[] OPACITY_PERCENT = {0, 95, 90, 85, 80, 75, 70, 65, 60, 55, 50};

    public static int cornerDp(int choice) { return choice > 0 && choice < CORNER_DP.length ? CORNER_DP[choice] : 0; }
    public static int opacity(int choice) { return choice > 0 && choice < OPACITY_PERCENT.length ? OPACITY_PERCENT[choice] : 0; }

    // ================================================================================================= notification / power-menu finishes

    /** Static overlays over a native rounded background that was already drawn into the same layer. Styles 1..5 (power menu, notifications). */
    public static boolean drawCard(int style, Canvas canvas, RectF b, float[] corners, float density) { return drawCard(style, canvas, b, corners, density, true); }

    /**
     * Styles 1..5 as above; 6 = One UI: an opaque One UI card colour painted over the (translucent) native background,
     * plus a 1 px lighter hairline inside the edge. {@code dark} selects the One UI dark or light card.
     */
    public static boolean drawCard(int style, Canvas canvas, RectF b, float[] corners, float density, boolean dark) {
        if (style < 1 || style > ONE_UI_CARD || b.isEmpty() || corners == null || corners.length != 8 || !Float.isFinite(density) || density <= 0) return false;
        for (float r : corners) if (!Float.isFinite(r) || r < 0) return false;
        int save = canvas.saveLayer(b, null); boolean complete = false;
        try {
            canvas.clipPath(outline(b, corners, 0));
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float u = Math.min(density, Math.min(b.width(), b.height()) / 12f);
            switch (style) {
                case ONE_UI_CARD: {
                    p.setColor(QsArt.oneUiCard(dark)); canvas.drawRect(b, p);
                    p.setColor(dark ? 0x24FFFFFF : 0x14000000); canvas.drawPath(ring(b, corners, 0, Math.max(1f, density)), p);
                    break;
                }
                case 1: p.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, 0x90ffffff, 0x60000000, Shader.TileMode.CLAMP)); canvas.drawRect(b.left, b.top, b.right, b.top + 3 * u, p); canvas.drawRect(b.left, b.bottom - 3 * u, b.right, b.bottom, p); break;
                case 2: {
                    p.setColor(0x45000000); canvas.drawRect(b, p); p.setColor(0x28ffffff);
                    Path facets = new Path();
                    facets.moveTo(b.left, b.top + 6 * u); facets.lineTo(b.centerX() - 3 * u, b.top + 6 * u); facets.lineTo(b.centerX() + 3 * u, b.bottom - 3 * u); facets.lineTo(b.left, b.bottom - 3 * u); facets.close();
                    facets.moveTo(b.centerX() + 3 * u, b.top + 3 * u); facets.lineTo(b.right, b.top + 3 * u); facets.lineTo(b.right, b.bottom - 6 * u); facets.lineTo(b.centerX() + 9 * u, b.bottom - 6 * u); facets.close();
                    canvas.drawPath(facets, p);
                    break;
                }
                case 3: p.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, 0x20ffffff, 0x08000000, Shader.TileMode.CLAMP)); canvas.drawRect(b, p); break;
                case 4: p.setColor(0x50000000); canvas.drawRect(b.left, b.bottom - 12 * u, b.right, b.bottom, p); p.setColor(0x28ffffff); canvas.drawRect(b.left + 12 * u, b.bottom - 8 * u, b.right - 12 * u, b.bottom - 4 * u, p); p.setColor(0x40ffffff); canvas.drawRect(b.left + 24 * u, b.bottom - 4 * u, b.right - 24 * u, b.bottom, p); break;
                case 5: {
                    p.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, 0x65ffffff, 0x60000000, Shader.TileMode.CLAMP));
                    Path ring = outline(b, corners, 0); ring.setFillType(Path.FillType.EVEN_ODD); ring.addPath(outline(b, corners, 4 * u)); canvas.drawPath(ring, p);
                    break;
                }
                default: return false;
            }
            complete = true;
            return true;
        } finally {
            try { if (!complete) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(save); }
        }
    }

    private static Path outline(RectF bounds, float[] corners, float inset) {
        RectF b = new RectF(bounds); b.inset(inset, inset); Path p = new Path();
        if (b.isEmpty()) return p;
        float[] radii = corners.clone(); for (int i = 0; i < 8; i++) radii[i] = Math.max(0, radii[i] - inset);
        p.addRoundRect(b, radii, Path.Direction.CW); return p;
    }
    private static Path ring(RectF bounds, float[] corners, float start, float end) {
        Path path = outline(bounds, corners, start); path.setFillType(Path.FillType.EVEN_ODD); path.addPath(outline(bounds, corners, end)); return path;
    }

    // ================================================================================================= volume

    /** Rising-inspired shading over one native vendor slider draw. Styles 1..6. */
    public static boolean drawVolume(int style, Canvas canvas, RectF bounds, float[] corners, float density, QsArt.NativeDraw stock) throws Throwable {
        if (style < 1 || style > 6 || bounds.isEmpty() || !Float.isFinite(density) || density <= 0 || corners == null || corners.length != 8) return false;
        for (float n : corners) if (!Float.isFinite(n) || n < 0) return false;
        int save = canvas.saveLayer(null, null); boolean complete = false;
        try {
            stock.draw();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setBlendMode(BlendMode.SRC_ATOP); float unit = Math.min(density, Math.min(bounds.width(), bounds.height()) / 12f);
            if (style == 2) { p.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, 0x50d3cbef, 0x60353778, Shader.TileMode.CLAMP)); canvas.drawRect(bounds, p); }
            if (style == 3 || style == 4 || style == 5) {
                p.setShader(new LinearGradient(bounds.left, bounds.top, bounds.left, bounds.bottom, 0x70ffffff, 0x70000000, Shader.TileMode.CLAMP)); canvas.drawPath(ring(bounds, corners, 0, 2 * unit), p);
                p.setShader(new LinearGradient(bounds.left, bounds.top, bounds.left, bounds.bottom, 0x50000000, 0x50ffffff, Shader.TileMode.CLAMP)); canvas.drawPath(ring(bounds, corners, 2 * unit, 4 * unit), p);
                if (style == 5) { p.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, 0x45ffffff, 0x55000000, Shader.TileMode.CLAMP)); canvas.drawPath(ring(bounds, corners, 5 * unit, 7 * unit), p); }
            }
            if (style == 6) { p.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, 0x70000000, 0x10ffffff, Shader.TileMode.CLAMP)); canvas.drawRect(bounds, p); }
            if (style == 1 || style == 4) { p.setShader(null); p.setBlendMode(BlendMode.DST_OUT); p.setColor(0x80000000); canvas.drawPath(outline(bounds, corners, (style == 4 ? 4 : 2.5f) * unit), p); }
            complete = true;
            return true;
        } finally { try { if (!complete) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(save); } }
    }

    // ================================================================================================= preview painters (app side)

    /** nubia-ly NotificationStylePreview: two sample cards with the finish, opacity and corner choice applied. */
    public static void paintNotificationPreview(Canvas c, int style, float w, float h, float density, int opacityPercent, int cornerDp) {
        if (w < 16 || h < 16) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 2; i++) {
            float margin = w * .04f, gap = h * .04f, top = i * h / 2 + gap;
            RectF b = new RectF(margin, top, w - margin, (i + 1) * h / 2 - gap);
            float radius = Math.min(b.height() / 3, 16 * density);
            if (cornerDp != 0) radius = Math.max(radius, cornerDp * density);
            float[] radii = new float[8]; Arrays.fill(radii, radius);
            GradientDrawable bg = new GradientDrawable(); bg.setColor(0xff66616f); bg.setCornerRadius(radius);
            bg.setBounds(Math.round(b.left), Math.round(b.top), Math.round(b.right), Math.round(b.bottom));
            int layer = c.saveLayerAlpha(b.left, b.top, b.right, b.bottom, opacityPercent == 0 ? 255 : opacityPercent * 255 / 100);
            try { bg.draw(c); if (style > 0) drawCard(style, c, b, radii, density); } catch (Throwable t) { bg.draw(c); } finally { c.restoreToCount(layer); }
            float y = b.top + b.height() * .38f;
            p.setColor(0xffd3cbef); c.drawCircle(b.left + b.height() * .28f, y, b.height() * .09f, p);
            float x = b.left + b.height() * .5f; p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(Math.max(2, b.height() * .055f));
            c.drawLine(x, y, b.left + b.width() * .7f, y, p);
            p.setColor(0xffc8c1d1); c.drawLine(x, y + b.height() * .24f, b.left + b.width() * .86f, y + b.height() * .24f, p);
        }
    }

    /** nubia-ly VolumeStylePreview: three vertical sliders at different levels. */
    public static void paintVolumePreview(final Canvas c, int style, float w, float h, float density) {
        for (int i = 0; i < 3; i++) {
            final int index = i;
            float width = Math.min(w / 5f, (h - 8) / 5f), height = width * 5, x = w * (i + 0.5f) / 3f - width / 2, top = (h - height) / 2;
            final RectF b = new RectF(x, top, x + width, top + height);
            float[] radii = new float[8]; Arrays.fill(radii, width / 2);
            final GradientDrawable d = new GradientDrawable(); d.setColor(0xff67626f); d.setCornerRadius(width / 2);
            d.setBounds(Math.round(b.left), Math.round(b.top), Math.round(b.right), Math.round(b.bottom));
            QsArt.NativeDraw nativeDraw = () -> {
                d.draw(c); int saved = c.save(); c.clipRect(b.left, b.top + b.height() * (0.25f + index * 0.15f), b.right, b.bottom);
                d.setColor(0xffd3cbef); d.draw(c); c.restoreToCount(saved); d.setColor(0xff67626f);
            };
            try { if (style <= 0 || !drawVolume(style, c, b, radii, density, nativeDraw)) nativeDraw.draw(); } catch (Throwable t) { d.draw(c); }
        }
    }

    /** nubia-ly PowerStylePreview: the two round Power / Restart buttons with the finish applied. */
    public static void paintPowerPreview(Canvas c, int style, float w, float h, float density) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float size = Math.min(w * .30f, h * .70f), gap = size * .35f, left = (w - size * 2 - gap) / 2, top = (h - size) / 2;
        for (int i = 0; i < 2; i++) {
            float x = left + i * (size + gap);
            RectF b = new RectF(x, top, x + size, top + size);
            p.setColor(0xff56515f); c.drawRoundRect(b, size / 2, size / 2, p);
            float[] corners = new float[8]; Arrays.fill(corners, size / 2);
            try { if (style > 0) drawCard(style, c, b, corners, density); } catch (Throwable ignored) { }
            p.setColor(0xffd3cbef); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(size * .045f);
            float cx = b.centerX(), cy = b.centerY();
            c.drawArc(new RectF(cx - size * .19f, cy - size * .19f, cx + size * .19f, cy + size * .19f), -45, 270, false, p);
            if (i == 0) c.drawLine(cx, cy - size * .27f, cx, cy, p);
            else { c.drawLine(cx + size * .19f, cy - size * .19f, cx + size * .19f, cy, p); c.drawLine(cx + size * .19f, cy - size * .19f, cx, cy - size * .19f, p); }
            p.setStyle(Paint.Style.FILL);
        }
    }

    /** Status-bar schematic: N notification icons then either the overflow dot or a "+N" count. */
    public static void paintOverflowPreview(Canvas c, boolean count, int limit, float w, float h, int accent, int dim) {
        if (w < 20 || h < 10) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float icon = Math.min(h * 0.42f, w / 12f), gap = icon * 0.45f, x = w * 0.06f, cy = h / 2f;
        int shown = limit <= 0 ? 4 : Math.min(limit, 8);
        p.setColor(dim);
        for (int i = 0; i < shown; i++) { c.drawRoundRect(new RectF(x, cy - icon / 2, x + icon, cy + icon / 2), icon * 0.25f, icon * 0.25f, p); x += icon + gap; }
        p.setColor(accent);
        if (count) {
            p.setTextSize(icon * 0.9f); p.setTextAlign(Paint.Align.LEFT); p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            Paint.FontMetrics fm = p.getFontMetrics(); c.drawText("+3", x, cy - (fm.ascent + fm.descent) / 2, p);
        } else c.drawCircle(x + icon * 0.3f, cy, icon * 0.16f, p);
    }
}
