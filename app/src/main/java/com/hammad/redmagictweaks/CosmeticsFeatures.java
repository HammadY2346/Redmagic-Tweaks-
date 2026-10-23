package com.hammad.redmagictweaks;

import android.content.Context;

/** Look & feel of System UI ported from nubia-ly: battery icon shapes, lock-clock font, QS tile shapes and finishes, notification, power-menu and slider styles (hooked in com.android.systemui by xposed/CosmeticsHooks). */
public final class CosmeticsFeatures {
    private CosmeticsFeatures() {}

    public static final String COS_BATTERY_STYLE = "cos_battery_style";
    public static final String COS_BATTERY_PERCENT = "cos_battery_percent";
    public static final String COS_BATTERY_HIDE_CHARGE = "cos_battery_hide_charge";
    public static final String COS_BATTERY_SCALE = "cos_battery_scale";
    /** Stock battery shape only: draw the inside percentage solid and contrasting instead of RedMagic's see-through digits. */
    public static final String COS_BATTERY_STOCK_READABLE = "cos_battery_stock_readable";
    public static final String COS_CLOCK_FONT = "cos_clock_font";
    /** ACTION: the UI wires this to a document picker (any file type; .ttf / .otf expected) and calls Frames.fontImport(ctx, uri). */
    public static final String COS_FONT_IMPORT = "cos_font_import";
    /** Typeface of all System UI text (status bar, Quick Settings / Control Center, notifications, lock screen, volume, power menu). */
    public static final String COS_SYSUI_FONT = "cos_sysui_font";
    public static final String[] SYSUI_FONTS = {"Stock", "One UI style (Inter)"};
    public static final int SYSUI_FONT_ONE_UI = 1;
    public static final String COS_QS_SHAPE = "cos_qs_shape";
    public static final String COS_QS_SHAPE_LARGE = "cos_qs_shape_large";
    public static final String COS_QS_STYLE = "cos_qs_style";
    public static final String COS_NOTIF_STYLE = "cos_notif_style";
    public static final String COS_NOTIF_OPACITY = "cos_notif_opacity";
    public static final String COS_POWER_STYLE = "cos_power_style";
    public static final String COS_BRIGHTNESS_STYLE = "cos_brightness_style";
    public static final String COS_VOLUME_STYLE = "cos_volume_style";

    /** Battery icon shapes (index = nubia-ly BatteryStyles id; 5 = text only, 6 = hidden). */
    public static final String[] BATTERY_STYLES = {"Stock", "Portrait", "Circle", "Dotted circle", "Filled circle", "Text only", "Hidden", "Landscape right", "Landscape left", "Big circle", "Big dotted circle", "Capsule", "Lorn", "Portrait iOS", "Portrait Mx", "Airoo", "Landscape right A", "Landscape left A", "Landscape right B", "Landscape left B", "iOS 15", "iOS 16", "Origami", "OneUI 7", "MiUI Pill", "One UI 8"};
    public static final int BATTERY_TEXT = 5, BATTERY_HIDDEN = 6, BATTERY_ONE_UI8 = 25;

    /** {label, family, style}; index 0 = stock, last = imported font file. */
    public static final String[][] CLOCK_FONT_DEFS = {
        {"Stock", "", "0"},
        {"Sans", "sans-serif", "0"}, {"Sans bold", "sans-serif", "1"}, {"Sans italic", "sans-serif", "2"}, {"Sans bold italic", "sans-serif", "3"},
        {"Thin", "sans-serif-thin", "0"}, {"Thin italic", "sans-serif-thin", "2"}, {"Light", "sans-serif-light", "0"}, {"Light italic", "sans-serif-light", "2"}, {"Medium", "sans-serif-medium", "0"}, {"Black", "sans-serif-black", "0"},
        {"Condensed", "sans-serif-condensed", "0"}, {"Condensed bold", "sans-serif-condensed", "1"}, {"Condensed italic", "sans-serif-condensed", "2"}, {"Condensed bold italic", "sans-serif-condensed", "3"},
        {"Serif", "serif", "0"}, {"Serif bold", "serif", "1"}, {"Serif italic", "serif", "2"}, {"Serif bold italic", "serif", "3"},
        {"Mono", "monospace", "0"}, {"Mono bold", "monospace", "1"}, {"Mono italic", "monospace", "2"}, {"Serif mono", "serif-monospace", "0"},
        {"Casual", "casual", "0"}, {"Cursive", "cursive", "0"}, {"Small caps", "sans-serif-smallcaps", "0"},
        {"Imported font file (see below)", "custom", "0"},
    };
    public static final String[] CLOCK_FONTS = labels(CLOCK_FONT_DEFS);
    public static final int CLOCK_FONT_CUSTOM = CLOCK_FONT_DEFS.length - 1;

    public static final String[] QS_SHAPES = {"Stock", "Square", "Rounded square", "Leaf", "Pill", "Custom 10 %", "Custom 35 %"};
    /** Corner radius as a fraction of the tile's smaller side; -1 = leaf (two opposite corners pill-round). */
    public static final float[] QS_SHAPE_FACTOR = {0f, 0f, 0.24f, -1f, 0.5f, 0.10f, 0.35f};
    public static final String[] QS_STYLES = {"Stock", "Outline", "CyberPunk", "Neumorph", "Reflected", "Shaded", "Surround", "Thin"};
    public static final String[] NOTIF_STYLES = {"Stock", "Duoline", "CyberPunk", "Fluid", "iOS", "Layers"};
    public static final String[] NOTIF_OPACITY = {"Stock", "90 %", "80 %", "70 %", "60 %", "50 %"};
    public static final int[] NOTIF_OPACITY_PERCENT = {100, 90, 80, 70, 60, 50};
    public static final String[] POWER_STYLES = NOTIF_STYLES;
    public static final String[] BRIGHTNESS_STYLES = {"Stock", "Outline", "Neumorph", "Thin"};
    public static final String[] VOLUME_STYLES = {"Stock", "Outline", "Gradient", "Neumorph", "Neumorph Outline", "Double Layer", "Shaded Layer"};

