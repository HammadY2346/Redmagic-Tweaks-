package com.hammad.redmagictweaks.xposed;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;

import java.util.Arrays;

/**
 * Pure drawing code for the Quick Settings tweaks, ported from nubia-ly (QsOutlineRenderer, QsTileEffects,
 * BrightnessEffects, QsShapeBackend geometry, QsStylePreview, BrightnessStylePreview). Used by QsHooks inside
 * System UI and by QsFeatures for the picker previews. Nothing here touches host state.
 */
public final class QsArt {
    private QsArt() {}

    /** Runs the host's own draw of the decorated element. */
    public interface NativeDraw { void draw() throws Throwable; }

    /**
     * Index 8 = "One UI": no overlay, the tile colours themselves change (QsHooks colour hooks).
     * Index 9 = "One UI 8 (white on)": the frosted One UI 8 quick panel — round tiles white with a dark icon when on,
     * translucent with a white icon when off; large pills stay translucent and carry their icon in a circle.
     */
    public static final String[] TILE_STYLES = {"Stock", "Outline", "CyberPunk", "Neumorph", "Reflected", "Shaded", "Surround", "Thin", "One UI", "One UI 8 (white on)"};
    public static final int ONE_UI_TILE = 8;
    public static final int ONE_UI8_TILE = 9;
    /** True for the two styles whose colours come from QsHooks' colour getters instead of an overlay. */
    public static boolean oneUiTile(int style) { return style == ONE_UI_TILE || style == ONE_UI8_TILE; }
    /**
     * Index 4 = "One UI": thick track in One UI colours with a small sun glyph inside its start.
     * Index 5 = "One UI 8": fully rounded track and a fully rounded fill that never gets shorter than a circle, so the
     * icon at the start always sits inside the fill (Samsung One UI 7/8 quick-panel sliders).
     */
    public static final String[] BRIGHTNESS_STYLES = {"Stock", "Outline", "Neumorph", "Thin", "One UI", "One UI 8 (rounded fill)"};
    public static final int ONE_UI_BRIGHTNESS = 4;
    public static final int ONE_UI8_BRIGHTNESS = 5;
    /** True for both One UI slider styles (Samsung colours). */
    public static boolean oneUiSlider(int style) { return style == ONE_UI_BRIGHTNESS || style == ONE_UI8_BRIGHTNESS; }
    /** Tile shapes: {label, nubia-ly shape id} — 1 square, 2 rounded square, 3 leaf, 4 pill, 5 One UI pill (fully rounded), 100 + percent = custom corner fraction. */
    public static final String[] SHAPES = {"Stock", "Square", "Rounded square", "Leaf", "Pill", "Custom 10 %", "Custom 15 %", "Custom 20 %", "Custom 30 %", "Custom 35 %", "Custom 40 %", "Custom 45 %", "One UI pill"};
    public static final int[] SHAPE_IDS = {0, 1, 2, 3, 4, 110, 115, 120, 130, 135, 140, 145, 5};
    public static final int ONE_UI_PILL_SHAPE = 5;

    public static int shapeId(int choice) { return choice > 0 && choice < SHAPE_IDS.length ? SHAPE_IDS[choice] : 0; }

    // ================================================================================================= One UI 7/8 palette

    public static final int ONE_UI_BLUE = 0xFF3E7BFA;
    /** Tile background for a QS state (0 unavailable, 1 inactive, 2 active); 0 = leave the native colour. */
    public static int oneUiBackground(int state, boolean dark) {
        switch (state) {
            case 2: return ONE_UI_BLUE;
            case 1: return dark ? 0xFF2A2A2E : 0xFFFFFFFF;
            case 0: return dark ? 0xFF202024 : 0xFFEBEBEF;
            default: return 0;
        }
    }
    public static int oneUiIcon(int state, boolean dark) {
        switch (state) {
            case 2: return 0xFFFFFFFF;
            case 1: return dark ? 0xFFD4D4D8 : 0xFF3C3C40;
            case 0: return dark ? 0xFF6A6A70 : 0xFFA0A0A6;
            default: return 0;
        }
    }
    /** Label colour: wide tiles carry the label inside the pill, small tiles under the circle (on the panel). */
    public static int oneUiLabel(int state, boolean wide, boolean dark) {
        if (state < 0 || state > 2) return 0;
        if (state == 0) return dark ? 0xFF7A7A80 : 0xFF9A9AA0;
        if (wide) return state == 2 ? 0xFFFFFFFF : (dark ? 0xFFE6E6EA : 0xFF1C1C1E);
        return dark ? 0xFFE2E2E6 : 0xFF1C1C1E;
    }
    public static int oneUiSecondaryLabel(int state, boolean dark) {
        int c = oneUiLabel(state, true, dark);
        return c == 0 ? 0 : (c & 0x00FFFFFF) | 0xA6000000;
    }
    // ---- One UI 8 "white on" palette (TILE_STYLES[9]); wide = the large pill (Wi-Fi, Bluetooth)
    public static int oneUi8Background(int state, boolean wide, boolean dark) {
        if (state < 0 || state > 2) return 0;
        if (wide) return state == 0 ? (dark ? 0x14FFFFFF : 0x0A000000) : (dark ? 0x26FFFFFF : 0x12000000);
        switch (state) {
            case 2: return dark ? 0xFFF4F4F6 : 0xFF26262A;
            case 1: return dark ? 0x33FFFFFF : 0x1A000000;
            default: return dark ? 0x1AFFFFFF : 0x0D000000;
        }
    }
    /** Icon colour; on wide pills the icon sits in {@link #oneUi8IconCircle}, so the same rule applies. */
    public static int oneUi8Icon(int state, boolean dark) {
        switch (state) {
            case 2: return dark ? 0xFF1C1C1E : 0xFFFFFFFF;
            case 1: return dark ? 0xFFFFFFFF : 0xFF1C1C1E;
            case 0: return dark ? 0x80FFFFFF : 0x801C1C1E;
            default: return 0;
        }
    }
    /** The circle behind a large pill's icon. */
    public static int oneUi8IconCircle(int state, boolean dark) {
        switch (state) {
            case 2: return dark ? 0xFFF4F4F6 : 0xFF26262A;
            case 1: return dark ? 0x33FFFFFF : 0x1A000000;
            default: return dark ? 0x1AFFFFFF : 0x0D000000;
        }
    }
    public static int oneUi8Label(int state, boolean dark) {
        if (state < 0 || state > 2) return 0;
        if (state == 0) return dark ? 0x99FFFFFF : 0x991C1C1E;
        return dark ? 0xFFFFFFFF : 0xFF1C1C1E;
    }
    public static int oneUi8SecondaryLabel(int state, boolean dark) {
        int c = oneUi8Label(state, dark);
        return c == 0 ? 0 : (c & 0x00FFFFFF) | 0xA6000000;
    }
    /** Translucent card of the One UI 8 Control Center layout (tile grid, sliders, media, shortcuts). */
    public static int oneUi8Card(boolean dark) { return dark ? 0x1FFFFFFF : 0x10000000; }
    /** Soft circle / pill inside a card (slider buttons off, shortcut icons, "Media output"). */
    public static int oneUi8Soft(boolean dark) { return dark ? 0x26FFFFFF : 0x14000000; }
    public static int oneUi8Text(boolean dark) { return dark ? 0xFFFFFFFF : 0xFF1C1C1E; }

