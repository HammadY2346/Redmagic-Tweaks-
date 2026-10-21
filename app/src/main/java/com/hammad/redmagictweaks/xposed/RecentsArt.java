package com.hammad.redmagictweaks.xposed;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Recent apps styles for the stock launcher (com.zte.mifavor.launcher, Launcher3 quickstep): the option keys, what
 * "Auto" means for each style, the card transform of the moving styles (shared by the hooks and the previews, so the
 * picture in the app moves the cards exactly like the phone) and the preview painter.
 *
 * Card transform: a card {@code d} pages from the middle of the screen (negative = left, older apps) gets a scale, an
 * extra horizontal shift, a Y rotation and a Z order. Carousel and cover flow keep the same gap between all cards
 * (neighbours shrink and move in by exactly what they lost), so no card ever lies over another one and touches always
 * reach the card you see. Stack (iOS style) lays every card over the one on its left; the launcher picks the card to
 * swipe away in that same right-to-left order, so it is also the card on top.
 */
public final class RecentsArt {
    private RecentsArt() {}

    public static final String KEY_STYLE = "recents_style";
    public static final String KEY_HEADER = "recents_header";
    public static final String KEY_CLEAR = "recents_clear_all";
    public static final String KEY_CORNERS = "recents_corners";
    public static final String KEY_SIZE = "recents_card_size";

    public static final int STOCK = 0, ONE_UI = 1, CAROUSEL = 2, STACK = 3, COVERFLOW = 4, GRID = 5, LIST = 6;
    public static final String[] STYLES = {"Stock (launcher setting)", "One UI 8", "Carousel", "Stack (iOS style)", "Cover flow (3D)", "Grid", "List"};

    public static final int H_AUTO = 0, H_STOCK = 1, H_ICON = 2, H_ICON_NAME = 3;
    public static final String[] HEADERS = {"Auto (follows the style)", "Stock: icon, name and buttons", "Icon in the middle (One UI)", "Icon and name in the middle"};

    public static final int C_AUTO = 0, C_STOCK = 1, C_PILL = 2, C_HIDDEN = 3;
    public static final String[] CLEARS = {"Auto (follows the style)", "Stock round button", "\"Close all\" pill (One UI)", "Hidden"};

    public static final String[] CORNERS = {"Auto (follows the style)", "Stock (16 dp)", "Square · 4 dp", "Small · 12 dp", "Medium · 20 dp", "One UI · 26 dp", "Large · 34 dp"};
    private static final float[] CORNER_DP = {0, 0, 4, 12, 20, 26, 34};
    public static final float STOCK_CORNER_DP = 16f;

    public static final String[] SIZES = {"Auto (follows the style)", "Stock", "92 %", "86 %", "80 %"};
    private static final float[] SIZE_F = {1, 1, 0.92f, 0.86f, 0.80f};

    /** Everything one recents screen needs, with "Auto" already resolved for the style. */
    public static final class Look {
        public int style, header, clear;
        /** 0 = stock corners. */
        public float cornerDp;
        /** 1 = stock card size. */
        public float size;
        public boolean any() { return style != STOCK || header != H_STOCK || clear != C_STOCK || cornerDp > 0 || size != 1f; }
        public boolean moves() { return style == CAROUSEL || style == STACK || style == COVERFLOW; }
    }

    public static Look resolve(int style, int header, int clear, int corners, int size) {
        Look l = new Look();
        l.style = style >= 0 && style < STYLES.length ? style : STOCK;
        boolean oneUi = l.style == ONE_UI;
        l.header = header > 0 && header < HEADERS.length ? header : oneUi ? H_ICON : H_STOCK;
        l.clear = clear > 0 && clear < CLEARS.length ? clear : oneUi ? C_PILL : C_STOCK;
        if (corners > 0 && corners < CORNER_DP.length) l.cornerDp = CORNER_DP[corners];
        else l.cornerDp = oneUi ? 26f : l.style == CAROUSEL || l.style == COVERFLOW ? 24f : l.style == STACK ? 22f : 0f;
        if (size > 0 && size < SIZE_F.length) l.size = SIZE_F[size];
        else l.size = oneUi ? 0.9f : l.style == COVERFLOW ? 0.86f : l.style == CAROUSEL || l.style == STACK ? 0.92f : 1f;
        return l;
    }