    static final String STYLE_DESC = "Draws the status bar, lock screen and Control Center / Quick Settings battery in another shape (shapes adapted from RisingOS; \"One UI 8\" is a rounded pill with the number always inside, black or white against the fill so it is readable on light and dark wallpapers, green while charging, orange in power saving, red at 15 % and below). Restart System UI after changing.";
    static final String STOCK_READABLE_DESC = "Only for the stock battery shape with the percentage inside: RedMagic cuts the digits out of the pill and paints them over in nearly the same colour, so the number is hard to see and disappears in the Control Center. This draws the number solid in black or white against the pill instead. Restart System UI after changing.";

    private static String[] labels(String[][] v) { String[] l = new String[v.length]; for (int i = 0; i < v.length; i++) l[i] = v[i][0]; return l; }

    public static final Features.Section SECTION = new Features.Section("cosmetics", "Look & feel",
        "Battery icon shapes, lock-screen clock font, Quick Settings, notification, power-menu and slider styles",
        R.drawable.ic_sysui,
        new String[]{"com.android.systemui"},
        new Features.Feature(COS_BATTERY_STYLE, "Status bar battery icon", STYLE_DESC, Features.Type.CHOICE, BATTERY_STYLES, 0),
        new Features.Feature(COS_BATTERY_STOCK_READABLE, "Readable number in the stock battery", STOCK_READABLE_DESC),
        new Features.Feature(COS_BATTERY_PERCENT, "Level inside the custom battery icon", "Writes the percentage inside the custom shape. Set the stock battery style to \"icon only\" so the two do not overlap."),
        new Features.Feature(COS_BATTERY_HIDE_CHARGE, "Hide the charging symbol", "Removes the lightning bolt shown next to / inside the status bar battery while charging (stock and custom shapes). Applies within a minute."),
        new Features.Feature(COS_BATTERY_SCALE, "Battery icon size", "Scales the whole status bar battery (icon and percentage). Applies within a minute.", Features.Type.CHOICE, IconPackFeatures.SCALES, 0).num(50, 160, 1, "%"),
        new Features.Feature(COS_SYSUI_FONT, "System UI font", "Font of every text System UI draws: status bar clock, Quick Settings and Control Center labels, notifications, lock screen, volume pop-up and power menu (apps keep theirs). \"One UI style\" is Inter, a free font (SIL Open Font License) close to Samsung's One UI Sans, in regular, medium and bold. Restart System UI after changing.", Features.Type.CHOICE, SYSUI_FONTS, 0),
        new Features.Feature(COS_CLOCK_FONT, "Lock screen clock font", "Font family for the lock-screen and Always-on clock (the default and the split clock styles). Lock and unlock, or restart System UI, after changing.", Features.Type.CHOICE, CLOCK_FONTS, 0),
        new Features.Feature(COS_FONT_IMPORT, "Import a clock font file…", "Pick a .ttf / .otf file; choose \"Imported font file\" above to use it. Restart System UI after importing a new file.", Features.Type.ACTION, null, 0)
    );

    static {
        Previews.register(COS_BATTERY_STYLE, (ctx, i) -> i <= 0 || i >= BATTERY_STYLES.length ? null : new BatteryPreview(ctx, i), "Shown at 60 % (left) and charging (right)");
    }

    /** Two samples of a shape drawn by the ported renderer: 60 % normal and 60 % charging. */
    public static final class BatteryPreview extends android.graphics.drawable.Drawable {
        private final int style, w, h, fg, frame;
        public BatteryPreview(Context ctx, int style) {
            this.style = style; w = ClockFeatures.dp(ctx, 120); h = ClockFeatures.dp(ctx, 56);
            fg = ClockFeatures.textColor(ctx); frame = (fg & 0x00ffffff) | 0x99000000;
        }
        @Override public int getIntrinsicWidth() { return w; }
        @Override public int getIntrinsicHeight() { return h; }
        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            if (b.isEmpty()) return;
            if (style == BATTERY_HIDDEN) {
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                p.setColor(frame); p.setStyle(android.graphics.Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, b.height() / 24f));
                float inset = b.height() * 0.3f;
                android.graphics.RectF r = new android.graphics.RectF(b.centerX() - inset * 1.6f, b.top + inset, b.centerX() + inset * 1.6f, b.bottom - inset);
                c.drawRoundRect(r, inset / 2, inset / 2, p); c.drawLine(r.left, r.bottom, r.right, r.top, p);
                return;
            }
            float cellW = b.width() / 2f, pad = b.height() * 0.2f;
            for (int i = 0; i < 2; i++) {
                android.graphics.RectF cell = new android.graphics.RectF(b.left + i * cellW + pad, b.top + pad, b.left + (i + 1) * cellW - pad, b.bottom - pad);
                try {
                    if (style == BATTERY_ONE_UI8) com.hammad.redmagictweaks.xposed.CosmeticsArt.drawBattery(style, c, cell, i == 1 ? 76 : 60, i == 1, false, true, i == 1 ? com.hammad.redmagictweaks.xposed.CosmeticsArt.ONE_UI8_CHARGING : fg, fg);
                    else com.hammad.redmagictweaks.xposed.CosmeticsArt.drawBattery(style, c, cell, 60, i == 1, false, true, fg, frame);
                } catch (Throwable ignored) { }
            }
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
