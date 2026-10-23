package com.hammad.redmagictweaks;

import com.hammad.redmagictweaks.xposed.OneUiVolumeArt;
import com.hammad.redmagictweaks.xposed.QsArt;
import com.hammad.redmagictweaks.xposed.SurfaceArt;

/**
 * Notification cards, heads-up behaviour, status-bar notification icons, volume sliders and the power menu, ported
 * from nubia-ly (NotificationBackend, NotificationBehaviorBackend, NotificationIconBackend, VolumeBackend,
 * PowerMenuBackend). Hooked in com.android.systemui by xposed/SurfaceHooks.
 * Supersedes CosmeticsFeatures.COS_NOTIF_STYLE / COS_NOTIF_OPACITY / COS_POWER_STYLE / COS_VOLUME_STYLE and
 * StatusBarFeatures.SB_NOTIF_MAX.
 */
public final class SurfaceFeatures {
    private SurfaceFeatures() {}

    public static final String NOTIF_STYLE = "notif_style";
    public static final String NOTIF_OPACITY = "notif_opacity";
    public static final String NOTIF_CORNERS = "notif_corners";
    public static final String NOTIF_NO_AUTO_HEADSUP = "notif_no_auto_headsup";
    public static final String NOTIF_OVERFLOW_COUNT = "notif_overflow_count";
    public static final String NOTIF_ICON_LIMIT = "notif_icon_limit";
    public static final String VOL_STYLE = "vol_style";
    public static final String PWR_STYLE = "pwr_style";
    public static final String VOL_ONEUI = "vol_oneui8";
    public static final String PWR_ONEUI = "pwr_oneui8";
    public static final String VOL_POSITION = OneUiVolumeArt.VOL_POS, VOL_TIMEOUT = OneUiVolumeArt.VOL_TIMEOUT;
    public static final String VOL_SIDE = OneUiVolumeArt.VOL_SIDE, VOL_MARGIN = OneUiVolumeArt.VOL_MARGIN;
    public static final String VOL_ONEUI_WIDTH = OneUiVolumeArt.VOL_WIDTH, VOL_ONEUI_HEIGHT = OneUiVolumeArt.VOL_HEIGHT, VOL_ONEUI_SHAPE = OneUiVolumeArt.VOL_SHAPE,
            VOL_ONEUI_COLOR = OneUiVolumeArt.VOL_FILL, VOL_ONEUI_TRACK = OneUiVolumeArt.VOL_TRACK, VOL_ONEUI_ICON = OneUiVolumeArt.VOL_ICON,
            VOL_ONEUI_LEVEL = OneUiVolumeArt.VOL_LEVEL;
    public static final String PWR_ONEUI_SIZE = OneUiVolumeArt.PWR_SIZE, PWR_ONEUI_SHAPE = OneUiVolumeArt.PWR_SHAPE,
            PWR_ONEUI_COLORS = OneUiVolumeArt.PWR_COLORS, PWR_ONEUI_LABELS = OneUiVolumeArt.PWR_LABELS;

    // notification cards: tint, space between cards, side margin
    public static final String NOTIF_TINT = "notif_tint", NOTIF_GAP = "notif_gap", NOTIF_SIDE = "notif_side";
    public static final String[] NOTIF_GAPS = {"Stock", "Closer (4 dp less)", "4 dp more", "8 dp more", "12 dp more", "16 dp more"};
    public static final int[] NOTIF_GAP_DP = {0, -4, 4, 8, 12, 16};
    public static final String[] NOTIF_SIDES = {"Stock", "Wider cards (8 dp less margin)", "Wider cards (4 dp less margin)", "Narrower cards (4 dp more margin)",
            "Narrower cards (8 dp more margin)", "Narrower cards (16 dp more margin)"};
    public static final int[] NOTIF_SIDE_DP = {0, -8, -4, 4, 8, 16};
    // power menu (stock and One UI 8 buttons alike)
    public static final String PWR_POSITION = "pwr_position", PWR_LAYOUT = "pwr_layout", PWR_DIM = "pwr_dim";
    public static final String[] PWR_POSITIONS = {"Stock", "Higher (80 dp)", "Much higher (160 dp)", "Lower (80 dp)", "Much lower (160 dp)"};
    public static final int[] PWR_POSITION_DP = {0, -80, -160, 80, 160};
    public static final String[] PWR_LAYOUTS = {"Stock", "One row", "2 columns", "3 columns", "Vertical list"};
    /** Columns per layout choice; 0 = stock, -1 = everything in one row. */
    public static final int[] PWR_LAYOUT_COLS = {0, -1, 2, 3, 1};
    public static final String[] PWR_DIMS = {"Stock", "None", "Light (30 %)", "Medium (50 %)", "Strong (70 %)", "Almost black (90 %)"};
    public static final int[] PWR_DIM_PCT = {-1, 0, 30, 50, 70, 90};

