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
 * Line glyphs for the One UI lock screen (OneUiLockHooks): phone, camera, padlock, alarm clock, calendar. Drawn on a
 * 24 x 24 unit grid centred in the bounds (no resources needed inside System UI); original shapes in One UI's weight.
 */
public final class LockGlyph extends Drawable {
    public static final int PHONE = 0, CAMERA = 1, LOCK = 2, ALARM = 3, CALENDAR = 4;

    private final int type;
    private int color;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LockGlyph(int type, int color) { this.type = type; this.color = color; }

    public void setColor(int c) { if (color != c) { color = c; invalidateSelf(); } }

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

    static void paint(Canvas c, int type, int color, Paint p) {
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.9f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (type) {
            case PHONE: {
                // handset: rounded earpiece top-left, mouthpiece bottom-right, joined by a curved grip
                Path h = new Path();
                h.moveTo(6.2f, 3.6f);
                h.lineTo(8.6f, 3.6f);
                h.quadTo(9.5f, 3.6f, 9.8f, 4.5f);
                h.lineTo(10.6f, 7.4f);
                h.quadTo(10.8f, 8.2f, 10.2f, 8.8f);
                h.lineTo(8.7f, 10.1f);
                h.quadTo(10.4f, 13.7f, 13.9f, 15.3f);
                h.lineTo(15.2f, 13.8f);
                h.quadTo(15.8f, 13.2f, 16.6f, 13.4f);
                h.lineTo(19.5f, 14.2f);
                h.quadTo(20.4f, 14.5f, 20.4f, 15.4f);
                h.lineTo(20.4f, 17.8f);
                h.quadTo(20.4f, 20.4f, 17.6f, 20.3f);
                h.quadTo(4.2f, 19.4f, 3.7f, 6.4f);
                h.quadTo(3.6f, 3.6f, 6.2f, 3.6f);
                h.close();
                c.drawPath(h, p);
                break;
            }
            case CAMERA: {
                Path body = new Path();
                body.addRoundRect(new RectF(3f, 6.6f, 21f, 19.4f), 3.2f, 3.2f, Path.Direction.CW);
                c.drawPath(body, p);
                Path hump = new Path();
                hump.moveTo(8.4f, 6.6f); hump.lineTo(9.6f, 4.6f); hump.lineTo(14.4f, 4.6f); hump.lineTo(15.6f, 6.6f);
                c.drawPath(hump, p);
                c.drawCircle(12f, 13f, 3.4f, p);
                break;
            }
            case LOCK: {
                c.drawRoundRect(new RectF(5.5f, 10.5f, 18.5f, 20.5f), 2.6f, 2.6f, p);
                Path shackle = new Path();
                shackle.moveTo(8.3f, 10.5f); shackle.lineTo(8.3f, 7.6f);
                shackle.arcTo(new RectF(8.3f, 3.9f, 15.7f, 11.3f), 180f, 180f, false);
                shackle.lineTo(15.7f, 10.5f);
                c.drawPath(shackle, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(12f, 15.3f, 1.25f, p);
                break;
            }
            case ALARM: {
                c.drawCircle(12f, 13.2f, 7.2f, p);
                Path hands = new Path();
                hands.moveTo(12f, 9.4f); hands.lineTo(12f, 13.4f); hands.lineTo(14.6f, 15f);
                c.drawPath(hands, p);
                Path bells = new Path();
                bells.moveTo(3.6f, 6.4f); bells.lineTo(6.6f, 3.8f);
                bells.moveTo(20.4f, 6.4f); bells.lineTo(17.4f, 3.8f);
                c.drawPath(bells, p);
                break;
            }
            case CALENDAR: {
                c.drawRoundRect(new RectF(4f, 5.4f, 20f, 20.2f), 3f, 3f, p);
                c.drawLine(4f, 9.8f, 20f, 9.8f, p);
                c.drawLine(8.4f, 3.4f, 8.4f, 6.6f, p);
                c.drawLine(15.6f, 3.4f, 15.6f, 6.6f, p);
                break;
            }
            default: break;
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