    /** The launcher's own setting (1 list, 2 grid) the style needs, or 0 to leave it alone. */
    public static int launcherSetting(int style) {
        switch (style) {
            case GRID: return 2;
            case STOCK: return 0;
            default: return 1;
        }
    }

    // ============================================================================================ card transform

    /**
     * @param d    position of the card's centre in pages from the middle of the screen, negative = left
     * @param w    card width (px)
     * @param step distance between two card centres in the stock layout (px)
     * @param out  {scale, extra x shift (px), rotation Y (degrees), z}
     */
    public static void transform(int style, float d, float w, float step, float[] out) {
        out[0] = 1f; out[1] = 0f; out[2] = 0f; out[3] = 0f;
        float a = Math.abs(d), sign = Math.signum(d);
        switch (style) {
            case CAROUSEL: {
                // scale 1 in the middle, 0.86 one page away, 0.72 from two pages on; the gap stays the stock gap
                out[0] = 1f - 0.14f * Math.min(a, 2f);
                float lost = a <= 2f ? 0.07f * a * a : 0.28f + 0.28f * (a - 2f);   // integral of (1 - scale)
                out[1] = -sign * w * lost;
                out[3] = -a;
                break;
            }
            case COVERFLOW: {
                // turned up to 42 degrees towards the middle, a little smaller, pulled in so the gap stays the same
                float rot = 42f * Math.min(a, 1f);
                out[0] = 1f - 0.12f * Math.min(a, 1f);
                out[2] = -sign * rot;
                out[1] = -sign * w * lostCover(a);
                out[3] = -a;
                break;
            }
            case STACK: {
                if (d < 0) {
                    // older cards slide under the next one: 42 % of a page apart, 6 % smaller per page, at most 3 deep
                    float k = Math.min(a, 3.2f);
                    float x = -k * step * 0.42f;
                    out[1] = x - d * step;
                    out[0] = 1f - 0.06f * k;
                }
                out[3] = d;     // every card above the one on its left
                break;
            }
            default: break;
        }
    }

    /** Integral over 0..a of (1 - visible width) for the cover flow card (scale x cos(rotation)). */
    private static float lostCover(float a) {
        int n = 16;
        float upTo = Math.min(a, 1f), sum = 0f, h = upTo / n;
        for (int i = 0; i < n; i++) {
            float x = (i + 0.5f) * h;
            sum += (1f - widthCover(x)) * h;
        }
        if (a > 1f) sum += (1f - widthCover(1f)) * (a - 1f);
        return sum;
    }

    private static float widthCover(float x) {
        float t = Math.min(x, 1f);
        return (1f - 0.12f * t) * (float) Math.cos(Math.toRadians(42f * t));
    }

    // ============================================================================================ preview

    /** Phone size the preview is drawn in (dp), the NX789J screen. */
    public static final float SW = 412f, SH = 912f;
    /** Stock card: 70 % of the screen (overview_max_scale), centred between the 48 dp header and the button row. */
    private static final float CARD_W = 288f, CARD_H = 638f, CARD_CY = 422f, HEADER = 48f, BUTTON_CY = 819f;

    private static final int[][] APPS = {     // accent, background of the app screen
        {0xFF3B82F6, 0xFFF4F7FB}, {0xFF34A853, 0xFFFFFFFF}, {0xFFEF4444, 0xFF181818}, {0xFFF59E0B, 0xFFFFFBF2}, {0xFF8B5CF6, 0xFF121018}};
    private static final String[] NAMES = {"Messages", "Chrome", "YouTube", "Gallery", "Settings"};