    /** The preset's value, index 0 = stock. */
    public static int pick(int[] values, int choice) { return choice > 0 && choice < values.length ? values[choice] : values[0]; }

    public static final String[] ICON_LIMITS = {"Stock", "1", "2", "3", "4", "5", "6", "7", "8", "10", "As many as fit"};
    public static final int[] ICON_LIMIT_VALUES = {0, 1, 2, 3, 4, 5, 6, 7, 8, 10, Integer.MAX_VALUE};

    public static int iconLimit(int choice) { return choice > 0 && choice < ICON_LIMIT_VALUES.length ? ICON_LIMIT_VALUES[choice] : 0; }

    public static final Features.Section SECTION = new Features.Section("surface", "Notifications, volume & power menu",
        "Notification card looks and behaviour, status-bar notification icons, volume slider and power-menu styles",
        R.drawable.ic_sysui,
        new String[]{"com.android.systemui"},
        new Features.Feature(NOTIF_STYLE, "Notification card finish", "Decorative finish drawn over each notification card background; the card colours and blur stay stock, except \"One UI\", which makes the card an opaque Samsung-style dark (or light) surface with a hairline edge. Applies immediately.", Features.Type.CHOICE, SurfaceArt.NOTIF_STYLES, 0),
        new Features.Feature(NOTIF_OPACITY, "Notification card opacity", "Makes the card backgrounds partly transparent (text and icons stay solid). Applies immediately.", Features.Type.CHOICE, SurfaceArt.OPACITIES, 0).num(30, 100, 5, "%"),
        new Features.Feature(NOTIF_CORNERS, "Notification card corners", "Rounds the card background corners more than stock (26 dp matches One UI 7/8). Applies immediately.", Features.Type.CHOICE, SurfaceArt.CORNERS, 0).num(1, 44, 1, "dp"),
        new Features.Feature(NOTIF_TINT, "Notification card tint", "Washes every notification card with a colour: your wallpaper colour, plain colours or a gradient. The text stays readable; the card finish, opacity and corners still apply. Applies immediately.", Features.Type.CHOICE, QsArt.PANEL_COLORS, 0),
        new Features.Feature(NOTIF_GAP, "Space between notification cards", "More (or a little less) space between the cards in the shade and on the lock screen. Restart System UI after changing.", Features.Type.CHOICE, NOTIF_GAPS, 0).num(-8, 32, 1, "dp"),
        new Features.Feature(NOTIF_SIDE, "Notification card side margin", "Narrower cards with more space at both sides, or wider cards that reach closer to the screen edges. Restart System UI after changing.", Features.Type.CHOICE, NOTIF_SIDES, 0).num(-16, 48, 1, "dp"),
        new Features.Feature(NOTIF_NO_AUTO_HEADSUP, "No automatic heads-up pop-ups", "Notifications no longer drop down as a floating banner over what you are doing; they go straight to the shade. Banners you pinned yourself (and full-screen alarms / calls) still show."),
        new Features.Feature(NOTIF_OVERFLOW_COUNT, "Show the hidden-notification count", "Replaces the small overflow dot in the status bar with \"+N\", the number of notification icons that did not fit."),
        new Features.Feature(NOTIF_ICON_LIMIT, "Notification icons in the status bar", "How many notification icons are shown before the overflow dot / count. \"As many as fit\" fills the free space.", Features.Type.CHOICE, ICON_LIMITS, 0).num(1, 15, 1, "icons"),
        new Features.Feature(VOL_ONEUI, "One UI 8 volume panel", "The pop-up you get with the volume keys looks like Samsung's: a slim, tall panel whose bar fills from a circle at the bottom, with the speaker / bell icon inside the fill (tap it to mute, as before). Works collapsed and expanded. Off: RedMagic's own bar, which the options below can restyle too. Applies the next time the pop-up opens."),
        new Features.Feature(VOL_ONEUI_WIDTH, "Panel width", "How thick the bar is. Default: 40 dp on the One UI panel (the blurred panel around it is 4 dp wider and the ringer button follows), RedMagic's 20 dp on its own bar. Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens.", Features.Type.CHOICE, OneUiVolumeArt.WIDTHS, 0).num(6, 64, 1, "dp"),
        new Features.Feature(VOL_ONEUI_HEIGHT, "Panel height", "How much taller than RedMagic's the panel is (default: 80 dp on the One UI panel, stock on RedMagic's). It never grows past the screen (in landscape it stays shorter). Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens.", Features.Type.CHOICE, OneUiVolumeArt.HEIGHTS, 0).num(0, 300, 5, "dp"),
        new Features.Feature(VOL_POSITION, "Panel position", "Moves the volume pop-up up or down the edge of the screen (RedMagic centres it). Any value from 400 dp up to 400 dp down. Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens.", Features.Type.CHOICE, OneUiVolumeArt.POSITIONS, 0).num(-400, 400, 5, "dp"),
        new Features.Feature(VOL_SIDE, "Panel side", "Which edge of the screen the volume pop-up sits on: the right (RedMagic's) or the left, where it slides in from. Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens.", Features.Type.CHOICE, OneUiVolumeArt.SIDES, 0),
        new Features.Feature(VOL_MARGIN, "Distance from the edge", "Moves the volume pop-up away from its screen edge (stock: right at the edge, past the camera cut-out). Any value up to 160 dp. Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens.", Features.Type.CHOICE, OneUiVolumeArt.MARGINS, 0).num(0, 160, 2, "dp"),
        new Features.Feature(VOL_TIMEOUT, "Hide after", "How long the volume pop-up stays after the last key press or touch before it hides (stock: about 3 seconds). Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens.", Features.Type.CHOICE, OneUiVolumeArt.TIMEOUTS, 0).num(1, 60, 1, "s"),
        new Features.Feature(VOL_ONEUI_SHAPE, "Bar shape", "The bar's shape: a pill like One UI, rounded, soft or sharp corners, a leaf, pointed ends, a thin line with a knob, segments, dots, an outline, or notched every tenth. With corner shapes the blurred panel and the ringer button take the same corners. Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens.", Features.Type.CHOICE, OneUiVolumeArt.SHAPES, 0),
        new Features.Feature(VOL_ONEUI_COLOR, "Bar colour", "Colour of the fill: One UI's light fill, your wallpaper colour, plain colours or a gradient from the bottom to the top. The icon turns dark or light to stay readable. Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens.", Features.Type.CHOICE, OneUiVolumeArt.FILLS, 0),
        new Features.Feature(VOL_ONEUI_TRACK, "Bar background", "The empty part of the bar: see-through glass, clear, dim, solid, or a faint tint of the bar colour. Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens.", Features.Type.CHOICE, OneUiVolumeArt.TRACKS, 0),
        new Features.Feature(VOL_ONEUI_ICON, "Speaker icon", "Inside the bottom of the bar (One UI's default), or under the bar (RedMagic's default). Inside needs a bar at least 24 dp thick. Either way, tapping it mutes. Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens.", Features.Type.CHOICE, OneUiVolumeArt.ICONS, 0),
        new Features.Feature(VOL_ONEUI_LEVEL, "Show the volume level", "A small number (0 to 100) at the top of the bar. Works on the One UI 8 panel and on RedMagic's own bar. Applies the next time the pop-up opens."),
        new Features.Feature(PWR_ONEUI, "One UI 8 power menu", "Samsung-style buttons in the power menu: Power off is a solid red circle, Restart a green one and the other buttons grey, all a little bigger (72 dp), with bold white names under them. Size, shape, colours and labels can be changed below. RedMagic's blurred background and tap-twice confirmation stay. Overrides the button finish. Applies the next time the menu opens."),
        new Features.Feature(PWR_ONEUI_SIZE, "Button size", "Size of the power-menu buttons. Applies the next time the menu opens.", Features.Type.CHOICE, OneUiVolumeArt.PWR_SIZES, 0).num(48, 104, 2, "dp"),
        new Features.Feature(PWR_ONEUI_SHAPE, "Button shape", "Circle like One UI, a squircle like One UI app icons, or rounder or sharper squares. Applies the next time the menu opens.", Features.Type.CHOICE, OneUiVolumeArt.PWR_SHAPES, 0),
        new Features.Feature(PWR_ONEUI_COLORS, "Button colours", "One UI's red, green and grey, your wallpaper colour, RedMagic red, all grey, see-through glass, or white with dark icons. Applies the next time the menu opens.", Features.Type.CHOICE, OneUiVolumeArt.PWR_COLOR_SETS, 0),
        new Features.Feature(PWR_ONEUI_LABELS, "Button names", "Style of the names under the buttons. Applies the next time the menu opens.", Features.Type.CHOICE, OneUiVolumeArt.PWR_LABEL_STYLES, 0),
        new Features.Feature(PWR_POSITION, "Power menu position", "Moves the power-menu buttons up or down the screen. Any value from 400 dp up to 400 dp down. Works with RedMagic's buttons and the One UI 8 buttons. Applies the next time the menu opens.", Features.Type.CHOICE, PWR_POSITIONS, 0).num(-400, 400, 5, "dp"),
        new Features.Feature(PWR_LAYOUT, "Power menu layout", "Arranges the power-menu buttons in one row, 2 or 3 columns, or a vertical list. Works with RedMagic's buttons and the One UI 8 buttons. Applies the next time the menu opens.", Features.Type.CHOICE, PWR_LAYOUTS, 0),
        new Features.Feature(PWR_DIM, "Power menu background dim", "How dark the screen behind the power menu gets, from none to almost black. RedMagic's blur stays. Applies the next time the menu opens.", Features.Type.CHOICE, PWR_DIMS, 0).num(0, 100, 5, "%"),
        new Features.Feature(VOL_STYLE, "Volume slider style", "Outline, gradient, neumorphic or layered look for RedMagic's own sliders, while none of the bar options above is changed. Applies the next time the pop-up opens.", Features.Type.CHOICE, SurfaceArt.VOLUME_STYLES, 0),
        new Features.Feature(PWR_STYLE, "Power menu button finish", "Decorative finish on the round Power and Restart buttons of the power menu. Applies the next time the menu opens.", Features.Type.CHOICE, SurfaceArt.CARD_STYLES, 0)
    );

