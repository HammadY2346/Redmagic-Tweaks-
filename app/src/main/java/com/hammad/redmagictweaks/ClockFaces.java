package com.hammad.redmagictweaks;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;

import java.util.Calendar;
import java.util.Locale;

/**
 * Lock screen clock faces for the One UI lock screen (xposed/OneUiLockHooks draws them in System UI, the app uses the
 * same code for the picker previews). Text, number and dial faces in the spirit of One UI's ClockFace list; every face
 * is drawn with Canvas from the time, the date, one colour and one size, so it needs no resources inside System UI.
 *
 * {@link #render} both measures (canvas null) and draws, so the height a face reports is exactly what it paints.
 */
public final class ClockFaces {
    private ClockFaces() {}

    public static final String[] NAMES = {
        "One UI 8 · stacked",
        "Classic",
        "Side by side",
        "Two-tone",
        "Slim",
        "Big hour",
        "Left stacked",
        "Words · it's … past …",
        "Words · hour minute",
        "Hour ring",
        "Retro LCD",
        "Dot matrix",
        "Analog · minimal",
        "Analog · numbers",
        "Analog · roman",
        "Bubbles",
        "Diagonal",
        "Twin dials",
    };
    public static final int COUNT = NAMES.length;

    /** What a face is drawn from. */
    public static final class Spec {
        public float size;          // main digit size in px (the "clock size" setting)
        public float density = 1f;
        public int color = 0xFFFFFFFF;
        public Typeface digits;     // rounded bold digits; null = system sans 600
        public boolean h24;
        public boolean shadow = true;
        public Calendar time = Calendar.getInstance();
        public Locale locale = Locale.getDefault();
    }

    /** Height in px that face needs at this width. */
    public static float height(int face, Spec s, float width) { return render(null, face, s, width); }

    public static void draw(Canvas c, int face, Spec s, float width) { render(c, face, s, width); }

    // ------------------------------------------------------------------ helpers

    private static final class Pen {
        final Canvas c;
        final Spec s;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        final Rect r = new Rect();
        Pen(Canvas c, Spec s) {
            this.c = c; this.s = s;
            reset();
        }
        void reset() {
            p.reset();
            p.setFlags(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            p.setColor(s.color);
            if (s.shadow) p.setShadowLayer(3f * s.density, 0, 0, 0x33000000);
        }
        Pen digits(float size) {
            reset();
            p.setTypeface(s.digits != null ? s.digits : Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 600, false));
            p.setTextSize(size);
            return this;
        }
        Pen font(String family, int weight, float size) {
            reset();
            p.setTypeface(Typeface.create(Typeface.create(family, Typeface.NORMAL), weight, false));
            p.setTextSize(size);
            return this;
        }
        Pen color(int c) { p.setColor(c); return this; }
        Pen align(Paint.Align a) { p.setTextAlign(a); return this; }
        /** Height of the digit glyphs (cap height of "0..9"). */
        float digitH() { p.getTextBounds("0123456789", 0, 10, r); return -r.top; }
        /** Cap height of letters. */
        float capH() { p.getTextBounds("HEDIM", 0, 5, r); return -r.top; }
        float width(String t) { return p.measureText(t); }
        /** Draws t with its cap line at {@code top} (baseline = top + capHeight); returns the baseline. */
        float textTop(String t, float x, float top, float cap) {
            float base = top + cap;
            if (c != null) c.drawText(t, x, base, p);
            return base;
        }
        /** Shrinks the text size until t fits in maxW. */
        void fit(String t, float maxW) {
            float w = p.measureText(t);
            if (w > maxW && w > 0) p.setTextSize(p.getTextSize() * maxW / w);
        }
    }

    static int alpha(int color, float a) { return (color & 0x00FFFFFF) | (Math.round(((color >>> 24) & 0xFF) * a) << 24); }