    /**
     * The whole recent apps screen (412 x 912 dp units on {@code c}; scale the canvas for other sizes), without the
     * status bar. {@code font(weight)} may return null.
     */
    public static void paintScreen(Canvas c, Look l, FontSource fonts, boolean background) {
        if (background) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, 0, 0, SH, 0xFF2B3550, 0xFF10131B, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, SW, SH, p);
            p.setShader(null);
            p.setColor(0x59000000);              // the dim over the wallpaper
            c.drawRect(0, 0, SW, SH, p);
        }
        if (l.style == GRID) paintGrid(c, l, fonts);
        else paintList(c, l, fonts);
        paintClear(c, l, fonts);
    }

    public interface FontSource { Typeface font(int weight); }

    private static void paintList(Canvas c, Look l, FontSource fonts) {
        float cw = CARD_W * l.size, ch = CARD_H * l.size, gap = 16f;
        float step = cw + gap;
        // scrolled one app back, so there are cards on both sides: d = +1 newest ... -3
        float[] ds = {1f, 0f, -1f, -2f, -3f};
        final float[][] tr = new float[ds.length][4];
        Integer[] order = new Integer[ds.length];
        for (int i = 0; i < ds.length; i++) { transform(l.moves() ? l.style : STOCK, ds[i], cw, step, tr[i]); order[i] = i; }
        Arrays.sort(order, Comparator.comparingDouble(i -> tr[i][3]));
        float corner = l.cornerDp > 0 ? l.cornerDp : STOCK_CORNER_DP;
        for (int idx : order) {
            float cx = SW / 2f + ds[idx] * step + tr[idx][1];
            if (cx + cw < -40 || cx - cw > SW + 40) continue;
            int save = c.save();
            // the launcher scales and turns a card around the middle of its screenshot
            c.translate(cx, CARD_CY);
            if (tr[idx][2] != 0f) c.concat(turn(tr[idx][2], cw / 2f, ch / 2f + HEADER));
            c.scale(tr[idx][0], tr[idx][0]);
            paintCard(c, l, fonts, idx == 0 ? 1 : idx == 1 ? 0 : idx, cw, ch, corner);
            c.restoreToCount(save);
        }
    }

    /** Camera distance of the turned cards, dp (the hook gives the card view the same). */
    public static final float CAMERA_DP = 1008f;

    /**
     * Perspective of a card turned {@code deg} around its vertical centre line, as View.setRotationY draws it: a
     * positive angle moves the right edge away. Built from the four corners, so it doesn't depend on Camera.
     */
    private static Matrix turn(float deg, float hw, float hh) {
        double th = Math.toRadians(deg);
        float cos = (float) Math.cos(th), sin = (float) Math.sin(th);
        float[] src = {-hw, -hh, hw, -hh, hw, hh, -hw, hh}, dst = new float[8];
        for (int k = 0; k < 4; k++) {
            float x = src[2 * k], y = src[2 * k + 1], f = CAMERA_DP / (CAMERA_DP + x * sin);
            dst[2 * k] = x * cos * f;
            dst[2 * k + 1] = y * f;
        }
        Matrix m = new Matrix();
        m.setPolyToPoly(src, 0, dst, 0, 4);
        return m;
    }

    /** One task view centred on (0, 0): header strip above the screenshot. */
    private static void paintCard(Canvas c, Look l, FontSource fonts, int app, float cw, float ch, float corner) {
        int[] col = APPS[app % APPS.length];
        RectF r = new RectF(-cw / 2f, -ch / 2f, cw / 2f, ch / 2f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // screenshot
        Path clip = new Path();
        clip.addRoundRect(r, corner, corner, Path.Direction.CW);
        int save = c.save();
        c.clipPath(clip);
        p.setColor(col[1]);
        c.drawRect(r, p);
        boolean dark = (col[1] & 0xFF) < 0x80;
        p.setColor(col[0]);
        c.drawRect(r.left, r.top, r.right, r.top + ch * 0.13f, p);
        p.setColor(dark ? 0x26FFFFFF : 0x14000000);
        float y = r.top + ch * 0.19f;
        for (int i = 0; i < 6; i++) {
            float x0 = r.left + cw * 0.08f;
            c.drawRoundRect(new RectF(x0, y, x0 + cw * 0.16f, y + cw * 0.16f), cw * 0.08f, cw * 0.08f, p);
            c.drawRoundRect(new RectF(x0 + cw * 0.22f, y + cw * 0.02f, r.right - cw * (0.12f + 0.07f * (i % 3)), y + cw * 0.06f), 6, 6, p);
            c.drawRoundRect(new RectF(x0 + cw * 0.22f, y + cw * 0.09f, r.right - cw * 0.3f, y + cw * 0.12f), 6, 6, p);
            y += cw * 0.25f;
        }
        c.restoreToCount(save);
        // header
        float iconSz = 32f, hy = r.top - HEADER / 2f;
        Typeface medium = fonts == null ? null : fonts.font(500);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFFFFFFFF);
        tp.setTextSize(15f);
        if (medium != null) tp.setTypeface(medium);
        String name = NAMES[app % NAMES.length];
        switch (l.header) {
            case H_ICON:
                appIcon(c, 0f, hy, iconSz, col[0], name);
                break;
            case H_ICON_NAME: {
                float tw = tp.measureText(name), total = iconSz + 8f + tw, x0 = -total / 2f;
                appIcon(c, x0 + iconSz / 2f, hy, iconSz, col[0], name);
                c.drawText(name, x0 + iconSz + 8f, hy + 5.5f, tp);
                break;
            }
            default: {
                float ix = r.left + 12f + iconSz / 2f;
                appIcon(c, ix, hy, iconSz, col[0], name);
                c.drawText(name, ix + iconSz / 2f + 8f, hy + 5.5f, tp);
                if (cw < 220f) break;     // grid cards: no room for the buttons in the picture
                // mini window and menu buttons at the right end
                Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
                bp.setColor(0xE6FFFFFF);
                float mx = r.right - 18f;
                for (int i = -1; i <= 1; i++) c.drawCircle(mx, hy + i * 6f, 2.2f, bp);
                bp.setStyle(Paint.Style.STROKE);
                bp.setStrokeWidth(2f);
                c.drawRoundRect(new RectF(mx - 46f, hy - 8f, mx - 30f, hy + 8f), 3f, 3f, bp);
                break;
            }
        }
    }

    private static void appIcon(Canvas c, float cx, float cy, float sz, int color, String name) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawCircle(cx, cy, sz / 2f, p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(sz * 0.5f);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(name.substring(0, 1), cx, cy + sz * 0.18f, p);
    }

    private static void paintGrid(Canvas c, Look l, FontSource fonts) {
        float ch = 290f * l.size, cw = ch * SW / SH * 1.05f, gapX = 22f, gapY = 56f;
        float top = 96f + 30f;
        float corner = l.cornerDp > 0 ? l.cornerDp * 0.7f : STOCK_CORNER_DP * 0.7f;
        Look small = new Look();
        small.header = l.header; small.style = GRID; small.size = l.size;
        int app = 0;
        for (int row = 0; row < 2; row++) {
            for (int col = -1; col <= 1; col++) {
                float cx = SW / 2f + col * (cw + gapX);
                float cy = top + row * (ch + gapY) + ch / 2f;
                int save = c.save();
                c.translate(cx, cy);
                c.scale(0.8f, 0.8f);
                paintCard(c, small, fonts, app++, cw / 0.8f, ch / 0.8f, corner / 0.8f);
                c.restoreToCount(save);
            }
        }
    }

    private static void paintClear(Canvas c, Look l, FontSource fonts) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = SW / 2f, cy = BUTTON_CY;
        if (l.clear == C_HIDDEN) return;
        if (l.clear == C_PILL) {
            Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(0xFFFFFFFF);
            tp.setTextSize(15f);
            Typeface t = fonts == null ? null : fonts.font(600);
            if (t != null) tp.setTypeface(t);
            tp.setTextAlign(Paint.Align.CENTER);
            float tw = tp.measureText("Close all");
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(new RectF(cx - tw / 2f - 22f, cy - 20f, cx + tw / 2f + 22f, cy + 20f), 20f, 20f, p);
            c.drawText("Close all", cx, cy + 5.5f, tp);
            return;
        }
        // stock: a round button with a cross
        p.setColor(0x33FFFFFF);
        c.drawCircle(cx, cy, 20f, p);
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.4f);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(cx - 7f, cy - 7f, cx + 7f, cy + 7f, p);
        c.drawLine(cx + 7f, cy - 7f, cx - 7f, cy + 7f, p);
    }

    /** Part of the screen a picker card shows for an option key (x, y, w, h in the 412 x 912 screen). */
    public static RectF cropFor(String key) {
        if (KEY_CLEAR.equals(key)) return new RectF(0, 620, SW, 620 + SW / 1.25f);
        if (KEY_HEADER.equals(key)) return new RectF(50, 40, 362, 40 + 312 / 1.25f);
        if (KEY_CORNERS.equals(key)) return new RectF(40, 60, 290, 60 + 250 / 1.25f);
        return new RectF(0, 40, SW, 40 + SW / 1.25f);
    }
}
