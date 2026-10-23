package com.hammad.redmagictweaks;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

/**
 * Status bar clock customisation ported from nubia-ly's clock editor (ClockStyle/Color/Date/Visibility backends):
 * font family, bold / italic, colour, size, letter spacing, a date next to the time, position nudges and hiding.
 * Hooked in com.android.systemui by xposed/ClockHooks. Seconds live in StatusBarFeatures (SB_CLOCK_SECONDS).
 */
public final class ClockFeatures {
    private ClockFeatures() {}

    public static final String CLK_FONT = "clk_font";
    public static final String CLK_BOLD = "clk_bold";
    public static final String CLK_ITALIC = "clk_italic";
    public static final String CLK_COLOR = "clk_color";
    public static final String CLK_SIZE = "clk_size";
    public static final String CLK_SPACING = "clk_spacing";
    public static final String CLK_DATE = "clk_date";
    public static final String CLK_DATE_POS = "clk_date_pos";
    public static final String CLK_DATE_SEP = "clk_date_sep";
    public static final String CLK_DATE_CASE = "clk_date_case";
    public static final String CLK_OFFSET_X = "clk_offset_x";
    public static final String CLK_OFFSET_Y = "clk_offset_y";
    public static final String CLK_HIDE = "clk_hide";

    /** {label, family, style} — nubia-ly LockClockFonts.FACES; index 0 = stock. */
    public static final String[][] FONT_DEFS = {
        {"Stock", "", "0"},
        {"Sans", "sans-serif", "0"}, {"Sans bold", "sans-serif", "1"}, {"Sans italic", "sans-serif", "2"}, {"Sans bold italic", "sans-serif", "3"},
        {"Thin", "sans-serif-thin", "0"}, {"Thin italic", "sans-serif-thin", "2"}, {"Light", "sans-serif-light", "0"}, {"Light italic", "sans-serif-light", "2"}, {"Medium", "sans-serif-medium", "0"}, {"Black", "sans-serif-black", "0"},
        {"Condensed", "sans-serif-condensed", "0"}, {"Condensed bold", "sans-serif-condensed", "1"}, {"Condensed italic", "sans-serif-condensed", "2"}, {"Condensed bold italic", "sans-serif-condensed", "3"},
        {"Serif", "serif", "0"}, {"Serif bold", "serif", "1"}, {"Serif italic", "serif", "2"}, {"Serif bold italic", "serif", "3"},
        {"Mono", "monospace", "0"}, {"Mono bold", "monospace", "1"}, {"Mono italic", "monospace", "2"}, {"Serif mono", "serif-monospace", "0"},
        {"Casual", "casual", "0"}, {"Cursive", "cursive", "0"}, {"Small caps", "sans-serif-smallcaps", "0"},
    };
    public static final String[] FONTS = labels(FONT_DEFS);

    /** Named colours shared by the clock, status icons and battery pickers; index 0 = stock (no override). */
    public static final String[] COLOR_NAMES = {"Stock", "White", "Black", "Red", "Orange", "Amber", "Green", "Teal", "Cyan", "Blue", "Purple", "Pink"};
    public static final int[] COLOR_VALUES = {0, 0xFFFFFFFF, 0xFF000000, 0xFFF44336, 0xFFFF7A1A, 0xFFFFC107, 0xFF4CAF50, 0xFF1DBFAE, 0xFF00D2FF, 0xFF4F8CFF, 0xFFA86BFF, 0xFFFF6BB5};

    public static final String[] SIZES = {"Stock", "80 %", "85 %", "90 %", "95 %", "105 %", "110 %", "115 %", "120 %", "130 %", "140 %"};
    public static final int[] SIZE_PERCENT = {100, 80, 85, 90, 95, 105, 110, 115, 120, 130, 140};
    public static final String[] SPACINGS = {"Stock", "Tighter (−0.05 em)", "Slightly tighter (−0.02 em)", "Slightly wider (+0.02 em)", "Wider (+0.05 em)", "Wide (+0.10 em)", "Widest (+0.15 em)"};
    /** Letter-spacing offset in hundredths of an em (nubia-ly ClockStylePolicy range −5…15). */
    public static final int[] SPACING_HUNDREDTHS = {0, -5, -2, 2, 5, 10, 15};

