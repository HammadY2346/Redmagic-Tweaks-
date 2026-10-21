package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Fingerprint artwork shared by the app (previews) and the hooked processes (xposed/FpArt): the code-drawn
 * idle glyphs ported from nubia-ly's FingerprintGlyphDrawable, a wrapper that scales any icon picture, and
 * the RisingOS animation atlases (8 columns of 256 px tiles, {@code rising_anim_atlas_<id>}). No Xposed
 * types are referenced here so the class loads in the plain app process.
 */
public final class FpArtwork {
    private FpArtwork() {}

    public static final int TILE = 256, COLUMNS = 8;

    /** Number of tiles in {@code rising_anim_atlas_<id>} for nubia-ly animation ids 1..40 (index = id - 1). */
    public static final int[] ATLAS_FRAMES = {
        1, 33, 17, 26, 52, 60, 50, 44, 19, 20, 18, 20, 44, 29, 29, 41, 40, 29, 14, 25,
        25, 22, 40, 46, 60, 45, 29, 26, 38, 38, 46, 46, 38, 16, 46, 34, 59, 30, 29, 30,
    };

    public static int atlasFrames(int id) { return id >= 1 && id <= ATLAS_FRAMES.length ? ATLAS_FRAMES[id - 1] : 0; }

    /** Source rectangle of tile {@code frame} inside an atlas. */
    public static Rect tile(int frame) {
        int c = frame % COLUMNS, r = frame / COLUMNS;
        return new Rect(c * TILE, r * TILE, (c + 1) * TILE, (r + 1) * TILE);
    }

    /** Decode {@code rising_anim_atlas_<id>} from {@code res} unscaled; null when missing or malformed. */
    public static Bitmap decodeAtlas(Resources res, String pkg, int id) {
        if (res == null) return null;
        try {
            int rid = res.getIdentifier("rising_anim_atlas_" + id, "drawable", pkg);
            if (rid == 0) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeResource(res, rid, o);
            int count = atlasFrames(id);
            if (b == null || count <= 0 || b.getWidth() != COLUMNS * TILE || b.getHeight() < ((count + COLUMNS - 1) / COLUMNS) * TILE) {
                if (b != null) b.recycle();
                return null;
            }
            return b;
        } catch (Throwable t) { return null; }
    }

    /** One tile of a shared atlas as a drawable (nothing is copied). */
    public static final class AtlasFrame extends Drawable {
        private final Bitmap atlas; private final Rect src;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        public AtlasFrame(Bitmap atlas, int frame) { this.atlas = atlas; this.src = tile(frame); }
        @Override public void draw(Canvas c) { if (atlas != null && !atlas.isRecycled()) c.drawBitmap(atlas, src, getBounds(), paint); }
        @Override public void setAlpha(int a) { paint.setAlpha(a); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return TILE; }
        @Override public int getIntrinsicHeight() { return TILE; }
    }

    private static int cachedId; private static Bitmap cachedAtlas;

    /** The app's own copy of an atlas, one entry cached (the picker asks for the same atlas repeatedly). */
    public static synchronized Bitmap atlas(Context ctx, int id) {
        if (cachedAtlas != null && cachedId == id && !cachedAtlas.isRecycled()) return cachedAtlas;
        Bitmap b = decodeAtlas(ctx.getResources(), ctx.getPackageName(), id);
        if (b != null) { cachedAtlas = b; cachedId = id; }
        return b;
    }

    /** All tiles of an atlas as drawables, for Previews.registerFrames. */
    public static Drawable[] atlasDrawables(Context ctx, int id) {
        Bitmap b = atlas(ctx, id);
        if (b == null) return null;
        int n = atlasFrames(id);
        Drawable[] out = new Drawable[n];
        for (int i = 0; i < n; i++) out[i] = new AtlasFrame(b, i);
        return out;
    }

    /** Solid round colour swatch for colour pickers. */
    public static final class Dot extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        public Dot(int argb) { paint.setColor(argb); ring.setColor(0x33000000); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(2); }
        @Override public void draw(Canvas c) {
            Rect b = getBounds(); float r = Math.min(b.width(), b.height()) * 0.3f;
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, paint);
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, ring);
        }
        @Override public void setAlpha(int a) { paint.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Idle fingerprint icon: a code-drawn nubia-ly glyph (styles 0..4: rings, scan frame, hexagon, diamond, dot),
     * an artwork drawable (RisingOS icons) or a bitmap (icon shipped with a frame set), scaled by {@code percent}
     * around the centre of its bounds. Style 0..4 use {@code color}; pictures keep their own colours.
     */
    public static final class Glyph extends Drawable {
        private final int style, percent;
        private final Bitmap bitmap;
        private final Drawable artwork;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        public Glyph(int style, int color, Drawable artwork, Bitmap bitmap, int percent) {
            this.style = style; this.artwork = artwork; this.bitmap = bitmap; this.percent = percent <= 0 ? 100 : percent;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            int save = canvas.save();
            try {
                if (percent != 100) canvas.scale(percent / 100f, percent / 100f, cx, cy);
                float size = Math.min(b.width(), b.height());
                if (artwork != null) {
                    float l = cx - size / 2f, t = cy - size / 2f;
                    artwork.setBounds(Math.round(l), Math.round(t), Math.round(l + size), Math.round(t + size));
                    artwork.draw(canvas);
                    return;
                }
                if (bitmap != null && !bitmap.isRecycled()) {
                    float l = cx - size / 2f, t = cy - size / 2f;
                    canvas.drawBitmap(bitmap, null, new RectF(l, t, l + size, t + size), paint);
                    return;
                }
                float r = size * 0.34f;
                paint.setStrokeWidth(Math.max(1, size * 0.025f));
                switch (style) {
                    case 0:
                        for (int i = 0; i < 3; i++) canvas.drawCircle(cx, cy, r * (1 - i * 0.28f), paint);
                        break;
                    case 1: {
                        float d = r * 0.45f;
                        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) {
                            canvas.drawLine(cx + x * r, cy + y * r, cx + x * (r - d), cy + y * r, paint);
                            canvas.drawLine(cx + x * r, cy + y * r, cx + x * r, cy + y * (r - d), paint);
                        }
                        canvas.drawCircle(cx, cy, r * 0.4f, paint);
                        break;
                    }
                    case 2: case 3: {
                        Path path = new Path();
                        int sides = style == 2 ? 6 : 4;
                        for (int i = 0; i < sides; i++) {
                            double a = -Math.PI / 2 + 2 * Math.PI * i / sides;
                            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
                            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                        }
                        path.close();
                        canvas.drawPath(path, paint);
                        canvas.drawCircle(cx, cy, r * 0.34f, paint);
                        break;
                    }
                    default: {
                        Paint.Style old = paint.getStyle();
                        paint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(cx, cy, r * 0.42f, paint);
                        paint.setStyle(old);
                        canvas.drawCircle(cx, cy, r, paint);
                        break;
                    }
                }
            } finally {
                canvas.restoreToCount(save);
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); if (artwork != null) artwork.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); if (artwork != null) artwork.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