    static {
        Previews.register(NOTIF_STYLE, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> SurfaceArt.paintNotificationPreview(c, i, w, h, d, 0, 0)), "Finish over two sample cards");
        Previews.register(NOTIF_OPACITY, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> SurfaceArt.paintNotificationPreview(c, 0, w, h, d, SurfaceArt.opacity(i), 0)));
        Previews.register(NOTIF_CORNERS, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> SurfaceArt.paintNotificationPreview(c, 0, w, h, d, 0, SurfaceArt.cornerDp(i))));
        Previews.register(NOTIF_TINT, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> paintTintPreview(c, w, h, d, QsArt.panelColors(ctx, i, true))), "Two cards with the tint");
        Previews.register(NOTIF_ICON_LIMIT, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 40, (c, w, h, d) -> SurfaceArt.paintOverflowPreview(c, false, iconLimit(i), w, h, QsFeatures.accent(ctx), QsFeatures.dim(ctx))));
        Previews.register(VOL_STYLE, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> SurfaceArt.paintVolumePreview(c, i, w, h, d)));
        Previews.register(PWR_STYLE, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 72, (c, w, h, d) -> SurfaceArt.paintPowerPreview(c, i, w, h, d)));
        for (final String k : new String[]{VOL_ONEUI_WIDTH, VOL_ONEUI_HEIGHT, VOL_ONEUI_SHAPE, VOL_ONEUI_COLOR, VOL_ONEUI_TRACK, VOL_ONEUI_ICON})
            Previews.register(k, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) ->
                    OneUiVolumeArt.paintCard(c, ctx, w, h, d, oneUi(ctx, k, i), VOL_ONEUI_HEIGHT.equals(k))));
        for (final String k : new String[]{PWR_ONEUI_SIZE, PWR_ONEUI_SHAPE, PWR_ONEUI_COLORS, PWR_ONEUI_LABELS})
            Previews.register(k, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> LookPreview.paintPowerCard(c, ctx, w, h, d, oneUi(ctx, k, i))));
    }

    /** Two notification cards washed with {@code colors} (null = stock). */
    static void paintTintPreview(android.graphics.Canvas c, int w, int h, float d, int[] colors) {
        if (colors == null) { SurfaceArt.paintNotificationPreview(c, 0, w, h, d, 0, 0); return; }
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        android.graphics.RectF all = new android.graphics.RectF(0, 0, w, h);
        QsArt.fillPaint(p, all, colors);
        p.setAlpha(NOTIF_TINT_ALPHA);
        p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP));
        int layer = c.saveLayer(all, null);
        try { SurfaceArt.paintNotificationPreview(c, 0, w, h, d, 0, 0); c.drawRect(all, p); } finally { c.restoreToCount(layer); }
    }
    /** Strength of the card tint (out of 255). */
    public static final int NOTIF_TINT_ALPHA = 0x55;

    /** The One UI volume / power look of the live settings with option {@code key} set to {@code i}. */
    public static OneUiVolumeArt.Look oneUi(android.content.Context ctx, String key, int i) {
        final Looks.V v = Looks.with(Looks.live(ctx), key, i);
        return OneUiVolumeArt.look(new OneUiVolumeArt.Src() {
            @Override public int choice(String k) { return v.choice(k); }
            @Override public boolean on(String k) { return v.on(k); }
        });
    }
}
