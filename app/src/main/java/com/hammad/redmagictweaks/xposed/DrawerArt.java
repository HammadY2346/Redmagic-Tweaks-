package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.hammad.redmagictweaks.Previews;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure drawing code + choice tables for OUR app-drawer folders (the folder cell in the paged drawer and the opened
 * folder view). Used by DrawerEdit inside the launcher and by the app for the picker previews. No Xposed imports.
 *
 * Keys are read by LauncherHooks/DrawerEdit through {@code Cfg.choice(KEY_*)}; the app registers the previews with
 * {@link #registerPreviews()} (call it from the static initializer of the Features class that declares the keys).
 */
public final class DrawerArt {
    private DrawerArt() {}

    // ------------------------------------------------------------------ keys (index 0 = the look before these options existed)

    public static final String KEY_SHAPE = "launcher_folder_shape";
    public static final String KEY_COLOR = "launcher_folder_color";
    public static final String KEY_OPACITY = "launcher_folder_opacity";
    public static final String KEY_GRID = "launcher_folder_grid";
    public static final String KEY_LABEL = "launcher_folder_label";
    public static final String KEY_VIEW_BG = "launcher_folder_view_bg";
    public static final String KEY_VIEW_RADIUS = "launcher_folder_view_radius";
    public static final String KEY_VIEW_COLS = "launcher_folder_view_cols";
    public static final String KEY_VIEW_NO_NAMES = "launcher_folder_view_no_names";      // SWITCH: hide app names in the opened folder

    public static final String[] SHAPES = {"Rounded square", "Circle", "Squircle", "Square", "Leaf", "One UI"};
    public static final String[] COLORS = {"Auto (translucent white)", "White", "Black", "Grey", "Red", "Orange", "Amber", "Green", "Teal", "Blue", "Purple", "Pink", "Transparent"};
    private static final int[] COLOR_ARGB = {0x66FFFFFF, 0xFFFFFFFF, 0xFF000000, 0xFF5F6368, 0xFFE53935, 0xFFFB8C00, 0xFFFFB300, 0xFF43A047, 0xFF00897B, 0xFF1E88E5, 0xFF8E24AA, 0xFFD81B60, 0x00000000};
    public static final String[] OPACITIES = {"100 %", "80 %", "60 %", "40 %"};
    private static final float[] OPACITY_F = {1f, 0.8f, 0.6f, 0.4f};
    public static final String[] GRIDS = {"2 × 2 mini icons", "3 × 3 mini icons", "Big first icon + count"};
    public static final String[] LABELS = {"Show (app label colour)", "Show, white", "Show, black", "Hide"};
    public static final String[] VIEW_BGS = {"Dark card", "Light card", "Translucent dark"};
    public static final String[] VIEW_RADII = {"Medium", "Small", "Large", "Extra large"};
    private static final float[] VIEW_RADIUS_DP = {28f, 14f, 40f, 56f};
    public static final String[] VIEW_COLS = {"4 columns", "3 columns", "5 columns"};
    private static final int[] VIEW_COLS_N = {4, 3, 5};

    /** Resolved folder-cell style (global choices, optionally overridden per folder). */
    public static final class Style {
        public int shape, color, opacity, grid;
        public Style(int shape, int color, int opacity, int grid) {
            this.shape = clamp(shape, SHAPES.length); this.color = clamp(color, COLORS.length);
            this.opacity = clamp(opacity, OPACITIES.length); this.grid = clamp(grid, GRIDS.length);
        }
        public String key() { return shape + ":" + color + ":" + opacity + ":" + grid; }
    }

    static int clamp(int i, int n) { return i < 0 || i >= n ? 0 : i; }

    /** "One UI" shape (index 5): a full rounded square whose corner radius is ~26 % of the side (Samsung squircle-ish);
     *  its mini icons are clipped to the same proportion. */
    private static final float ONE_UI_RADIUS = 0.26f;

    public static int tileColor(Style s) {
        int c = COLOR_ARGB[s.color];
        int a = Math.round(Color.alpha(c) * OPACITY_F[s.opacity]);
        return (c & 0x00FFFFFF) | (a << 24);
    }

    /** Text colour that reads on this tile (used for the count badge). */
    private static boolean lightTile(Style s) { int c = COLOR_ARGB[s.color]; return s.color == 0 || s.color == 12 || Color.luminance(c | 0xFF000000) > 0.55f; }

    public static float viewRadiusDp(int choice) { return VIEW_RADIUS_DP[clamp(choice, VIEW_RADII.length)]; }
    public static int viewColumns(int choice) { return VIEW_COLS_N[clamp(choice, VIEW_COLS.length)]; }
    /** Opened-folder card colour / text colour for a background choice. */
    public static int viewCardColor(int choice) { switch (clamp(choice, VIEW_BGS.length)) { case 1: return 0xF7F5F5F7; case 2: return 0xB3101014; default: return 0xF2202124; } }
    public static int viewTextColor(int choice) { return clamp(choice, VIEW_BGS.length) == 1 ? 0xFF202124 : 0xFFFFFFFF; }

    // ------------------------------------------------------------------ shapes

    /** Outline of the folder tile inside r. */
    public static Path tilePath(RectF r, int shape) {
        Path p = new Path();
        float w = r.width(), h = r.height(), m = Math.min(w, h);
        switch (shape) {
            case 1: p.addOval(r, Path.Direction.CW); break;
            case 2: squircle(p, r); break;
            case 3: p.addRoundRect(r, m * 0.06f, m * 0.06f, Path.Direction.CW); break;
            case 4: { float big = m * 0.5f, small = m * 0.08f; float[] radii = {big, big, small, small, big, big, small, small}; p.addRoundRect(r, radii, Path.Direction.CW); break; }
            case 5: p.addRoundRect(r, m * ONE_UI_RADIUS, m * ONE_UI_RADIUS, Path.Direction.CW); break;   // One UI: full rounded square, large radius
            default: p.addRoundRect(r, m * 0.24f, m * 0.24f, Path.Direction.CW); break;
        }
        return p;
    }

    /** Superellipse (|x|^n + |y|^n = 1, n = 5) built from cubic segments. */
    private static void squircle(Path p, RectF r) {
        float cx = r.centerX(), cy = r.centerY(), rx = r.width() / 2f, ry = r.height() / 2f;
        final float k = 0.92f;     // control-point handle length for a smooth n≈5 superellipse
        p.moveTo(cx + rx, cy);
        p.cubicTo(cx + rx, cy + ry * k, cx + rx * k, cy + ry, cx, cy + ry);
        p.cubicTo(cx - rx * k, cy + ry, cx - rx, cy + ry * k, cx - rx, cy);
        p.cubicTo(cx - rx, cy - ry * k, cx - rx * k, cy - ry, cx, cy - ry);
        p.cubicTo(cx + rx * k, cy - ry, cx + rx, cy - ry * k, cx + rx, cy);
        p.close();
    }

    // ------------------------------------------------------------------ folder tile

    /**
     * Draw a folder tile of the given style into {@code size}² px. {@code icons} may be shorter than needed or contain
     * nulls (a coloured placeholder square is drawn instead); {@code total} is the folder's app count for the badge.
     */
    public static Bitmap folderIcon(List<Bitmap> icons, int total, int size, Style s) {
        if (size <= 0) size = 96;
        if (icons == null) icons = new ArrayList<>();
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float inset = size * 0.02f;
        RectF tile = new RectF(inset, inset, size - inset, size - inset);
        Path path = tilePath(tile, s.shape);
        int fill = tileColor(s);
        if (Color.alpha(fill) > 0) {
            p.setColor(fill);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, size * 0.015f));
            p.setColor(lightTile(s) ? 0x33000000 : 0x33FFFFFF);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
        }
        boolean round = s.shape == 1;
        float pad = size * (round ? 0.20f : s.shape == 5 ? 0.13f : 0.14f);
        if (s.grid == 2) {                                                     // big first icon + count badge
            float big = size - 2 * pad;
            RectF dst = new RectF(pad, pad, pad + big, pad + big);
            drawMini(c, p, icons.isEmpty() ? null : icons.get(0), dst, 0, s.shape == 5);
            if (total > 1) {
                float br = size * 0.17f, bx = size - br - size * 0.08f, by = size - br - size * 0.08f;
                p.setColor(lightTile(s) ? 0xFF202124 : 0xFFFFFFFF);
                c.drawCircle(bx, by, br, p);
                p.setColor(lightTile(s) ? 0xFFFFFFFF : 0xFF202124);
                p.setTextSize(br * 1.25f);
                p.setTextAlign(Paint.Align.CENTER);
                p.setFakeBoldText(true);
                String t = total > 99 ? "99+" : String.valueOf(total);
                c.drawText(t, bx, by - (p.descent() + p.ascent()) / 2f, p);
                p.setFakeBoldText(false);
            }
        } else {
            int n = s.grid == 1 ? 3 : 2;
            float gap = size * (n == 3 ? 0.045f : s.shape == 5 ? 0.08f : 0.07f);
            float cell = (size - 2 * pad - (n - 1) * gap) / n;
            for (int i = 0; i < n * n; i++) {
                Bitmap b = i < icons.size() ? icons.get(i) : null;
                if (i >= Math.max(total, icons.size())) break;                 // no empty slots
                float x = pad + (i % n) * (cell + gap), y = pad + (i / n) * (cell + gap);
                drawMini(c, p, b, new RectF(x, y, x + cell, y + cell), i, s.shape == 5);
            }
        }
        return out;
    }

    private static final int[] PLACEHOLDER = {0xFFE05A4F, 0xFF3D8BFD, 0xFF43B06B, 0xFFF2B33D, 0xFF9C5BD6, 0xFF26A69A, 0xFFFF7043, 0xFF5C6BC0, 0xFFEC407A};

    private static void drawMini(Canvas c, Paint p, Bitmap b, RectF dst, int i, boolean roundedClip) {
        if (b != null && !b.isRecycled()) {
            if (roundedClip) {
                int sc = c.save();
                Path clip = new Path();
                float r = dst.width() * ONE_UI_RADIUS;
                clip.addRoundRect(dst, r, r, Path.Direction.CW);
                c.clipPath(clip);
                c.drawBitmap(b, null, dst, p);
                c.restoreToCount(sc);
            } else c.drawBitmap(b, null, dst, p);
        } else {
            p.setColor(PLACEHOLDER[i % PLACEHOLDER.length]);
            float r = dst.width() * 0.26f;
            c.drawRoundRect(dst, r, r, p);
        }
    }

    // ------------------------------------------------------------------ previews for the app's picker

    private static int dp(Context ctx, float v) { return Math.round(v * ctx.getResources().getDisplayMetrics().density); }

    /** Preview drawable for one CHOICE index of one of our keys (null for unknown keys). */
    public static Drawable preview(Context ctx, String key, int i) {
        try {
            Bitmap bmp;
            switch (key) {
                case KEY_SHAPE: bmp = cellPreview(ctx, new Style(i, 0, 0, 0), null); break;
                case KEY_COLOR: bmp = cellPreview(ctx, new Style(0, i, 0, 0), null); break;
                case KEY_OPACITY: bmp = cellPreview(ctx, new Style(0, 9, i, 0), null); break;          // blue tile makes the opacity visible
                case KEY_GRID: bmp = cellPreview(ctx, new Style(0, 0, 0, i), null); break;
                case KEY_LABEL: bmp = cellPreview(ctx, new Style(0, 0, 0, 0), i); break;
                case KEY_VIEW_BG: bmp = viewPreview(ctx, i, 0, 0, false); break;
                case KEY_VIEW_RADIUS: bmp = viewPreview(ctx, 0, i, 0, false); break;
                case KEY_VIEW_COLS: bmp = viewPreview(ctx, 0, 0, i, false); break;
                default: return null;
            }
            return new BitmapDrawable(ctx.getResources(), bmp);
        } catch (Throwable t) { return null; }
    }

    /** Registers picture previews for every CHOICE key above. */
    public static void registerPreviews() {
        String[] keys = {KEY_SHAPE, KEY_COLOR, KEY_OPACITY, KEY_GRID, KEY_LABEL, KEY_VIEW_BG, KEY_VIEW_RADIUS, KEY_VIEW_COLS};
        for (final String k : keys) Previews.register(k, new Previews.Provider() { @Override public Drawable get(Context ctx, int i) { return preview(ctx, k, i); } });
        Previews.register(DrawerKeys.KEY_FOLDER_OPEN, new Previews.Provider() {
            @Override public Drawable get(Context ctx, int i) { return i == 1 ? new BitmapDrawable(ctx.getResources(), oneUiFolderPreview(ctx)) : preview(ctx, KEY_VIEW_BG, 0); }
        }, "How a folder in the app drawer opens");
        Previews.register(DrawerKeys.KEY_SEARCH_POS, new Previews.Provider() {
            @Override public Drawable get(Context ctx, int i) { return new BitmapDrawable(ctx.getResources(), searchPosPreview(ctx, i)); }
        }, "Schematic of the app drawer; while you type, the search bar is at the top like stock");
    }

    /** Drawer schematic for the search-bar position: search pill at the top / at the bottom under the page dots (One UI) / none. */
    private static Bitmap searchPosPreview(Context ctx, int pos) {
        int w = dp(ctx, 84), h = dp(ctx, 120);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF37474F);
        c.drawRoundRect(new RectF(0, 0, w, h), dp(ctx, 12), dp(ctx, 12), p);
        float side = dp(ctx, 8), pillH = dp(ctx, 11), r = pillH / 2f;
        float gridTop = dp(ctx, 12), gridBottom = h - dp(ctx, 20);
        if (pos == 0) {                                                        // stock: pill on top
            p.setColor(0x55FFFFFF);
            c.drawRoundRect(new RectF(side, dp(ctx, 8), w - side, dp(ctx, 8) + pillH), r, r, p);
            gridTop = dp(ctx, 8) + pillH + dp(ctx, 8);
        } else if (pos == 1) {                                                 // One UI: pill at the bottom, dots above it
            float top = h - dp(ctx, 7) - pillH;
            p.setColor(0x55FFFFFF);
            c.drawRoundRect(new RectF(side, top, w - side, top + pillH), r, r, p);
            p.setColor(0xCCFFFFFF);                                            // the ⋮ inside the pill
            float mx = w - side - dp(ctx, 7), my = top + pillH / 2f, d = dp(ctx, 1.1f);
            for (int k = -1; k <= 1; k++) c.drawCircle(mx, my + k * dp(ctx, 2.6f), d, p);
            p.setColor(0x99FFFFFF);
            c.drawRect(side + dp(ctx, 6), my - dp(ctx, 1), side + dp(ctx, 22), my + dp(ctx, 1), p);   // "Search" hint
            gridBottom = top - dp(ctx, 14);
        }
        float dotsY = pos == 1 ? h - dp(ctx, 7) - pillH - dp(ctx, 7) : h - dp(ctx, 9);
        p.setColor(0x80FFFFFF);
        float dot = dp(ctx, 2.2f), gap = dp(ctx, 3);
        float total = 3 * dot * 2 + 2 * gap + dp(ctx, 4);
        float x = (w - total) / 2f;
        for (int k = 0; k < 3; k++) {
            if (k == 1) { p.setColor(0xFFFFFFFF); c.drawRoundRect(new RectF(x, dotsY - dot, x + dot * 2 + dp(ctx, 4), dotsY + dot), dot, dot, p); x += dot * 2 + dp(ctx, 4) + gap; p.setColor(0x80FFFFFF); }
            else { c.drawCircle(x + dot, dotsY, dot, p); x += dot * 2 + gap; }
        }
        int cols = 4, rows = Math.max(3, Math.min(6, Math.round((gridBottom - gridTop) / dp(ctx, 15))));
        float cell = (w - 2 * side) / cols, icon = Math.min(cell * 0.62f, dp(ctx, 11));
        float rowH = (gridBottom - gridTop) / rows;
        for (int i = 0; i < cols * rows; i++) {
            float cx = side + (i % cols) * cell + cell / 2f, cy = gridTop + (i / cols) * rowH + rowH / 2f;
            p.setColor(PLACEHOLDER[i % PLACEHOLDER.length]);
            c.drawRoundRect(new RectF(cx - icon / 2f, cy - icon / 2f, cx + icon / 2f, cy + icon / 2f), icon * 0.3f, icon * 0.3f, p);
        }
        return out;
    }

    /** A drawer cell on a neutral wallpaper-ish background: the folder tile (4 placeholder apps, 6 in total) and optionally the label. */
    private static Bitmap cellPreview(Context ctx, Style s, Integer label) {
        int w = dp(ctx, 96), h = dp(ctx, 96);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF37474F);                                                // slate "wallpaper"
        c.drawRoundRect(new RectF(0, 0, w, h), dp(ctx, 12), dp(ctx, 12), p);
        int size = dp(ctx, label == null ? 60 : 52);
        List<Bitmap> none = new ArrayList<>();
        Bitmap tile = folderIcon(none, 6, size, s);
        float top = label == null ? (h - size) / 2f : dp(ctx, 10);
        c.drawBitmap(tile, (w - size) / 2f, top, p);
        if (label != null && label != 3) {
            p.setColor(label == 2 ? 0xFF000000 : 0xFFFFFFFF);
            p.setTextSize(dp(ctx, 11));
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("Folder", w / 2f, top + size + dp(ctx, 15), p);
        }
        return out;
    }

    /** The opened folder card: title line + grid of placeholder apps. */
    private static Bitmap viewPreview(Context ctx, int bg, int radius, int colsChoice, boolean noNames) {
        int w = dp(ctx, 120), h = dp(ctx, 96);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF37474F);
        c.drawRoundRect(new RectF(0, 0, w, h), dp(ctx, 12), dp(ctx, 12), p);
        RectF card = new RectF(dp(ctx, 10), dp(ctx, 8), w - dp(ctx, 10), h - dp(ctx, 8));
        float r = Math.min(dp(ctx, viewRadiusDp(radius)) * 0.5f, card.height() / 2f);
        p.setColor(viewCardColor(bg));
        c.drawRoundRect(card, r, r, p);
        p.setColor(viewTextColor(bg));
        p.setTextSize(dp(ctx, 9));
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("Folder", card.centerX(), card.top + dp(ctx, 14), p);
        int cols = viewColumns(colsChoice);
        float pad = dp(ctx, 10), gap = dp(ctx, 4);
        float cell = (card.width() - 2 * pad - (cols - 1) * gap) / cols;
        float icon = Math.min(cell, dp(ctx, 18));
        float y0 = card.top + dp(ctx, 22);
        for (int i = 0; i < cols * 2; i++) {
            float x = card.left + pad + (i % cols) * (cell + gap) + (cell - icon) / 2f;
            float y = y0 + (i / cols) * (icon + (noNames ? gap : dp(ctx, 9)));
            if (y + icon > card.bottom - dp(ctx, 4)) break;
            p.setColor(PLACEHOLDER[i % PLACEHOLDER.length]);
            c.drawRoundRect(new RectF(x, y, x + icon, y + icon), icon * 0.26f, icon * 0.26f, p);
            if (!noNames) { p.setColor(viewTextColor(bg) & 0x99FFFFFF); c.drawRect(x + icon * 0.15f, y + icon + dp(ctx, 3), x + icon * 0.85f, y + icon + dp(ctx, 5), p); }
        }
        return out;
    }

    /** One UI folder screen: dimmed/blurred backdrop, big title, + and colour dot, rounded panel with a 4-column grid. */
    private static Bitmap oneUiFolderPreview(Context ctx) {
        int w = dp(ctx, 84), h = dp(ctx, 120);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, w, h, 0xFF1B2B33, 0xFF0B0F12, android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, w, h), dp(ctx, 12), dp(ctx, 12), p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(dp(ctx, 13));
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(false);
        c.drawText("Folder", w / 2f, dp(ctx, 26), p);
        RectF panel = new RectF(dp(ctx, 6), dp(ctx, 46), w - dp(ctx, 6), h - dp(ctx, 10));
        float bx = panel.right - dp(ctx, 6), by = panel.top - dp(ctx, 7);
        p.setColor(0xFF22313A); p.setStyle(Paint.Style.FILL); c.drawCircle(bx, by, dp(ctx, 3), p);
        p.setColor(0xFF8899A6); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(ctx, 0.8f)); c.drawCircle(bx, by, dp(ctx, 3), p);
        p.setColor(0xFFFFFFFF); p.setStrokeWidth(dp(ctx, 1.1f));
        float px = bx - dp(ctx, 10);
        c.drawLine(px - dp(ctx, 3), by, px + dp(ctx, 3), by, p); c.drawLine(px, by - dp(ctx, 3), px, by + dp(ctx, 3), p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xD9232527);
        c.drawRoundRect(panel, dp(ctx, 9), dp(ctx, 9), p);
        int cols = 4;
        float pad = dp(ctx, 5), cell = (panel.width() - 2 * pad) / cols, icon = Math.min(cell * 0.66f, dp(ctx, 11));
        for (int i = 0; i < 13; i++) {
            float cx = panel.left + pad + (i % cols) * cell + cell / 2f, cy = panel.top + dp(ctx, 11) + (i / cols) * dp(ctx, 17);
            p.setColor(PLACEHOLDER[i % PLACEHOLDER.length]);
            c.drawRoundRect(new RectF(cx - icon / 2f, cy - icon / 2f, cx + icon / 2f, cy + icon / 2f), icon * 0.3f, icon * 0.3f, p);
            p.setColor(0x88FFFFFF);
            c.drawRect(cx - icon * 0.4f, cy + icon / 2f + dp(ctx, 2), cx + icon * 0.4f, cy + icon / 2f + dp(ctx, 3.2f), p);
        }
        return out;
    }
}
