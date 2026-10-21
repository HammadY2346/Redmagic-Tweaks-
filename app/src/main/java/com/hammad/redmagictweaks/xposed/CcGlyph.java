package com.hammad.redmagictweaks.xposed;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Small line / fill glyphs for the One UI 8 Control Center layout (CcOneUiHooks) and its picker preview. Drawn on a
 * 24 x 24 unit grid centred in the bounds, so they need no resources inside System UI. Original shapes, One UI-like
 * weight (2 unit strokes, rounded caps).
 */
public final class CcGlyph extends Drawable {
    public static final int MOON = 0, SPEAKER = 1, VIBRATE = 2, MUTE = 3, NOTE = 4, DEVICES = 5, CAST = 6, MODES = 7, SHARE = 8, POWER = 9, OUTPUT = 10;

    private final int type;
    private int color;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CcGlyph(int type, int color) { this.type = type; this.color = color; }

    public void setColor(int c) { if (color != c) { color = c; invalidateSelf(); } }
    public int type() { return type; }
    public int getColor() { return color; }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float size = Math.min(b.width(), b.height());
        int save = canvas.save();
        try {
            canvas.translate(b.exactCenterX() - size / 2f, b.exactCenterY() - size / 2f);
            canvas.scale(size / 24f, size / 24f);
            paint(canvas, type, color, paint);
        } finally { canvas.restoreToCount(save); }
    }

    /** Draws glyph {@code type} on a 24 x 24 unit canvas. */
    public static void paint(Canvas c, int type, int color, Paint p) {
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (type) {
            case MOON: {
                Path outer = new Path(); outer.addCircle(12f, 12.5f, 8f, Path.Direction.CW);
                Path cut = new Path(); cut.addCircle(16.5f, 8f, 7f, Path.Direction.CW);
                outer.op(cut, Path.Op.DIFFERENCE);
                fill(p); c.drawPath(outer, p);
                break;
            }
            case SPEAKER: case MUTE: case VIBRATE: {
                if (type == VIBRATE) {
                    stroke(p, 2f);
                    c.drawRoundRect(new RectF(8f, 4f, 16f, 20f), 2.2f, 2.2f, p);
                    Path l = new Path(); l.moveTo(4.5f, 8.5f); l.lineTo(3f, 10.25f); l.lineTo(4.5f, 12f); l.lineTo(3f, 13.75f); l.lineTo(4.5f, 15.5f);
                    Path r = new Path(); r.moveTo(19.5f, 8.5f); r.lineTo(21f, 10.25f); r.lineTo(19.5f, 12f); r.lineTo(21f, 13.75f); r.lineTo(19.5f, 15.5f);
                    stroke(p, 1.6f); c.drawPath(l, p); c.drawPath(r, p);
                    break;
                }
                Path body = new Path();
                body.moveTo(3.5f, 9.5f); body.lineTo(7f, 9.5f); body.lineTo(11.5f, 5.5f); body.lineTo(11.5f, 18.5f); body.lineTo(7f, 14.5f); body.lineTo(3.5f, 14.5f); body.close();
                fill(p); c.drawPath(body, p);
                stroke(p, 2f);
                if (type == SPEAKER) {
                    c.drawArc(new RectF(10f, 8.5f, 17f, 15.5f), -55f, 110f, false, p);
                    c.drawArc(new RectF(10f, 5f, 21f, 19f), -55f, 110f, false, p);
                } else {
                    c.drawLine(15f, 9.5f, 20f, 14.5f, p);
                    c.drawLine(20f, 9.5f, 15f, 14.5f, p);
                }
                break;
            }
            case NOTE: {
                fill(p);
                c.drawOval(new RectF(4.5f, 15f, 10.5f, 20f), p);
                c.drawOval(new RectF(13.5f, 13f, 19.5f, 18f), p);
                stroke(p, 2f);
                c.drawLine(9.6f, 17.2f, 9.6f, 6.2f, p);
                c.drawLine(18.6f, 15.2f, 18.6f, 4.2f, p);
                fill(p);
                Path beam = new Path(); beam.moveTo(8.6f, 5.4f); beam.lineTo(19.6f, 3f); beam.lineTo(19.6f, 6.4f); beam.lineTo(8.6f, 8.8f); beam.close();
                c.drawPath(beam, p);
                break;
            }
            case DEVICES: {
                stroke(p, 2f);
                c.drawRoundRect(new RectF(2.5f, 5f, 16.5f, 15.5f), 2f, 2f, p);
                c.drawLine(6f, 19f, 13f, 19f, p);
                c.drawRoundRect(new RectF(16f, 9f, 21.5f, 19.5f), 1.6f, 1.6f, p);
                break;
            }
            case CAST: {
                stroke(p, 2f);
                Path frame = new Path(); frame.moveTo(3f, 9f); frame.lineTo(3f, 6.5f); frame.quadTo(3f, 4.5f, 5f, 4.5f); frame.lineTo(19f, 4.5f); frame.quadTo(21f, 4.5f, 21f, 6.5f);
                frame.lineTo(21f, 17.5f); frame.quadTo(21f, 19.5f, 19f, 19.5f); frame.lineTo(14.5f, 19.5f);
                c.drawPath(frame, p);
                c.drawArc(new RectF(-1.5f, 10.5f, 10.5f, 22.5f), -90f, 90f, false, p);
                c.drawArc(new RectF(0.5f, 14f, 7f, 20.5f), -90f, 90f, false, p);
                fill(p); c.drawCircle(3.2f, 19.3f, 1.3f, p);
                break;
            }
            case MODES: {
                stroke(p, 2f);
                c.drawCircle(12f, 12f, 8.5f, p);
                fill(p);
                c.drawArc(new RectF(5.5f, 5.5f, 18.5f, 18.5f), -90f, 180f, true, p);
                break;
            }
            case SHARE: {
                fill(p);
                c.drawCircle(17.5f, 5.5f, 2.8f, p);
                c.drawCircle(6.5f, 12f, 2.8f, p);
                c.drawCircle(17.5f, 18.5f, 2.8f, p);
                stroke(p, 1.8f);
                c.drawLine(8.8f, 10.6f, 15.2f, 6.9f, p);
                c.drawLine(8.8f, 13.4f, 15.2f, 17.1f, p);
                break;
            }
            case POWER: {
                stroke(p, 2f);
                c.drawArc(new RectF(4.5f, 5f, 19.5f, 20f), -55f, 290f, false, p);
                c.drawLine(12f, 3.5f, 12f, 11.5f, p);
                break;
            }
            case OUTPUT: {
                stroke(p, 2f);
                c.drawRoundRect(new RectF(3f, 4f, 13f, 20f), 2f, 2f, p);
                c.drawCircle(8f, 14.5f, 2.3f, p);
                fill(p);
                c.drawCircle(8f, 7.8f, 1.1f, p);
                stroke(p, 2f);
                c.drawArc(new RectF(12f, 8f, 18f, 16f), -60f, 120f, false, p);
                c.drawArc(new RectF(12f, 5f, 22f, 19f), -60f, 120f, false, p);
                break;
            }
            default: break;
        }
    }

    private static void fill(Paint p) { p.setStyle(Paint.Style.FILL); }
    private static void stroke(Paint p, float w) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return -1; }
    @Override public int getIntrinsicHeight() { return -1; }
}