    // ================================================================================================= chosen colours

    /** Colours for the active tile and the brightness / volume fill (plain, wallpaper, gradients). Index 0 = stock. */
    public static final String[] PANEL_COLORS = {"Stock", "Wallpaper colour", "One UI blue", "RedMagic red", "Blue", "Green", "Purple", "Orange",
            "Pink", "Teal", "Gold", "White", "Sunset gradient", "Ocean gradient", "RedMagic gradient", "Aurora gradient"};
    private static final int[][] PANEL_RGB = {null, null, {ONE_UI_BLUE}, {0xFFE8343B}, {0xFF3E8BFF}, {0xFF34C759}, {0xFF9B6BFF}, {0xFFFF9F0A},
            {0xFFFF5C93}, {0xFF2EC4B6}, {0xFFE6B84C}, {0xFFF4F4F6}, {0xFFFF9F0A, 0xFFFF2D55}, {0xFF2EC4B6, 0xFF3E5BFF}, {0xFFFF5A3C, 0xFFC8102E},
            {0xFF34C759, 0xFF9B6BFF}};

    /** {start, end} colours of a PANEL_COLORS choice (equal for a plain colour), or null for stock. */
    public static int[] panelColors(android.content.Context ctx, int choice, boolean dark) {
        if (choice <= 0 || choice >= PANEL_RGB.length) return null;
        if (choice == 1) {
            int a;
            try { a = ctx.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_500); } catch (Throwable t) { a = 0xFF3E8BFF; }
            return new int[]{a, a};
        }
        int[] c = PANEL_RGB[choice];
        return c.length == 1 ? new int[]{c[0], c[0]} : c.clone();
    }

    /** A readable icon / text colour on {@code bg}: near-black on light colours, white on dark ones. */
    public static int onColor(int bg) {
        double l = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0;
        return l > 0.62 ? 0xFF1C1C1E : 0xFFFFFFFF;
    }

    /** Paints {@code colors} (plain or a start-to-end gradient along x) into whatever {@code draw} paints inside {@code bounds}. */
    public static void tinted(Canvas canvas, RectF bounds, int[] colors, NativeDraw draw) throws Throwable {
        if (bounds.isEmpty()) { draw.draw(); return; }
        int layer = canvas.saveLayer(bounds, null);
        try {
            draw.draw();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            fillPaint(p, bounds, colors);
            canvas.drawRect(bounds, p);
        } finally { canvas.restoreToCount(layer); }
    }

    /** Solid colour or a horizontal gradient over {@code b}. */
    public static void fillPaint(Paint p, RectF b, int[] colors) {
        if (colors[0] == colors[1]) { p.setShader(null); p.setColor(colors[0]); }
        else { p.setColor(0xFFFFFFFF); p.setShader(new LinearGradient(b.left, b.top, b.right, b.top, colors[0], colors[1], Shader.TileMode.CLAMP)); }
    }

    public static int oneUiTrack(boolean dark) { return dark ? 0xFF3A3A3F : 0xFFD6D6DB; }
    public static int oneUiFill(boolean dark) { return dark ? 0xFFEDEDF0 : 0xFFFFFFFF; }
    public static int oneUiCard(boolean dark) { return dark ? 0xFF1E1E22 : 0xFFFFFFFF; }

    /** Corner radii for a nubia-ly shape id on a width x height box (QsShapeBackend.paint). Null when nothing should change. */
    public static float[] shapeCorners(int id, int width, int height) {
        if (id <= 0) return null;
        if (id != 1 && (width <= 0 || height <= 0)) return null;
        float factor = id >= 100 ? (id - 100) / 100f : (id == 1 ? 0f : id == 2 ? 0.24f : 0.5f);
        float radius = Math.min(width, height) * factor;
        return id == 3 ? new float[]{radius, radius, 0, 0, radius, radius, 0, 0} : new float[]{radius, radius, radius, radius, radius, radius, radius, radius};
    }

    // ================================================================================================= outline (QS style 1, brightness style 1)

    /** Outline composition reuses stock drawing, including its state colours, tint and alpha. */
    public static boolean drawOutline(Canvas canvas, GradientDrawable drawable, float density, NativeDraw stock) throws Throwable {
        if (!Float.isFinite(density) || density <= 0) return false;
        Rect bounds = drawable.getBounds(); int shape = drawable.getShape();
        if (bounds.isEmpty() || (shape != GradientDrawable.RECTANGLE && shape != GradientDrawable.OVAL)) return false;
        float stroke = Math.min(2.5f * density, Math.min(bounds.width(), bounds.height()) / 4f);
        float[] radii = drawable.getCornerRadii();
        if (radii == null) { radii = new float[8]; Arrays.fill(radii, drawable.getCornerRadius()); }
        if (radii.length != 8) return false;
        for (float r : radii) if (!Float.isFinite(r) || r < 0) return false;
        RectF outer = new RectF(bounds), inner = new RectF(bounds); inner.inset(stroke, stroke);
        Path ring = new Path(), interior = new Path(); ring.setFillType(Path.FillType.EVEN_ODD);
        if (shape == GradientDrawable.OVAL) { ring.addOval(outer, Path.Direction.CW); interior.addOval(inner, Path.Direction.CW); }
        else { ring.addRoundRect(outer, radii, Path.Direction.CW); float[] inset = radii.clone(); for (int i = 0; i < 8; i++) inset[i] = Math.max(0, inset[i] - stroke); interior.addRoundRect(inner, inset, Path.Direction.CW); }
        ring.addPath(interior);
        int saved = canvas.save();
        try {
            int layer = canvas.saveLayerAlpha(outer, 128);
            try { canvas.clipPath(interior); stock.draw(); } finally { canvas.restoreToCount(layer); }
            canvas.clipPath(ring); stock.draw();
            return true;
        } finally { canvas.restoreToCount(saved); }
    }

    // ================================================================================================= tile finishes

    /** Native colour/alpha is the base; SRC_ATOP confines static shading to that background. Styles 1..7. */
    public static boolean drawTile(int style, Canvas canvas, GradientDrawable tile, float density, NativeDraw stock) throws Throwable {
        if (style == 1) return drawOutline(canvas, tile, density, stock);
        if (oneUiTile(style)) return false;  // colours come from the tile itself (QsHooks), nothing is drawn over it
        if (style < 2 || style >= TILE_STYLES.length || !Float.isFinite(density) || density <= 0 || tile.getBounds().isEmpty() || (tile.getShape() != GradientDrawable.RECTANGLE && tile.getShape() != GradientDrawable.OVAL)) return false;
        RectF b = new RectF(tile.getBounds()); float unit = Math.min(density, Math.min(b.width(), b.height()) / 20f);
        int saved = canvas.saveLayer(b, null); boolean complete = false;
        try {
            stock.draw();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setBlendMode(BlendMode.SRC_ATOP);
            switch (style) {
                case 2: {
                    gradient(canvas, b, p, 0x14ffffff, 0x50000000, false);
                    Path facets = new Path();
                    facets.moveTo(b.left + 3 * unit, b.top + 7 * unit); facets.lineTo(b.centerX() - 2 * unit, b.top + 7 * unit); facets.lineTo(b.centerX() + 2 * unit, b.bottom - 3 * unit); facets.lineTo(b.left + 3 * unit, b.bottom - 3 * unit); facets.close();
                    facets.moveTo(b.centerX() + 2 * unit, b.top + 3 * unit); facets.lineTo(b.right - 3 * unit, b.top + 3 * unit); facets.lineTo(b.right - 3 * unit, b.bottom - 7 * unit); facets.lineTo(b.centerX() + 6 * unit, b.bottom - 7 * unit); facets.close();
                    p.setShader(null); p.setColor(0x30ffffff); canvas.drawPath(facets, p);
                    break;
                }
                case 3: {
                    bevel(canvas, tile, b, p, 2 * unit, 0x60ffffff, 0x60000000);
                    RectF inset = new RectF(b); inset.inset(2 * unit, 2 * unit);
                    Path ring = ring(tile, b, 2 * unit, 4 * unit);
                    p.setShader(new LinearGradient(0, inset.top, 0, inset.bottom, 0x38000000, 0x38ffffff, Shader.TileMode.CLAMP)); canvas.drawPath(ring, p);
                    break;
                }
                case 4: {
                    gradient(canvas, b, p, 0x34ffffff, 0x24000000, true);
                    Path reflection = new Path(); reflection.moveTo(b.left, b.top); reflection.lineTo(b.right, b.top); reflection.lineTo(b.right, b.top + b.height() * 0.22f); reflection.lineTo(b.left, b.top + b.height() * 0.72f); reflection.close();
                    p.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, 0x45ffffff, 0x00ffffff, Shader.TileMode.CLAMP)); canvas.drawPath(reflection, p);
                    break;
                }
                case 5: gradient(canvas, b, p, 0x60000000, 0x00000000, false); break;
                case 6: {
                    p.setColor(0x30000000); canvas.drawRect(b, p); p.setColor(0x48ffffff);
                    float inset = 4 * unit;
                    RectF capsule = new RectF(b.left + inset, b.top + inset, b.left + inset + Math.min(b.height() - 2 * inset, b.width() - 2 * inset), b.bottom - inset);
                    canvas.drawRoundRect(capsule, capsule.height() / 2, capsule.height() / 2, p);
                    break;
                }
                case 7: gradient(canvas, b, p, 0x0cffffff, 0x10000000, false); bevel(canvas, tile, b, p, 1.5f * unit, 0x65ffffff, 0x35000000); break;
                default: return false;
            }
            complete = true;
            return true;
        } finally {
            try { if (!complete) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(saved); }
        }
    }

    private static void gradient(Canvas c, RectF b, Paint p, int first, int last, boolean diagonal) {
        p.setShader(new LinearGradient(b.left, b.top, diagonal ? b.right : b.left, b.bottom, first, last, Shader.TileMode.CLAMP)); c.drawRect(b, p); p.setShader(null);
    }
    private static void bevel(Canvas c, GradientDrawable tile, RectF b, Paint p, float width, int top, int bottom) {
        p.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP)); c.drawPath(ring(tile, b, 0, width), p); p.setShader(null);
    }
    private static Path ring(GradientDrawable tile, RectF bounds, float start, float end) {
        Path p = outline(tile, bounds, start); p.setFillType(Path.FillType.EVEN_ODD); p.addPath(outline(tile, bounds, end)); return p;
    }
    private static Path outline(GradientDrawable tile, RectF bounds, float inset) {
        RectF box = new RectF(bounds); box.inset(inset, inset); Path p = new Path();
        if (box.isEmpty()) return p;
        if (tile.getShape() == GradientDrawable.OVAL) p.addOval(box, Path.Direction.CW);
        else {
            float[] original = tile.getCornerRadii(); float[] radii = original == null ? null : original.clone();
            if (radii == null) { radii = new float[8]; Arrays.fill(radii, tile.getCornerRadius()); }
            for (int i = 0; i < radii.length; i++) radii[i] = Math.max(0, radii[i] - inset);
            p.addRoundRect(box, radii, Path.Direction.CW);
        }
        return p;
    }

    // ================================================================================================= brightness slider

    /**
     * Outline, Neumorph, Thin and One UI treatments of one GradientDrawable layer of the slider. Styles 1..4.
     * {@code fill} tells the track (false) from the progress layer (true); {@code dark} is the host's night mode
     * (both only matter for the One UI style, which repaints the layer in One UI colours; the sun glyph is drawn
     * separately by {@link #paintBrightnessGlyph} once the whole slider has been drawn).
     */
    public static boolean drawBrightness(int style, Canvas canvas, GradientDrawable drawable, float density, boolean fill, boolean dark, NativeDraw nativeDraw) throws Throwable {
        return drawBrightness(style, canvas, drawable, density, fill, dark, null, nativeDraw);
    }

    /** As above; {@code fillColors} (PANEL_COLORS) replaces the fill colour of any style, null = the style's own. */
    public static boolean drawBrightness(int style, Canvas canvas, GradientDrawable drawable, float density, boolean fill, boolean dark, int[] fillColors, NativeDraw nativeDraw) throws Throwable {
        if (fill && fillColors != null && style != ONE_UI_BRIGHTNESS) {
            final GradientDrawable d = drawable;
            NativeDraw styled = style <= 0 ? nativeDraw : () -> { if (!drawBrightness(style, canvas, d, density, nativeDraw)) nativeDraw.draw(); };
            tinted(canvas, new RectF(drawable.getBounds()), fillColors, styled);
            return true;
        }
        if (style == ONE_UI_BRIGHTNESS) {
            Rect bounds = drawable.getBounds();
            if (bounds.isEmpty() || drawable.getShape() != GradientDrawable.RECTANGLE) return false;
            float[] radii = drawable.getCornerRadii();
            if (radii == null) { radii = new float[8]; Arrays.fill(radii, drawable.getCornerRadius()); }
            if (radii.length != 8) return false;
            boolean round = true;
            for (float r : radii) if (!Float.isFinite(r) || r < 0) return false; else if (r < bounds.height() / 2f) round = false;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(fill ? oneUiFill(dark) : oneUiTrack(dark));
            RectF b = new RectF(bounds);
            if (fill && fillColors != null) fillPaint(p, b, fillColors);
            // One UI tracks are fully rounded; keep a flatter native radius only when the firmware uses one deliberately.
            if (round || radii[0] <= 0) canvas.drawRoundRect(b, b.height() / 2, b.height() / 2, p);
            else { Path path = new Path(); path.addRoundRect(b, radii, Path.Direction.CW); canvas.drawPath(path, p); }
            return true;
        }
        return drawBrightness(style, canvas, drawable, density, nativeDraw);
    }

    /**
     * One UI 8 slider in one pass (called for the track layer; the fill layer is then skipped): a fully rounded track
     * and a fully rounded fill from the start edge, never shorter than the track is thick. {@code track} is in the
     * slider's own (unrotated) coordinates, length along x.
     */
    public static void drawOneUi8Slider(Canvas canvas, RectF track, float fraction, boolean dark) { drawOneUi8Slider(canvas, track, fraction, dark, null); }

    /** As above with the fill in {@code fillColors} (PANEL_COLORS), null = One UI's light fill. */
    public static void drawOneUi8Slider(Canvas canvas, RectF track, float fraction, boolean dark, int[] fillColors) {
        if (track == null || track.isEmpty() || !Float.isFinite(fraction)) return;
        float r = Math.min(track.width(), track.height()) / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(oneUiTrack(dark));
        canvas.drawRoundRect(track, r, r, p);
        float len = oneUi8FillLength(track, fraction);
        p.setColor(oneUiFill(dark));
        if (fillColors != null) fillPaint(p, track, fillColors);
        canvas.drawRoundRect(new RectF(track.left, track.top, track.left + len, track.bottom), r, r, p);
    }

    /**
     * Length of the One UI 8 fill: a circle (the track's thickness) at 0 % that grows to the whole track at 100 %, like
     * Samsung's slider. (It used to be max(circle, fraction x length): on the short vertical Control Center bars the
     * circle is 44 % of the bar, so every level below that looked like 0 and the bar seemed to reset after dragging.)
     */
    public static float oneUi8FillLength(RectF track, float fraction) {
        float f = Float.isFinite(fraction) ? Math.max(0f, Math.min(1f, fraction)) : 0f;
        float knob = Math.min(track.height(), track.width());
        return knob + (track.width() - knob) * f;
    }

    /** Icon colour over the One UI fill (the fill is light in both modes). */
    public static int oneUiIconOverFill(boolean dark) { return dark ? 0xFF2C2C2E : 0xFF3C3C40; }

    /** Small sun inside the start of the track: dark over the (light) fill, light over the bare track. */
    public static void paintBrightnessGlyph(Canvas canvas, RectF track, float fraction, boolean dark) {
        if (track == null || track.isEmpty() || !Float.isFinite(fraction)) return;
        float h = track.height(), cx = track.left + h / 2f, cy = track.centerY(), r = h * 0.16f;
        if (r < 1.5f) return;
        boolean overFill = track.left + track.width() * Math.max(0f, Math.min(1f, fraction)) >= cx + r * 2.1f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(overFill ? (dark ? 0xFF2C2C2E : 0xFF3C3C40) : (dark ? 0xFFCFCFD4 : 0xFF6E6E73));
        canvas.drawCircle(cx, cy, r, p);
        p.setStrokeWidth(Math.max(1.5f, r * 0.3f)); p.setStrokeCap(Paint.Cap.ROUND);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            canvas.drawLine((float) (cx + Math.cos(a) * r * 1.45), (float) (cy + Math.sin(a) * r * 1.45), (float) (cx + Math.cos(a) * r * 1.95), (float) (cy + Math.sin(a) * r * 1.95), p);
        }
    }

    /** Outline, Neumorph and Thin treatments of one GradientDrawable layer of the slider. Styles 1..3. */
    public static boolean drawBrightness(int style, Canvas canvas, GradientDrawable drawable, float density, NativeDraw nativeDraw) throws Throwable {
        if (style == 1) {
            if (drawable.getBounds().isEmpty()) return false;
            int saved = canvas.saveLayer(new RectF(drawable.getBounds()), null); boolean complete = false;
            try { if (!drawOutline(canvas, drawable, density, nativeDraw)) return false; complete = true; return true; }
            finally { try { if (!complete) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(saved); } }
        }
        if (style == 2) return drawTile(3, canvas, drawable, density, nativeDraw);
        if (style != 3 || !Float.isFinite(density) || density <= 0 || drawable.getShape() != GradientDrawable.RECTANGLE || drawable.getBounds().isEmpty()) return false;
        RectF bounds = new RectF(drawable.getBounds()); float height = Math.min(bounds.height(), 4 * density);
        RectF line = new RectF(bounds.left, bounds.centerY() - height / 2, bounds.right, bounds.centerY() + height / 2);
        int layer = canvas.saveLayer(bounds, null); boolean complete = false;
        try {
            Path clip = new Path(); clip.addRoundRect(line, height / 2, height / 2, Path.Direction.CW); canvas.clipPath(clip);
            nativeDraw.draw(); complete = true; return true;
        } finally { try { if (!complete) canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(layer); } }
    }

    // ================================================================================================= preview painters (app side)

    /** nubia-ly QsStylePreview: two sample tiles (light and dark) with the finish applied. */
    public static void paintTilePreview(Canvas canvas, int style, int w, int h, float density, int gap) {
        if (w < 12 || h < 12) return;
        if (style == ONE_UI_TILE) { paintOneUiTilePreview(canvas, w, h); return; }
        if (style == ONE_UI8_TILE) { paintOneUi8TilePreview(canvas, w, h); return; }
        for (int i = 0; i < 2; i++) {
            final GradientDrawable tile = new GradientDrawable();
            tile.setColor(i == 0 ? 0xffd3cbef : 0xff73707e); tile.setCornerRadius(h / 5f);
            tile.setBounds(gap, i * h / 2 + gap, w - gap, (i + 1) * h / 2 - gap);
            final Canvas c = canvas;
            if (style <= 0) { tile.draw(c); continue; }
            try { if (!drawTile(style, c, tile, density, () -> tile.draw(c))) tile.draw(c); } catch (Throwable t) { tile.draw(c); }
        }
    }

    /** One UI sample: a dark panel strip with an active (blue) and an inactive (grey) circular icon-only tile. */
    public static void paintOneUiTilePreview(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pad = Math.min(w, h) * 0.06f;
        RectF panel = new RectF(pad, pad, w - pad, h - pad);
        p.setColor(0xFF121214); c.drawRoundRect(panel, pad * 2, pad * 2, p);
        float d = Math.min(panel.height() * 0.62f, panel.width() * 0.36f), cy = panel.centerY();
        for (int i = 0; i < 2; i++) {
            float cx = panel.left + panel.width() * (i == 0 ? 0.3f : 0.7f);
            p.setColor(oneUiBackground(i == 0 ? 2 : 1, true)); c.drawCircle(cx, cy, d / 2, p);
            p.setColor(oneUiIcon(i == 0 ? 2 : 1, true)); paintGlyph(c, p, cx, cy, d * 0.17f, i == 0);
        }
    }

    /** One UI 8 sample: frosted panel, a translucent pill with its icon in a white circle, an on (white) and an off round tile. */
    public static void paintOneUi8TilePreview(Canvas c, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pad = Math.min(w, h) * 0.06f;
        RectF panel = new RectF(pad, pad, w - pad, h - pad);
        p.setShader(new LinearGradient(0, panel.top, 0, panel.bottom, 0xFF3B5566, 0xFF22303A, Shader.TileMode.CLAMP));
        c.drawRoundRect(panel, pad * 2, pad * 2, p);
        p.setShader(null);
        float pillH = panel.height() * 0.36f;
        RectF pill = new RectF(panel.left + pad, panel.top + pad, panel.right - pad, panel.top + pad + pillH);
        p.setColor(oneUi8Background(2, true, true)); c.drawRoundRect(pill, pillH / 2, pillH / 2, p);
        float r = pillH * 0.34f, cx = pill.left + pillH / 2, cy = pill.centerY();
        p.setColor(oneUi8IconCircle(2, true)); c.drawCircle(cx, cy, r, p);
        p.setColor(oneUi8Icon(2, true)); c.drawCircle(cx, cy, r * 0.36f, p);
        p.setColor(0xFFFFFFFF); c.drawRoundRect(new RectF(cx + r * 1.6f, cy - r * 0.5f, cx + r * 4.6f, cy - r * 0.1f), r * 0.2f, r * 0.2f, p);
        p.setColor(0x99FFFFFF); c.drawRoundRect(new RectF(cx + r * 1.6f, cy + r * 0.15f, cx + r * 3.6f, cy + r * 0.5f), r * 0.2f, r * 0.2f, p);
        float d = Math.min(panel.height() - pillH - pad * 3, panel.width() * 0.3f), y = (pill.bottom + panel.bottom) / 2;
        for (int i = 0; i < 2; i++) {
            float x = panel.left + panel.width() * (i == 0 ? 0.3f : 0.7f);
            p.setColor(oneUi8Background(i == 0 ? 2 : 1, false, true)); c.drawCircle(x, y, d / 2, p);
            p.setColor(oneUi8Icon(i == 0 ? 2 : 1, true)); paintGlyph(c, p, x, y, d * 0.15f, i == 0);
        }
    }

    /** Simple icon stand-in: a disc with a ring (active) or a plain disc (inactive). */
    private static void paintGlyph(Canvas c, Paint p, float cx, float cy, float r, boolean ring) {
        c.drawCircle(cx, cy, r, p);
        if (!ring) return;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.5f, r * 0.35f));
        c.drawCircle(cx, cy, r * 1.9f, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** nubia-ly BrightnessStylePreview: a track and a clipped fill with the style applied. */
    public static void paintBrightnessPreview(Canvas c, int style, int w, int h, float density, int inset) {
        if (w < 12 || h < 12) return;
        if (style == ONE_UI8_BRIGHTNESS) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            RectF panel = new RectF(inset / 2f, inset / 2f, w - inset / 2f, h - inset / 2f);
            p.setColor(0xFF121214); c.drawRoundRect(panel, inset, inset, p);
            RectF track = new RectF(inset * 2, h * 0.26f, w - inset * 2, h * 0.74f);
            drawOneUi8Slider(c, track, 0.55f, true);
            paintBrightnessGlyph(c, track, 1f, true);
            return;
        }
        if (style == ONE_UI_BRIGHTNESS) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            RectF panel = new RectF(inset / 2f, inset / 2f, w - inset / 2f, h - inset / 2f);
            p.setColor(0xFF121214); c.drawRoundRect(panel, inset, inset, p);
            RectF track = new RectF(inset * 2, h * 0.3f, w - inset * 2, h * 0.7f);
            p.setColor(oneUiTrack(true)); c.drawRoundRect(track, track.height() / 2, track.height() / 2, p);
            int save = c.save();
            try { c.clipRect(track.left, track.top, track.left + track.width() * 0.6f, track.bottom); p.setColor(oneUiFill(true)); c.drawRoundRect(track, track.height() / 2, track.height() / 2, p); } finally { c.restoreToCount(save); }
            paintBrightnessGlyph(c, track, 0.6f, true);
            return;
        }
        for (int i = 0; i < 2; i++) {
            final GradientDrawable d = new GradientDrawable();
            d.setColor(i == 0 ? 0xff73707e : 0xffd3cbef); d.setCornerRadius(h / 3f); d.setBounds(inset, h / 4, w - inset, h * 3 / 4);
            int save = c.save();
            if (i == 1) c.clipRect(inset, 0, w * 0.6f, h);
            try { if (style <= 0 || !drawBrightness(style, c, d, density, () -> d.draw(c))) d.draw(c); } catch (Throwable t) { d.draw(c); } finally { c.restoreToCount(save); }
        }
    }

    /** One sample tile (wide or circle-sized) in the chosen shape; the One UI pill is shown in One UI colours. */
    public static void paintShapePreview(Canvas c, int shapeId, int w, int h, boolean wide, int gap) {
        if (w < 12 || h < 12) return;
        int tw = wide ? w - 2 * gap : Math.min(w, h) - 2 * gap, th = Math.min(h, wide ? h : tw + 2 * gap) - 2 * gap;
        int left = (w - tw) / 2, top = (h - th) / 2;
        boolean oneUi = shapeId == ONE_UI_PILL_SHAPE;
        GradientDrawable d = new GradientDrawable(); d.setColor(oneUi ? ONE_UI_BLUE : 0xffd3cbef);
        float[] corners = shapeCorners(shapeId, tw, th);
        if (corners == null) { if (wide) d.setCornerRadius(th * 0.24f); else { d.setShape(GradientDrawable.OVAL); } }
        else { d.setShape(GradientDrawable.RECTANGLE); d.setCornerRadii(corners); }
        d.setBounds(left, top, left + tw, top + th); d.draw(c);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(oneUi ? 0xffffffff : 0xff353778);
        c.drawCircle(left + (wide ? th * 0.5f : tw / 2f), top + th / 2f, th * 0.16f, p);
        if (wide) { p.setStrokeWidth(Math.max(2, th * 0.08f)); p.setStrokeCap(Paint.Cap.ROUND); c.drawLine(left + th * 0.95f, top + th / 2f, left + tw * 0.75f, top + th / 2f, p); }
    }

    /**
     * Tiny schematic of the shade: optional large tiles on top (topLayout 0 stock 2x2, 1 = 2 in a row, 2 = 6 in 3 rows,
     * 3 = 4 in a column), a brightness bar and a cols x rows small-tile grid. brightnessPos 0 between, 1 above all, 2 below.
     * labelPos: 0 label under the dot, 1 above, 2 no label. Pass cols/rows 0 for the stock 4 x 3.
     */
    public static void paintGridPreview(Canvas c, int w, int h, int cols, int rows, int topLayout, int brightnessPos, int labelPos, boolean landscape, int accent, int dim) {
        if (w < 20 || h < 20) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pad = Math.min(w, h) * 0.08f;
        RectF frame = new RectF(pad, pad, w - pad, h - pad);
        if (landscape && frame.height() > frame.width() * 0.6f) { float extra = (frame.height() - frame.width() * 0.6f) / 2; frame.top += extra; frame.bottom -= extra; }
        p.setColor(dim); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.5f, pad * 0.2f));
        c.drawRoundRect(frame, pad, pad, p);
        p.setStyle(Paint.Style.FILL);
        float inner = pad * 0.7f;
        RectF body = new RectF(frame.left + inner, frame.top + inner, frame.right - inner, frame.bottom - inner);
        if (cols <= 0) cols = landscape ? 6 : 4;
        if (rows <= 0) rows = landscape ? 2 : 3;
        int topCols = topLayout == 1 ? 2 : topLayout == 2 ? 2 : topLayout == 3 ? 1 : 2, topRows = topLayout == 1 ? 1 : topLayout == 2 ? 3 : topLayout == 3 ? 4 : 2;
        float gapY = body.height() * 0.03f, gapX = body.width() * 0.03f;
        float barH = body.height() * 0.07f;
        float unit = (body.height() - barH - 4 * gapY) / (topRows * 1.15f + rows);
        float topH = unit * 1.15f * topRows, gridH = unit * rows;
        float y = body.top;
        float[] blocks = new float[3]; // heights in order: 0 top, 1 bar, 2 grid
        int[] order = brightnessPos == 1 ? new int[]{1, 0, 2} : brightnessPos == 2 ? new int[]{0, 2, 1} : new int[]{0, 1, 2};
        for (int k = 0; k < 3; k++) {
            int block = order[k];
            if (block == 0) {
                float cw = (body.width() - gapX * (topCols - 1)) / topCols, ch = (topH - gapY * (topRows - 1)) / topRows;
                float left0 = topLayout == 3 ? body.left + (body.width() - cw * 0.6f) / 2 : body.left;
                for (int r = 0; r < topRows; r++) for (int col = 0; col < topCols; col++) {
                    float cwE = topLayout == 3 ? cw * 0.6f : cw;
                    RectF t = new RectF(left0 + col * (cw + gapX), y + r * (ch + gapY), left0 + col * (cw + gapX) + cwE, y + r * (ch + gapY) + ch);
                    p.setColor(accent); c.drawRoundRect(t, ch * 0.3f, ch * 0.3f, p);
                }
                y += topH + gapY;
            } else if (block == 1) {
                p.setColor(dim); RectF bar = new RectF(body.left, y, body.right, y + barH); c.drawRoundRect(bar, barH / 2, barH / 2, p);
                p.setColor(accent); c.drawRoundRect(new RectF(bar.left, bar.top, bar.left + bar.width() * 0.55f, bar.bottom), barH / 2, barH / 2, p);
                y += barH + gapY;
            } else {
                float cw = (body.width() - gapX * (cols - 1)) / cols, ch = (gridH - gapY * (rows - 1)) / rows;
                float dot = Math.min(cw, ch) * (labelPos == 2 ? 0.62f : 0.48f);
                for (int r = 0; r < rows; r++) for (int col = 0; col < cols; col++) {
                    float cx = body.left + col * (cw + gapX) + cw / 2, cy0 = y + r * (ch + gapY);
                    float labelH = labelPos == 2 ? 0 : ch * 0.14f;
                    float cyDot = labelPos == 1 ? cy0 + labelH + (ch - labelH) / 2 : cy0 + (ch - labelH) / 2;
                    p.setColor(dim); c.drawCircle(cx, cyDot, dot / 2, p);
                    if (labelPos != 2) {
                        float ly = labelPos == 1 ? cy0 + labelH * 0.5f : cy0 + ch - labelH * 0.5f;
                        p.setColor(accent); c.drawRoundRect(new RectF(cx - cw * 0.3f, ly - labelH * 0.25f, cx + cw * 0.3f, ly + labelH * 0.25f), labelH, labelH, p);
                    }
                }
                y += gridH + gapY;
            }
        }
    }

    /** Brightness bar with or without the two sun icons. */
    public static void paintSunPreview(Canvas c, int w, int h, boolean hideIcons, int accent, int dim) {
        if (w < 20 || h < 12) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pad = w * 0.08f, icon = Math.min(h * 0.3f, w * 0.08f), barH = Math.min(h * 0.22f, w * 0.06f);
        float left = hideIcons ? pad : pad + icon * 2.2f, right = hideIcons ? w - pad : w - pad - icon * 2.2f;
        p.setColor(dim); c.drawRoundRect(new RectF(left, h / 2f - barH / 2, right, h / 2f + barH / 2), barH / 2, barH / 2, p);
        p.setColor(accent); c.drawRoundRect(new RectF(left, h / 2f - barH / 2, left + (right - left) * 0.55f, h / 2f + barH / 2), barH / 2, barH / 2, p);
        if (hideIcons) return;
        p.setColor(dim);
        for (int i = 0; i < 2; i++) {
            float cx = i == 0 ? pad + icon * 0.8f : w - pad - icon * 0.8f, r = i == 0 ? icon * 0.35f : icon * 0.55f;
            c.drawCircle(cx, h / 2f, r, p);
            p.setStrokeWidth(Math.max(1.5f, r * 0.25f)); p.setStrokeCap(Paint.Cap.ROUND);
            for (int k = 0; k < 8; k++) { double a = Math.PI / 4 * k; c.drawLine((float) (cx + Math.cos(a) * r * 1.4), (float) (h / 2f + Math.sin(a) * r * 1.4), (float) (cx + Math.cos(a) * r * 1.9), (float) (h / 2f + Math.sin(a) * r * 1.9), p); }
        }
    }
    /**
     * Control Center first block for QsFeatures.CC_SLIDER_POS: big tiles (accent pills), the brightness and volume bars
     * (One UI track + fill) and, for the bottom option, the small round tiles between them.
     */
    public static void paintCcSliderPreview(Canvas c, int w, int h, int pos, int accent) {
        if (w < 24 || h < 24) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pad = Math.min(w, h) * 0.06f;
        RectF panel = new RectF(pad / 2, pad / 2, w - pad / 2, h - pad / 2);
        p.setColor(0xFF121214); c.drawRoundRect(panel, pad * 1.5f, pad * 1.5f, p);
        float left = panel.left + pad, right = panel.right - pad, top = panel.top + pad, bottom = panel.bottom - pad;
        float gap = pad * 0.7f, colW = (right - left - gap) / 2;
        if (pos <= 1) {
            // three big pills in one column, two vertical bars + one pill in the other (mirrored for 1)
            float rowH = (bottom - top - gap * 2) / 3;
            float tilesX = pos == 0 ? left : left + colW + gap, sideX = pos == 0 ? left + colW + gap : left;
            p.setColor(accent);
            for (int i = 0; i < 3; i++) { float y = top + i * (rowH + gap); c.drawRoundRect(new RectF(tilesX, y, tilesX + colW, y + rowH), rowH / 2, rowH / 2, p); }
            float y2 = top + 2 * (rowH + gap); c.drawRoundRect(new RectF(sideX, y2, sideX + colW, y2 + rowH), rowH / 2, rowH / 2, p);
            float barW = (colW - gap) / 2, barH = rowH * 2 + gap;
            for (int i = 0; i < 2; i++) {
                float x = sideX + i * (barW + gap);
                RectF bar = new RectF(x, top, x + barW, top + barH);
                p.setColor(oneUiTrack(true)); c.drawRoundRect(bar, barW / 2, barW / 2, p);
                p.setColor(oneUiFill(true)); c.drawRoundRect(new RectF(x, top + barH * (i == 0 ? 0.45f : 0.3f), x + barW, top + barH), barW / 2, barW / 2, p);
            }
            return;
        }
        int rows = pos == 4 ? 5 : 4;   // two pill rows + two bars (+ a row of round tiles for the bottom option)
        float unit = (bottom - top - gap * (rows - 1)) / rows;
        float y = top;
        java.util.List<Integer> order = new java.util.ArrayList<>();
        if (pos == 3) { order.add(1); order.add(1); order.add(0); order.add(0); }
        else if (pos == 4) { order.add(0); order.add(0); order.add(2); order.add(1); order.add(1); }
        else { order.add(0); order.add(0); order.add(1); order.add(1); }
        for (int kind : order) {
            if (kind == 0) {
                p.setColor(accent);
                c.drawRoundRect(new RectF(left, y, left + colW, y + unit), unit / 2, unit / 2, p);
                c.drawRoundRect(new RectF(left + colW + gap, y, right, y + unit), unit / 2, unit / 2, p);
            } else if (kind == 1) {
                RectF bar = new RectF(left, y + unit * 0.12f, right, y + unit * 0.88f);
                drawOneUi8Slider(c, bar, 0.55f, true);
            } else {
                p.setColor(0xFF3A3A3F);
                float d = Math.min(unit, (right - left - gap * 4) / 5);
                for (int i = 0; i < 5; i++) { float cx = left + (right - left) * (i + 0.5f) / 5; c.drawCircle(cx, y + unit / 2, d / 2 * 0.8f, p); }
            }
            y += unit + gap;
        }
    }

    /**
     * Control Center layout (QsFeatures.QS_CC_LAYOUT) schematic. 0 = the stock RedMagic page (big clock, 2 x 2 pills,
     * vertical bars, small tiles); 1 = One UI 8 (header buttons, Wi-Fi | Bluetooth pills, tile card with handle,
     * slider card with round buttons, media card, 2 x 2 shortcut pills).
     */
    public static void paintCcLayoutPreview(Canvas c, int w, int h, int layout) {
        if (w < 24 || h < 24) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float u = Math.min(w, h) / 40f;
        RectF panel = new RectF(u, u, w - u, h - u);
        p.setShader(new LinearGradient(0, panel.top, 0, panel.bottom, 0xFF3B5566, 0xFF1D2A33, Shader.TileMode.CLAMP));
        c.drawRoundRect(panel, 3 * u, 3 * u, p);
        p.setShader(null);
        float left = panel.left + 2.5f * u, right = panel.right - 2.5f * u, y = panel.top + 2.5f * u, gap = 1.6f * u;
        if (layout != 1) {
            p.setColor(0xFFFFFFFF); c.drawRoundRect(new RectF(left, y, left + 9 * u, y + 4 * u), u, u, p);
            y += 6 * u;
            float colW = (right - left - gap) / 2, rowH = 4.2f * u;
            p.setColor(ONE_UI_BLUE);
            for (int i = 0; i < 3; i++) c.drawRoundRect(new RectF(left, y + i * (rowH + gap), left + colW, y + i * (rowH + gap) + rowH), rowH / 2, rowH / 2, p);
            p.setColor(0xFF3A3A3F);
            c.drawRoundRect(new RectF(left + colW + gap, y + 2 * (rowH + gap), right, y + 2 * (rowH + gap) + rowH), rowH / 2, rowH / 2, p);
            float barW = (colW - gap) / 2, barH = rowH * 2 + gap;
            for (int i = 0; i < 2; i++) {
                float x = left + colW + gap + i * (barW + gap);
                p.setColor(0x55FFFFFF); c.drawRoundRect(new RectF(x, y, x + barW, y + barH), barW / 3, barW / 3, p);
                p.setColor(0xFFF4F4F6); c.drawRoundRect(new RectF(x, y + barH * (i == 0 ? 0.5f : 0.35f), x + barW, y + barH), barW / 3, barW / 3, p);
            }
            y += 3 * (rowH + gap) + gap;
            p.setColor(0xFF3A3A3F);
            float d = (right - left) / 5 * 0.7f;
            for (int r = 0; r < 3 && y + d <= panel.bottom - u; r++, y += d + gap)
                for (int i = 0; i < 5; i++) c.drawCircle(left + (right - left) * (i + 0.5f) / 5, y + d / 2, d / 2, p);
            return;
        }
        int card = oneUi8Card(true), soft = oneUi8Soft(true);
        // header buttons
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < 3; i++) c.drawCircle(right - u - i * 4f * u, y + u, 0.9f * u, p);
        y += 3.5f * u;
        // Wi-Fi | Bluetooth pills
        float colW = (right - left - gap) / 2, pillH = 4.6f * u;
        for (int i = 0; i < 2; i++) {
            float x = left + i * (colW + gap);
            p.setColor(oneUi8Background(2, true, true)); c.drawRoundRect(new RectF(x, y, x + colW, y + pillH), pillH / 2, pillH / 2, p);
            p.setColor(0xFFF4F4F6); c.drawCircle(x + pillH / 2, y + pillH / 2, pillH * 0.32f, p);
            p.setColor(0xCCFFFFFF); c.drawRoundRect(new RectF(x + pillH, y + pillH * 0.4f, x + colW * 0.8f, y + pillH * 0.6f), u * 0.4f, u * 0.4f, p);
        }
        y += pillH + gap;
        // tile card with handle
        float d = (right - left - 4 * u) / 5 * 0.62f, cardH = 2 * d + 4.5f * u;
        p.setColor(card); c.drawRoundRect(new RectF(left, y, right, y + cardH), 2.6f * u, 2.6f * u, p);
        for (int r = 0; r < 2; r++)
            for (int i = 0; i < 5; i++) {
                boolean on = (r * 5 + i) % 3 == 1;
                p.setColor(oneUi8Background(on ? 2 : 1, false, true));
                c.drawCircle(left + 2 * u + (right - left - 4 * u) * (i + 0.5f) / 5, y + 1.5f * u + d / 2 + r * (d + u), d / 2, p);
            }
        p.setColor(0x66FFFFFF); c.drawRoundRect(new RectF((left + right) / 2 - 2 * u, y + cardH - 1.4f * u, (left + right) / 2 + 2 * u, y + cardH - 0.9f * u), u, u, p);
        y += cardH + gap;
        // slider card
        float barH = 3.2f * u, sliderCardH = 2 * barH + 3 * u;
        p.setColor(card); c.drawRoundRect(new RectF(left, y, right, y + sliderCardH), 2.6f * u, 2.6f * u, p);
        for (int i = 0; i < 2; i++) {
            float by = y + u + i * (barH + u);
            RectF bar = new RectF(left + u, by, right - u - barH - u, by + barH);
            drawOneUi8Slider(c, bar, i == 0 ? 0.25f : 0.4f, true);
            p.setColor(0xFFF4F4F6); c.drawCircle(right - u - barH / 2, by + barH / 2, barH / 2, p);
        }
        y += sliderCardH + gap;
        // media card
        float mediaH = 4f * u;
        if (y + mediaH <= panel.bottom - u) {
            p.setColor(card); c.drawRoundRect(new RectF(left, y, right, y + mediaH), mediaH / 2, mediaH / 2, p);
            p.setColor(0xCCFFFFFF); c.drawRoundRect(new RectF(left + 2 * u, y + mediaH * 0.42f, left + 9 * u, y + mediaH * 0.58f), u * 0.4f, u * 0.4f, p);
            p.setColor(soft); c.drawRoundRect(new RectF(right - 11 * u, y + 0.7f * u, right - 0.7f * u, y + mediaH - 0.7f * u), mediaH / 2, mediaH / 2, p);
            y += mediaH + gap;
        }
        // shortcut pills
        float sh = 4f * u;
        for (int r = 0; r < 2 && y + sh <= panel.bottom - u; r++, y += sh + gap)
            for (int i = 0; i < 2; i++) {
                float x = left + i * (colW + gap);
                p.setColor(card); c.drawRoundRect(new RectF(x, y, x + colW, y + sh), sh / 2, sh / 2, p);
                p.setColor(soft); c.drawCircle(x + sh / 2, y + sh / 2, sh * 0.32f, p);
                p.setColor(0xCCFFFFFF); c.drawRoundRect(new RectF(x + sh, y + sh * 0.42f, x + colW * 0.78f, y + sh * 0.58f), u * 0.4f, u * 0.4f, p);
            }
    }
}