    /** nubia-ly ClockDatePolicy labels and SimpleDateFormat patterns (index 0 = off). */
    public static final String[] DATE_FORMATS = {"Off", "Day of week", "Month & day", "Day & date", "Numeric date", "Full weekday", "Full month & day", "Day & month", "Day/month", "Month/day/year", "Day/month/year", "ISO date", "Full day & date"};
    public static final String[] DATE_PATTERNS = {"", "EEE", "MMM d", "EEE, MMM d", "MM/dd", "EEEE", "MMMM d", "d MMM", "dd/MM", "MM/dd/yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "EEEE, MMMM d"};
    public static final String[] DATE_POSITIONS = {"After the time", "Before the time"};
    public static final String[] DATE_SEPARATORS = {"Dot  ·", "Space", "Dash  —", "Bar  |"};
    public static final String[] DATE_SEPARATOR_TEXT = {" · ", " ", " — ", " | "};
    public static final String[] DATE_CASES = {"Stock case", "UPPERCASE", "lowercase"};

    public static final String[] OFFSETS_X = {"Stock", "Left 2 dp", "Left 4 dp", "Left 8 dp", "Right 2 dp", "Right 4 dp", "Right 8 dp"};
    public static final int[] OFFSETS_X_DP = {0, -2, -4, -8, 2, 4, 8};
    public static final String[] OFFSETS_Y = {"Stock", "Up 2 dp", "Up 4 dp", "Down 2 dp", "Down 4 dp"};
    public static final int[] OFFSETS_Y_DP = {0, -2, -4, 2, 4};

    private static String[] labels(String[][] v) { String[] l = new String[v.length]; for (int i = 0; i < v.length; i++) l[i] = v[i][0]; return l; }

    public static final Features.Section SECTION = new Features.Section("sbclock", "Status bar clock",
        "Font, colour, size, spacing, date and position of the clock in the top status bar",
        R.drawable.ic_sysui,
        new String[]{"com.android.systemui"},
        new Features.Feature(CLK_FONT, "Clock font", "Typeface of the status bar clock. Applies on the next clock tick (within a minute).", Features.Type.CHOICE, FONTS, 0),
        new Features.Feature(CLK_BOLD, "Bold clock", "Draws the clock in bold on top of the chosen font."),
        new Features.Feature(CLK_ITALIC, "Italic clock", "Slants the clock text."),
        new Features.Feature(CLK_COLOR, "Clock colour", "Fixed colour for the clock instead of the automatic light / dark tint. Applies on the next clock tick.", Features.Type.CHOICE, COLOR_NAMES, 0),
        new Features.Feature(CLK_SIZE, "Clock size", "Text size relative to stock.", Features.Type.CHOICE, SIZES, 0).num(60, 160, 1, "%"),
        new Features.Feature(CLK_SPACING, "Letter spacing", "Space between the digits of the clock (a typed value is in hundredths of a letter width: 5 = 0.05).", Features.Type.CHOICE, SPACINGS, 0).num(-10, 20, 1, "/100 em"),
        new Features.Feature(CLK_DATE, "Date next to the clock", "Adds the date to the status bar clock. Ignored while a custom \"Status bar clock format\" is selected in Status bar & notifications.", Features.Type.CHOICE, DATE_FORMATS, 0),
        new Features.Feature(CLK_DATE_POS, "Date position", "", Features.Type.CHOICE, DATE_POSITIONS, 0),
        new Features.Feature(CLK_DATE_SEP, "Date separator", "Character between the time and the date.", Features.Type.CHOICE, DATE_SEPARATORS, 0),
        new Features.Feature(CLK_DATE_CASE, "Date letter case", "", Features.Type.CHOICE, DATE_CASES, 0),
        new Features.Feature(CLK_OFFSET_X, "Nudge sideways", "Shifts the clock left or right.", Features.Type.CHOICE, OFFSETS_X, 0).num(-20, 20, 1, "dp"),
        new Features.Feature(CLK_OFFSET_Y, "Nudge up / down", "Shifts the clock up or down.", Features.Type.CHOICE, OFFSETS_Y, 0).num(-10, 10, 1, "dp"),
        new Features.Feature(CLK_HIDE, "Hide the status bar clock", "Removes the clock from the top status bar (the pull-down header clock stays). Applies within a minute.")
    );

    static {
        Previews.register(CLK_FONT, (ctx, i) -> i <= 0 || i >= FONT_DEFS.length ? null
                : new TextPreview(ctx, "12:34", FONT_DEFS[i][0], Typeface.create(FONT_DEFS[i][1], Integer.parseInt(FONT_DEFS[i][2]))));
        Previews.register(CLK_COLOR, (ctx, i) -> colorDot(ctx, i));
    }

    /** Filled circle for a named colour; null for index 0 (stock). */
    public static Drawable colorDot(Context ctx, int i) {
        if (i <= 0 || i >= COLOR_VALUES.length) return null;
        return new ColorDot(COLOR_VALUES[i], dp(ctx, 48));
    }

    static int dp(Context c, float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics())); }

    /** Colour of regular text in the app theme, so previews stay visible in light and dark mode. */
    static int textColor(Context ctx) {
        // previews are drawn on our dark cards: always use the app's light text colour
        try { return ctx.getColor(R.color.text); } catch (Throwable ignored) { }
        try {
            TypedValue tv = new TypedValue();
            if (ctx.getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
                if (tv.resourceId != 0) return ctx.getColorStateList(tv.resourceId).getDefaultColor();
                return tv.data;
            }
        } catch (Throwable ignored) { }
        return 0xFF202020;
    }

    /** Big sample text on top and the face name below, both in the previewed typeface. */
    public static final class TextPreview extends Drawable {
        private final String sample, name; private final Typeface face; private final int color, w, h;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public TextPreview(Context ctx, String sample, String name, Typeface face) {
            this.sample = sample; this.name = name; this.face = face; color = textColor(ctx); w = dp(ctx, 120); h = dp(ctx, 72);
        }
        @Override public int getIntrinsicWidth() { return w; }
        @Override public int getIntrinsicHeight() { return h; }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            paint.setColor(color); paint.setTypeface(face); paint.setTextAlign(Paint.Align.CENTER);
            fit(sample, b.width() * 0.9f, b.height() * 0.5f);
            Paint.FontMetrics m = paint.getFontMetrics();
            float y1 = b.top + b.height() * 0.36f - (m.ascent + m.descent) / 2;
            c.drawText(sample, b.centerX(), y1, paint);
            fit(name, b.width() * 0.9f, b.height() * 0.2f);
            m = paint.getFontMetrics();
            float y2 = b.top + b.height() * 0.78f - (m.ascent + m.descent) / 2;
            c.drawText(name, b.centerX(), y2, paint);
        }
        private void fit(String s, float width, float size) {
            paint.setTextSize(size);
            float measured = paint.measureText(s);
            if (measured > width && measured > 0) paint.setTextSize(size * width / measured);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Filled disc with a thin outline so white and black stay visible on any card. */
    public static final class ColorDot extends Drawable {
        private final int color, size; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public ColorDot(int color, int size) { this.color = color; this.size = size; }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float r = Math.min(b.width(), b.height()) * 0.38f;
            paint.setStyle(Paint.Style.FILL); paint.setColor(color);
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(1f, r * 0.08f)); paint.setColor(0x66808080);
            c.drawCircle(b.exactCenterX(), b.exactCenterY(), r, paint);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