    private static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }
    private static int hour12(Calendar t) { int h = t.get(Calendar.HOUR); return h == 0 ? 12 : h; }
    private static String hh(Spec s) { return two(s.h24 ? s.time.get(Calendar.HOUR_OF_DAY) : hour12(s.time)); }
    private static String h(Spec s) { return String.valueOf(s.h24 ? s.time.get(Calendar.HOUR_OF_DAY) : hour12(s.time)); }
    private static String mm(Spec s) { return two(s.time.get(Calendar.MINUTE)); }
    private static String ampm(Spec s) { return s.h24 ? "" : (s.time.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM"); }
    private static String date(Spec s, String skeleton) {
        return DateFormat.format(DateFormat.getBestDateTimePattern(s.locale, skeleton), s.time).toString();
    }
    private static String up(Spec s, String t) { return t.toUpperCase(s.locale); }

    private static final String[] ONES = {"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty"};
    private static String words(int n) { return n < 20 ? ONES[n] : TENS[n / 10] + (n % 10 != 0 ? "-" + ONES[n % 10] : ""); }

    /** A centred date line at {@code top}; returns the y below it. */
    private static float dateLine(Pen pen, String text, float cx, float top, float size, float maxW) {
        pen.font("sans-serif-medium", 500, size).align(Paint.Align.CENTER);
        pen.fit(text, maxW);
        float cap = pen.capH();
        pen.textTop(text, cx, top, cap);
        return top + cap + size * 0.32f;
    }

    // ------------------------------------------------------------------ faces

    static float render(Canvas c, int face, Spec s, float W) {
        Pen pen = new Pen(c, s);
        float S = s.size, d = s.density, cx = W / 2f, maxW = W - 24 * d;
        switch (face) {
            default:
            case 0: { // One UI 8: hours over minutes, date under
                pen.digits(S).align(Paint.Align.CENTER);
                pen.p.setLetterSpacing(0.01f);
                float dh = pen.digitH();
                pen.textTop(hh(s), cx, 0, dh);
                float y = dh + S * 0.16f;
                pen.textTop(mm(s), cx, y, dh);
                y += dh + S * 0.22f;
                return dateLine(pen, date(s, "EEEdMMM"), cx, y, Math.max(15 * d, S * 0.2f), maxW);
            }
            case 1: { // Classic: 9:18 / Wednesday, October 21
                pen.digits(S * 0.95f).align(Paint.Align.CENTER);
                String t = h(s) + ":" + mm(s);
                pen.fit(t, maxW);
                float dh = pen.digitH();
                pen.textTop(t, cx, 0, dh);
                return dateLine(pen, date(s, "EEEEMMMMd"), cx, dh + S * 0.2f, Math.max(15 * d, S * 0.19f), maxW);
            }
            case 2: { // Side by side: 9:18 | WED, / OCTOBER 21
                pen.digits(S * 0.62f).align(Paint.Align.LEFT);
                String t = h(s) + ":" + mm(s);
                float tw = pen.width(t), dh = pen.digitH(), gap = S * 0.13f, line = Math.max(1.5f * d, S * 0.015f);
                String l1 = up(s, date(s, "EEE")) + ",", l2 = up(s, date(s, "MMMMd"));
                Pen dp = new Pen(c, s);
                dp.font("sans-serif-medium", 500, Math.max(12 * d, S * 0.15f)).align(Paint.Align.LEFT);
                float dw = Math.max(dp.width(l1), dp.width(l2)), cap = dp.capH(), lineGap = dp.p.getTextSize() * 0.42f;
                float total = tw + gap * 2 + line + dw, x = (W - total) / 2f;
                if (total > maxW) x = (W - maxW) / 2f;
                pen.textTop(t, x, 0, dh);
                float lx = x + tw + gap;
                if (c != null) {
                    Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    lp.setColor(alpha(s.color, 0.85f));
                    c.drawRect(lx, dh * 0.04f, lx + line, dh, lp);
                }
                float blockH = cap * 2 + lineGap, by = (dh - blockH) / 2f;
                dp.textTop(l1, lx + line + gap, by, cap);
                dp.textTop(l2, lx + line + gap, by + cap + lineGap, cap);
                return dh + S * 0.12f;
            }
            case 3: { // Two-tone: bold hours, translucent minutes, tall and narrow
                pen.digits(S * 1.3f).align(Paint.Align.LEFT);
                pen.p.setTextScaleX(0.8f);
                float dh = pen.digitH(), w1 = pen.width(hh(s)), w2 = pen.width(mm(s)), gap = S * 0.05f;
                float x = (W - (w1 + gap + w2)) / 2f;
                pen.textTop(hh(s), x, 0, dh);
                pen.color(alpha(s.color, 0.55f));
                pen.textTop(mm(s), x + w1 + gap, 0, dh);
                return dateLine(pen, date(s, "EEEdMMM"), cx, dh + S * 0.2f, Math.max(14 * d, S * 0.17f), maxW);
            }
            case 4: { // Slim: thin condensed 09:18 / Oct 21
                pen.font("sans-serif-condensed", 300, S * 1.3f).align(Paint.Align.CENTER);
                pen.p.setTextScaleX(0.86f);
                String t = hh(s) + ":" + mm(s);
                pen.fit(t, maxW);
                float dh = pen.digitH();
                pen.textTop(t, cx, 0, dh);
                Pen dp = new Pen(c, s);
                dp.font("sans-serif-condensed", 300, Math.max(16 * d, S * 0.26f)).align(Paint.Align.CENTER);
                String dl = date(s, "MMMd");
                float cap = dp.capH(), top = dh + S * 0.18f;
                dp.textTop(dl, cx, top, cap);
                return top + cap + dp.p.getTextSize() * 0.3f;
            }
            case 5: { // Big hour: huge 9, AM over 18 beside it
                pen.digits(S * 1.7f).align(Paint.Align.LEFT);
                String hs = h(s);
                float bigH = pen.digitH(), bw = pen.width(hs), gap = S * 0.1f;
                Pen mp = new Pen(c, s);
                mp.digits(S * 0.8f).align(Paint.Align.LEFT);
                float mh = mp.digitH(), mw = mp.width(mm(s));
                Pen ap = new Pen(c, s);
                ap.font("sans-serif-medium", 500, Math.max(11 * d, S * 0.15f)).align(Paint.Align.LEFT);
                String am = ampm(s);
                float x = (W - (bw + gap + Math.max(mw, ap.width(am)))) / 2f;
                pen.textTop(hs, x, 0, bigH);
                float mx = x + bw + gap;
                mp.textTop(mm(s), mx, bigH - mh, mh);
                if (!am.isEmpty()) { float cap = ap.capH(); ap.textTop(am, mx + mw * 0.06f, bigH - mh - cap - S * 0.12f, cap); }
                return dateLine(pen, up(s, date(s, "EEEMMMMd")), cx, bigH + S * 0.24f, Math.max(13 * d, S * 0.16f), maxW);
            }
            case 6: { // Left stacked: 09 / 18 / WEDNESDAY / 2026.10.21
                float x = Math.max(28 * d, W * 0.09f);
                pen.digits(S * 0.95f).align(Paint.Align.LEFT);
                float dh = pen.digitH();
                pen.textTop(hh(s), x, 0, dh);
                float y = dh + S * 0.14f;
                pen.textTop(mm(s), x, y, dh);
                y += dh + S * 0.24f;
                Pen dp = new Pen(c, s);
                dp.digits(Math.max(18 * d, S * 0.3f)).align(Paint.Align.LEFT);
                String day = up(s, date(s, "EEEE"));
                dp.fit(day, W - x - 16 * d);
                float cap = dp.capH();
                dp.textTop(day, x, y, cap);
                y += cap + S * 0.14f;
                Pen np = new Pen(c, s);
                np.font("sans-serif-medium", 500, Math.max(12 * d, S * 0.15f)).align(Paint.Align.LEFT);
                Calendar t = s.time;
                String num = t.get(Calendar.YEAR) + ". " + two(t.get(Calendar.MONTH) + 1) + ". " + two(t.get(Calendar.DAY_OF_MONTH));
                float cap2 = np.capH();
                np.textTop(num, x, y, cap2);
                return y + cap2 + np.p.getTextSize() * 0.3f;
            }
            case 7: { // Words: IT'S / EIGHTEEN / PAST / NINE
                int m = s.time.get(Calendar.MINUTE), hr = hour12(s.time), next = hr % 12 + 1;
                String[] lines;
                if (m == 0) lines = new String[]{"it's", ONES[hr], "o'clock"};
                else if (m == 15) lines = new String[]{"it's", "quarter", "past", ONES[hr]};
                else if (m == 30) lines = new String[]{"it's", "half", "past", ONES[hr]};
                else if (m == 45) lines = new String[]{"it's", "quarter", "to", ONES[next]};
                else if (m < 30) lines = new String[]{"it's", words(m), "past", ONES[hr]};
                else lines = new String[]{"it's", words(60 - m), "to", ONES[next]};
                float x = Math.max(28 * d, W * 0.09f), size = S * 0.44f;
                pen.digits(size).align(Paint.Align.LEFT);
                for (String l : lines) pen.fit(up(s, l), W - x - 16 * d);
                float cap = pen.capH(), y = 0, step = cap + pen.p.getTextSize() * 0.22f;
                for (int i = 0; i < 4; i++) {
                    if (i < lines.length) pen.textTop(up(s, lines[i]), x, y, cap);
                    y += step;
                }
                Pen dp = new Pen(c, s);
                dp.font("sans-serif-medium", 500, Math.max(12 * d, S * 0.14f)).align(Paint.Align.LEFT);
                float cap2 = dp.capH();
                dp.textTop(up(s, date(s, "MMdEEEE")), x, y + S * 0.06f, cap2);
                return y + S * 0.06f + cap2 + dp.p.getTextSize() * 0.3f;
            }
            case 8: { // Words: NINE / EIGHTEEN
                int m = s.time.get(Calendar.MINUTE);
                String l1 = up(s, ONES[hour12(s.time)]);
                String l2 = up(s, m == 0 ? "o'clock" : m < 10 ? "oh " + ONES[m] : words(m));
                pen.font("sans-serif", 500, S * 0.6f).align(Paint.Align.CENTER);
                pen.fit(l1, maxW);
                float cap = pen.capH();
                pen.textTop(l1, cx, 0, cap);
                float y = cap + S * 0.16f;
                Pen p2 = new Pen(c, s);
                p2.font("sans-serif", 400, S * 0.46f).align(Paint.Align.CENTER);
                p2.fit(l2, maxW);
                float cap2 = p2.capH();
                p2.textTop(l2, cx, y, cap2);
                return dateLine(pen, up(s, date(s, "EEEMMMMd")), cx, y + cap2 + S * 0.2f, Math.max(13 * d, S * 0.16f), maxW);
            }
            case 9: { // Hour ring: twelve arcs, as many lit as the hour, minutes inside
                float R = Math.min(S * 0.95f, maxW / 2f), stroke = Math.max(3 * d, S * 0.06f), cy = R + stroke;
                if (c != null) {
                    Paint rp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    rp.setStyle(Paint.Style.STROKE);
                    rp.setStrokeWidth(stroke);
                    rp.setStrokeCap(Paint.Cap.ROUND);
                    RectF o = new RectF(cx - R, cy - R, cx + R, cy + R);
                    int lit = hour12(s.time);
                    for (int i = 0; i < 12; i++) {
                        rp.setColor(i < lit ? s.color : alpha(s.color, 0.25f));
                        c.drawArc(o, -90 + i * 30 + 4, 22, false, rp);
                    }
                }
                pen.digits(S * 0.9f).align(Paint.Align.CENTER);
                float dh = pen.digitH();
                pen.textTop(mm(s), cx, cy - dh / 2f, dh);
                return dateLine(pen, up(s, date(s, "EEEMMMMd")), cx, cy + R + stroke + S * 0.22f, Math.max(13 * d, S * 0.16f), maxW);
            }
            case 10: { // Retro LCD panel
                float pw = Math.min(maxW, S * 3.3f), ph = S * 1.55f, px = (W - pw) / 2f, rad = S * 0.14f;
                int ink = 0xFF2E3D2A;
                if (c != null) {
                    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                    bg.setColor(0xFFB9CBB0);
                    if (s.shadow) bg.setShadowLayer(6 * d, 0, 2 * d, 0x40000000);
                    c.drawRoundRect(new RectF(px, 0, px + pw, ph), rad, rad, bg);
                    bg.clearShadowLayer();
                    bg.setStyle(Paint.Style.STROKE);
                    bg.setStrokeWidth(Math.max(2 * d, S * 0.04f));
                    bg.setColor(0xE6FFFFFF);
                    c.drawRoundRect(new RectF(px, 0, px + pw, ph), rad, rad, bg);
                }
                Pen tp = new Pen(c, s);
                tp.font("monospace", 400, S * 0.17f).align(Paint.Align.CENTER).color(ink);
                tp.p.clearShadowLayer();
                tp.p.setLetterSpacing(0.12f);
                float cap = tp.capH();
                tp.textTop(up(s, date(s, "EEE")) + " " + two(s.time.get(Calendar.MONTH) + 1) + "/" + two(s.time.get(Calendar.DAY_OF_MONTH)), cx, S * 0.2f, cap);
                String t = hh(s) + mm(s);
                float dw = S * 0.42f, dhh = S * 0.72f, gap = S * 0.1f, colon = S * 0.16f;
                float total = dw * 4 + gap * 3 + colon + gap, x = cx - total / 2f, y = S * 0.2f + cap + S * 0.14f;
                for (int i = 0; i < 4; i++) {
                    seg(c, t.charAt(i) - '0', x, y, dw, dhh, S * 0.085f, ink);
                    x += dw + gap;
                    if (i == 1) {
                        if (c != null) {
                            Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
                            cp.setColor(ink);
                            float q = S * 0.08f, ccx = x - gap / 2f + colon / 2f;
                            c.drawRect(ccx - q / 2, y + dhh * 0.3f - q / 2, ccx + q / 2, y + dhh * 0.3f + q / 2, cp);
                            c.drawRect(ccx - q / 2, y + dhh * 0.7f - q / 2, ccx + q / 2, y + dhh * 0.7f + q / 2, cp);
                        }
                        x += colon;
                    }
                }
                return ph + S * 0.1f;
            }
            case 11: { // Dot matrix 09:18
                String t = hh(s) + ":" + mm(s);
                float pitch = Math.min(S * 0.13f, maxW / 25f), dot = pitch * 0.36f, x = cx - pitch * 25 / 2f;
                if (c != null) {
                    Paint dp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    for (int i = 0; i < t.length(); i++) {
                        char ch = t.charAt(i);
                        if (ch == ':') {
                            dp.setColor(s.color);
                            c.drawCircle(x + pitch / 2f, pitch * 2.5f, dot, dp);
                            c.drawCircle(x + pitch / 2f, pitch * 4.5f, dot, dp);
                            x += pitch * 2;
                            continue;
                        }
                        int[] rows = FONT[ch - '0'];
                        for (int ry = 0; ry < 7; ry++) for (int rx = 0; rx < 5; rx++) {
                            boolean on = ((rows[ry] >> (4 - rx)) & 1) != 0;
                            dp.setColor(on ? s.color : alpha(s.color, 0.13f));
                            c.drawCircle(x + rx * pitch + pitch / 2f, ry * pitch + pitch / 2f, dot, dp);
                        }
                        x += pitch * 6;
                    }
                }
                return dateLine(pen, up(s, date(s, "EEEMMMMd")), cx, pitch * 7 + S * 0.22f, Math.max(13 * d, S * 0.16f), maxW);
            }
            case 12: case 13: case 14: { // Analog dials
                float R = Math.min(S * 1.15f, maxW / 2f), cy = R + 2 * d;
                if (c != null) dial(c, s, face, cx, cy, R);
                return dateLine(pen, up(s, date(s, "EEEMMMMd")), cx, cy + R + S * 0.22f, Math.max(13 * d, S * 0.16f), maxW);
            }
            case 15: { // Bubbles: hour in a big bubble, minutes in a small one
                float r1 = S * 0.78f, r2 = S * 0.4f, c1x = cx - S * 0.22f, c1y = r1, c2x = c1x + r1 + r2 * 0.35f, c2y = r1 + r1 * 0.55f;
                if (c != null) {
                    Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    bp.setColor(alpha(s.color, 0.28f));
                    c.drawCircle(c1x, c1y, r1, bp);
                    bp.setColor(alpha(s.color, 0.38f));
                    c.drawCircle(c2x, c2y, r2, bp);
                }
                pen.font("sans-serif", 400, S * 0.85f).align(Paint.Align.CENTER);
                float dh = pen.digitH();
                pen.textTop(h(s), c1x, c1y - dh / 2f, dh);
                Pen mp = new Pen(c, s);
                mp.font("sans-serif", 400, S * 0.4f).align(Paint.Align.CENTER);
                float mh = mp.digitH();
                mp.textTop(mm(s), c2x, c2y - mh / 2f, mh);
                return dateLine(pen, up(s, date(s, "EEEMMMMd")), cx, Math.max(c1y + r1, c2y + r2) + S * 0.22f, Math.max(13 * d, S * 0.16f), maxW);
            }
            case 16: { // Diagonal: 9 top left, 18 bottom right, the date along a line between them
                float aw = Math.min(maxW, S * 3f), ah = S * 2.1f, ax = (W - aw) / 2f;
                pen.font("sans-serif", 400, S * 1.05f).align(Paint.Align.LEFT);
                float dh = pen.digitH();
                pen.textTop(h(s), ax + S * 0.15f, 0, dh);
                pen.align(Paint.Align.RIGHT);
                pen.textTop(mm(s), ax + aw - S * 0.05f, ah - dh, dh);
                float x0 = ax, y0 = ah * 0.88f, x1 = ax + aw, y1 = ah * 0.12f;
                if (c != null) {
                    Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    lp.setColor(alpha(s.color, 0.75f));
                    lp.setStrokeWidth(Math.max(1.2f * d, S * 0.012f));
                    c.drawLine(x0, y0, x1, y1, lp);
                    Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
                    tp.setColor(s.color);
                    tp.setTextSize(Math.max(11 * d, S * 0.13f));
                    tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    tp.setTextAlign(Paint.Align.LEFT);
                    Path path = new Path();
                    path.moveTo(x0, y0);
                    path.lineTo(x1, y1);
                    String dl = up(s, date(s, "EEEMMMMd"));
                    float len = (float) Math.hypot(x1 - x0, y1 - y0);
                    c.drawTextOnPath(dl, path, Math.max(0f, (len - tp.measureText(dl)) / 2f), -tp.getTextSize() * 0.45f, tp);
                }
                return ah + S * 0.12f;
            }
            case 17: { // Twin dials: hours left, minutes right
                float rr = Math.min(S * 0.62f, (maxW - S * 0.25f) / 4f), gap = S * 0.25f;
                float l = cx - gap / 2f - rr, r = cx + gap / 2f + rr, cy = rr + 2 * d;
                if (c != null) {
                    Paint op = new Paint(Paint.ANTI_ALIAS_FLAG);
                    op.setStyle(Paint.Style.STROKE);
                    op.setColor(alpha(s.color, 0.9f));
                    op.setStrokeWidth(Math.max(1.5f * d, S * 0.018f));
                    for (float x : new float[]{l, r}) {
                        c.drawCircle(x, cy, rr, op);
                        for (int i = 0; i < 12; i++) {
                            double a = Math.toRadians(i * 30);
                            float in = rr * (i % 3 == 0 ? 0.8f : 0.87f), out = rr * 0.93f;
                            c.drawLine(x + (float) Math.sin(a) * in, cy - (float) Math.cos(a) * in, x + (float) Math.sin(a) * out, cy - (float) Math.cos(a) * out, op);
                        }
                    }
                }
                pen.font("sans-serif", 400, rr * 0.85f).align(Paint.Align.CENTER);
                float dh = pen.digitH();
                pen.textTop(h(s), l, cy - dh / 2f, dh);
                pen.textTop(mm(s), r, cy - dh / 2f, dh);
                return dateLine(pen, up(s, date(s, "EEEMMMMd")), cx, cy + rr + S * 0.22f, Math.max(13 * d, S * 0.16f), maxW);
            }
        }
    }

    /** 5 x 7 dot font for 0-9, one int per row, bit 4 = left column. */
    private static final int[][] FONT = {
        {0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110},
        {0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110},
        {0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111},
        {0b11111, 0b00010, 0b00100, 0b00010, 0b00001, 0b10001, 0b01110},
        {0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010},
        {0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110},
        {0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110},
        {0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000},
        {0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110},
        {0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100},
    };

    /** Seven-segment digits: segments a b c d e f g as bits 6..0. */
    private static final int[] SEG = {0x7E, 0x30, 0x6D, 0x79, 0x33, 0x5B, 0x5F, 0x70, 0x7F, 0x7B};

    private static void seg(Canvas c, int digit, float x, float y, float w, float h, float t, int ink) {
        if (c == null || digit < 0 || digit > 9) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int bits = SEG[digit];
        float mid = y + h / 2f, r = t / 2f, inset = t * 0.65f;
        RectF[] rects = {
            new RectF(x + inset, y - r, x + w - inset, y + r),                 // a
            new RectF(x + w - r, y + inset, x + w + r, mid - inset),           // b
            new RectF(x + w - r, mid + inset, x + w + r, y + h - inset),       // c
            new RectF(x + inset, y + h - r, x + w - inset, y + h + r),         // d
            new RectF(x - r, mid + inset, x + r, y + h - inset),               // e
            new RectF(x - r, y + inset, x + r, mid - inset),                   // f
            new RectF(x + inset, mid - r, x + w - inset, mid + r),             // g
        };
        for (int i = 0; i < 7; i++) {
            boolean on = ((bits >> (6 - i)) & 1) != 0;
            p.setColor(on ? ink : (ink & 0x00FFFFFF) | 0x16000000);
            c.drawRoundRect(rects[i], r, r, p);
        }
    }

    private static final String[] ROMAN = {"XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"};

    private static void dial(Canvas c, Spec s, int face, float cx, float cy, float R) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setColor(s.color);
        if (s.shadow) p.setShadowLayer(3f * s.density, 0, 0, 0x33000000);
        p.setStrokeCap(Paint.Cap.ROUND);
        if (face == 12) {
            for (int i = 0; i < 60; i++) {
                boolean hour = i % 5 == 0;
                p.setStrokeWidth(R * (hour ? 0.028f : 0.012f));
                p.setColor(hour ? s.color : alpha(s.color, 0.6f));
                tick(c, p, cx, cy, i * 6, R * (hour ? 0.84f : 0.92f), R * 0.98f);
            }
        } else if (face == 13) {
            p.setColor(s.color);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(R * 0.17f);
            Rect b = new Rect();
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30);
                if (i % 2 == 0) {
                    String n = String.valueOf(i == 0 ? 12 : i);
                    p.getTextBounds(n, 0, n.length(), b);
                    float tx = cx + (float) Math.sin(a) * R * 0.8f, ty = cy - (float) Math.cos(a) * R * 0.8f - b.exactCenterY();
                    p.setStyle(Paint.Style.FILL);
                    c.drawText(n, tx, ty, p);
                } else {
                    p.setStrokeWidth(R * 0.035f);
                    tick(c, p, cx, cy, i * 30, R * 0.78f, R * 0.92f);
                }
            }
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(R * 0.02f);
            c.drawCircle(cx, cy, R * 0.98f, p);
            c.drawCircle(cx, cy, R * 0.72f, p);
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
            p.setTextSize(R * 0.14f);
            Rect b = new Rect();
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30);
                String n = ROMAN[i];
                p.getTextBounds(n, 0, n.length(), b);
                c.drawText(n, cx + (float) Math.sin(a) * R * 0.85f, cy - (float) Math.cos(a) * R * 0.85f - b.exactCenterY(), p);
            }
        }
        // hands
        Calendar t = s.time;
        float min = t.get(Calendar.MINUTE), hr = t.get(Calendar.HOUR) + min / 60f;
        p.setStyle(Paint.Style.STROKE);
        p.setColor(s.color);
        p.setStrokeWidth(R * 0.06f);
        tick(c, p, cx, cy, hr * 30, -R * 0.08f, R * 0.5f);
        p.setStrokeWidth(R * 0.035f);
        tick(c, p, cx, cy, min * 6, -R * 0.1f, R * 0.78f);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, R * 0.05f, p);
    }

    private static void tick(Canvas c, Paint p, float cx, float cy, float deg, float from, float to) {
        double a = Math.toRadians(deg);
        float sx = (float) Math.sin(a), sy = (float) -Math.cos(a);
        c.drawLine(cx + sx * from, cy + sy * from, cx + sx * to, cy + sy * to, p);
    }

    // ------------------------------------------------------------------ app previews

    private static Typeface previewFace;
    private static boolean previewTried;

    static Typeface previewDigits(Context ctx) {
        if (!previewTried) {
            previewTried = true;
            try { previewFace = Typeface.createFromAsset(ctx.getAssets(), "fonts/oneui_clock.ttf"); } catch (Throwable ignored) { }
        }
        return previewFace;
    }

    /** Picker card: the face at 9:18 on Wednesday 21 October over a dark wallpaper-like gradient. */
    public static Drawable preview(Context ctx, int face) {
        final Typeface tf = previewDigits(ctx);
        final boolean h24 = DateFormat.is24HourFormat(ctx);
        return new QsFeatures.Sketch(ctx, 120, 132, (c, w, h, d) -> {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setShader(new LinearGradient(0, 0, 0, h, 0xFF1D2C52, 0xFF4A3F7E, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(0, 0, w, h), 14 * d, 14 * d, bg);
            Spec s = new Spec();
            s.density = d;
            s.digits = tf;
            s.h24 = h24;
            s.time = Calendar.getInstance();
            s.time.set(2026, Calendar.OCTOBER, 21, 9, 18, 0);
            float W = 385 * d;                        // laid out at phone width, then scaled into the card
            s.size = 108 * d;
            float fh = height(face, s, W);
            float scale = Math.min(w * 0.92f / W, h * 0.84f / Math.max(1f, fh));
            int save = c.save();
            c.translate((w - W * scale) / 2f, (h - fh * scale) / 2f);
            c.scale(scale, scale);
            draw(c, face, s, W);
            c.restoreToCount(save);
        });
    }
}
