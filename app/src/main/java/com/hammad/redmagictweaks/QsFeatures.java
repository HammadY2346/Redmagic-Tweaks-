package com.hammad.redmagictweaks;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.hammad.redmagictweaks.xposed.QsArt;

/**
 * Quick Settings layout and looks ported from nubia-ly (QsGridBackend, QsLayoutBackend, QsShapeBackend,
 * QsOutlineBackend, BrightnessBackend). Hooked in com.android.systemui by xposed/QsHooks.
 * Supersedes CosmeticsFeatures.COS_QS_SHAPE / COS_QS_SHAPE_LARGE / COS_QS_STYLE / COS_BRIGHTNESS_STYLE and
 * StatusBarFeatures.SB_QS_COLS / SB_QS_ROWS.
 */
public final class QsFeatures {
    private QsFeatures() {}

    // grid (portrait / landscape are independent)
    public static final String QS_COLS = "qs_cols";
    public static final String QS_ROWS = "qs_rows";
    public static final String QS_LAND_COLS = "qs_land_cols";
    public static final String QS_LAND_ROWS = "qs_land_rows";
    // normal-shade layout
    public static final String QS_TOP_LAYOUT = "qs_top_layout";
    public static final String QS_LABEL_POS = "qs_label_pos";
    public static final String QS_LABEL_LINES = "qs_label_lines";
    public static final String QS_BRIGHTNESS_POS = "qs_brightness_pos";
    public static final String QS_BRIGHTNESS_NO_SUN = "qs_brightness_no_sun";
    // looks
    public static final String QS_SHAPE_SMALL = "qs_shape_small";
    public static final String QS_SHAPE_LARGE = "qs_shape_large";
    public static final String QS_TILE_STYLE = "qs_tile_style";
    public static final String QS_BRIGHTNESS_STYLE = "qs_brightness_style";
    /** Colour of a tile that is on (QsArt.PANEL_COLORS) and of the brightness / volume fill, shade and Control Center. */
    public static final String QS_TILE_COLOR = "qs_tile_color";
    public static final String QS_SLIDER_COLOR = "qs_slider_color";
    // split shade / Control Center (com.zte.controlcenter.*)
    public static final String QS_CC_ENABLE = "qs_cc_enable";
    public static final String QS_CC_COLS = "qs_cc_cols";
    public static final String QS_CC_VOLUME_STYLE = "qs_cc_volume_style";
    /** Space between the Control Center's round tiles: the circle shrinks inside its cell and rows move apart. */
    public static final String QS_CC_TILE_GAP = "qs_cc_tile_gap";
    public static final String[] CC_TILE_GAPS = {"Stock", "Small gap", "Medium gap", "Large gap"};
    public static final float[] CC_TILE_GAP_SCALE = {1f, 0.90f, 0.84f, 0.78f};
    public static final int[] CC_TILE_GAP_ROW_DP = {0, 4, 8, 12};
    public static int ccGap(int choice) { return choice < 0 || choice >= CC_TILE_GAPS.length ? 0 : choice; }
    /** Row spacing in dp: a typed gap ({@code customDp} >= 0) or the preset's. */
    public static int ccGapRowDp(int choice, int customDp) { return customDp >= 0 ? customDp : CC_TILE_GAP_ROW_DP[ccGap(choice)]; }
    /** Circle scale inside its cell: a typed gap shrinks the circle like the presets do (4 dp 0.90, 8 dp 0.84, 12 dp 0.78). */
    public static float ccGapScale(int choice, int customDp) {
        if (customDp < 0) return CC_TILE_GAP_SCALE[ccGap(choice)];
        return customDp == 0 ? 1f : Math.max(0.55f, 1f - 0.04f - 0.015f * customDp);
    }
    public static final String QS_CC_SLIDER_POS = "qs_cc_slider_pos";
    public static final String QS_CC_SLIDER_SIZE = "qs_cc_slider_size";
    /** Whole-page Control Center layout (xposed/CcOneUiHooks + CcLayoutHooks): 0 stock, 1 One UI 8. */
    public static final String QS_CC_LAYOUT = "qs_cc_layout";
    public static final String QS_CC_ONEUI_BIG_CLOCK = "qs_cc_oneui_big_clock";
    public static final String QS_CC_ONEUI_NO_HEADER = "qs_cc_oneui_no_header";
    public static final String QS_CC_ONEUI_NO_MEDIA = "qs_cc_oneui_no_media";
    public static final String QS_CC_ONEUI_NO_SHORTCUTS = "qs_cc_oneui_no_shortcuts";
    public static final String QS_CC_HIDE_SEARCH = "qs_cc_hide_search";
    public static final String QS_CC_HIDE_EDIT = "qs_cc_hide_edit";
    public static final String QS_CC_HIDE_USER = "qs_cc_hide_user";
    public static final String QS_CC_HIDE_POWER = "qs_cc_hide_power";
    public static final String QS_CC_HIDE_SETTINGS = "qs_cc_hide_settings";
    public static final String QS_CC_HIDE_DATE = "qs_cc_hide_date";
    /** The firmware's own switch: Settings.System "use_control_panel" (1 = Control Center / split shade, 0 = classic shade). */
    public static final String CC_SETTING = "use_control_panel";

    public static final String[] COLS = {"Stock", "2 columns", "3 columns", "4 columns", "5 columns", "6 columns"};
    public static final int[] COLS_VALUES = {0, 2, 3, 4, 5, 6};
    public static final String[] ROWS = {"Stock", "1 row", "2 rows", "3 rows", "4 rows", "5 rows", "6 rows"};
    public static final int[] ROWS_VALUES = {0, 1, 2, 3, 4, 5, 6};
    public static final String[] TOP_LAYOUTS = {"Stock (4 large tiles in 2 rows)", "2 large tiles in one row", "6 large tiles in three rows", "4 large tiles in a column"};
    public static final String[] LABEL_POS = {"Stock (label under the icon)", "Label above the icon", "Icons only (no labels)"};
    public static final String[] LABEL_LINES = {"Stock", "1 line", "2 lines", "3 lines"};
    /** Index 0 (stock) is already the One UI order: large tiles → brightness → small grid. */
    public static final String[] BRIGHTNESS_POS = {"Between large and small tiles (stock, One UI order)", "Above all tiles", "Below the small tiles"};

    /** Control Center brightness + volume placement (xposed/CcLayoutHooks). Index 2 is the One UI 7/8 arrangement. */
    public static final String[] CC_SLIDER_POS = {"Stock (vertical, next to the big tiles)", "Vertical, on the left", "Horizontal bars under the big tiles (One UI)", "Horizontal bars above the big tiles", "Horizontal bars at the bottom of the panel"};
    public static final String[] CC_LAYOUTS = {"Stock (RedMagic)", "One UI 8"};
    public static final String[] CC_SLIDER_SIZE = {"Default (56 dp)", "Slim (44 dp)", "48 dp", "52 dp", "60 dp", "64 dp", "72 dp"};
    public static final int[] CC_SLIDER_SIZE_DP = {56, 44, 48, 52, 60, 64, 72};
    public static int sliderSizeDp(int choice) { return choice > 0 && choice < CC_SLIDER_SIZE_DP.length ? CC_SLIDER_SIZE_DP[choice] : CC_SLIDER_SIZE_DP[0]; }

    public static int cols(int choice) { return choice > 0 && choice < COLS_VALUES.length ? COLS_VALUES[choice] : 0; }
    public static int rows(int choice) { return choice > 0 && choice < ROWS_VALUES.length ? ROWS_VALUES[choice] : 0; }

    public static final Features.Section SECTION = new Features.Section("qs", "Quick Settings",
        "Tile grid, large-tile layout, labels, brightness slider placement, tile shapes and finishes",
        R.drawable.ic_sysui,
        new String[]{"com.android.systemui"},
        new Features.Feature(QS_COLS, "Small tiles per row (portrait)", "Columns of the small (round) tiles in the pull-down panel. 5 or 6 columns only apply when the tiles still fit at their native size. Applies the next time the panel opens.", Features.Type.CHOICE, COLS, 0).num(2, 6, 1, "columns"),
        new Features.Feature(QS_ROWS, "Maximum rows per page (portrait)", "Rows of small tiles per page. Extra rows only appear when they fit the screen; the expansion animation is extended to match. Applies the next time the panel opens.", Features.Type.CHOICE, ROWS, 0).num(1, 6, 1, "rows"),
        new Features.Feature(QS_LAND_COLS, "Small tiles per row (landscape)", "Columns used when the phone is held sideways, independent of the portrait setting.", Features.Type.CHOICE, COLS, 0).num(2, 6, 1, "columns"),
        new Features.Feature(QS_LAND_ROWS, "Maximum rows per page (landscape)", "Rows per page when the phone is held sideways.", Features.Type.CHOICE, ROWS, 0).num(1, 6, 1, "rows"),
        new Features.Feature(QS_TOP_LAYOUT, "Large tile arrangement", "How many wide tiles sit above the brightness slider and how they are arranged. The remaining tiles move to the small grid; nothing is removed. Portrait only. Applies live; the compact shade height follows.", Features.Type.CHOICE, TOP_LAYOUTS, 0),
        new Features.Feature(QS_LABEL_POS, "Small tile labels", "Where the text under each small tile goes, or hide it and keep icons only. Portrait panel; also applies to the small tiles of the Control Center.", Features.Type.CHOICE, LABEL_POS, 0),
        new Features.Feature(QS_LABEL_LINES, "Label line limit", "Maximum lines for small tile labels (longer names are cut with …). Portrait panel and Control Center.", Features.Type.CHOICE, LABEL_LINES, 0),
        new Features.Feature(QS_BRIGHTNESS_POS, "Brightness slider placement", "Moves the brightness slider above the large tiles or below the small-tile grid. Portrait panel only.", Features.Type.CHOICE, BRIGHTNESS_POS, 0),
        new Features.Feature(QS_BRIGHTNESS_NO_SUN, "Hide the sun icons next to the brightness slider", "Removes the small and large sun icons at both ends of the slider; auto-brightness stays."),
        new Features.Feature(QS_SHAPE_SMALL, "Small tile shape", "Corner shape of the small (round) tiles. Stock colours stay.", Features.Type.CHOICE, QsArt.SHAPES, 0),
        new Features.Feature(QS_SHAPE_LARGE, "Large tile shape", "Corner shape of the wide tiles (Wi-Fi, data…). \"One UI pill\" is the fully rounded Samsung pill; combine it with the One UI tile finish for the filled blue look.", Features.Type.CHOICE, QsArt.SHAPES, 0),
        new Features.Feature(QS_TILE_STYLE, "Tile finish", "Decorative finish drawn over the tile background: outline, bevel, gradients… Stock colours stay, except \"One UI\", which recolours the tiles like Samsung One UI 7/8: blue filled when on, dark grey when off, white / light-grey icons and labels (follows light and dark mode); and \"One UI 8 (white on)\", the frosted One UI 8 panel: round tiles white with a dark icon when on, translucent when off, large pills translucent with the icon in a circle. Applies live.", Features.Type.CHOICE, QsArt.TILE_STYLES, 0),
        new Features.Feature(QS_TILE_COLOR, "Colour of tiles that are on", "Colours every tile that is switched on, in the pull-down panel and the Control Center: your wallpaper colour, plain colours or a gradient. The icon (and the name inside large tiles) turns dark or light to stay readable. Works with every tile finish, also One UI and One UI 8. Applies live.", Features.Type.CHOICE, QsArt.PANEL_COLORS, 0),
        new Features.Feature(QS_BRIGHTNESS_STYLE, "Brightness slider style", "Outline, bevel, thin-line or One UI look for the brightness slider in Quick Settings and the Control Center. \"One UI\" paints the thick track in Samsung colours with a small sun inside its start; \"One UI 8\" also rounds the fill and keeps it at least a circle around the icon, like Samsung's quick panel. Pair either with \"Hide the sun icons next to the brightness slider\" for the full One UI look.", Features.Type.CHOICE, QsArt.BRIGHTNESS_STYLES, 0),
        new Features.Feature(QS_SLIDER_COLOR, "Brightness & volume bar colour", "Colour of the filled part of the brightness bar in the pull-down panel and of the brightness and volume bars in the Control Center: your wallpaper colour, plain colours or a gradient. Works with every slider style. Applies live.", Features.Type.CHOICE, QsArt.PANEL_COLORS, 0),
        new Features.Feature(QS_CC_ENABLE, "Split shade (Control Center)", "Forces the firmware's two-page shade: notifications on one page, the Control Center (brightness, volume, media and tiles) on the other, like One UI 8. Same as the system setting \"use_control_panel\"; switching takes effect at once (the panel window is rebuilt)."),
        new Features.Feature(QS_CC_COLS, "Control Center tiles per row", "Columns of the small round tiles in the Control Center page (stock 4). 5 or 6 only apply when the tiles still fit. Applies the next time the panel opens.", Features.Type.CHOICE, COLS, 0).num(2, 6, 1, "columns"),
        new Features.Feature(QS_CC_TILE_GAP, "Control Center: space between round tiles", "Makes the round tiles slightly smaller inside their cells so there is clear space between them (like One UI), and adds a little space between the rows. Labels keep their size. Applies the next time the panel opens; if it does not, Restart System UI.", Features.Type.CHOICE, CC_TILE_GAPS, 0).num(0, 24, 1, "dp"),
        new Features.Feature(QS_CC_VOLUME_STYLE, "Control Center volume slider style", "Look of the volume slider in the Control Center. \"One UI 8\" is the Samsung bar: grey rounded track, light rounded fill that starts as a circle around the speaker icon (the icon turns dark so it stays readable). \"One UI\" matches the older One UI brightness style.", Features.Type.CHOICE, QsArt.BRIGHTNESS_STYLES, 0),
        new Features.Feature(QS_CC_SLIDER_POS, "Control Center: brightness & volume position", "Where the brightness and volume bars sit on the Control Center page: stock vertical bars next to the big tiles, vertical on the left, or full-width horizontal bars under / above the big tiles or at the bottom under the small tiles. Pick a \"One UI 8\" slider style for the Samsung look. Portrait only (landscape keeps the stock layout). Applies the next time the panel opens; if it does not, Restart System UI.", Features.Type.CHOICE, CC_SLIDER_POS, 0),
        new Features.Feature(QS_CC_SLIDER_SIZE, "Control Center: horizontal bar thickness", "Height of the brightness and volume bars when a horizontal position is chosen above. Applies the next time the panel opens; if it does not, Restart System UI.", Features.Type.CHOICE, CC_SLIDER_SIZE, 0).num(32, 96, 2, "dp"),
        new Features.Feature(QS_CC_LAYOUT, "Control Center layout", "\"One UI 8\" rebuilds the Control Center page like Samsung's quick panel: small clock with Edit / Power / Settings buttons, Wi-Fi and Bluetooth as two pills (the other big tiles join the round tiles), the round tiles in a frosted card, brightness and volume as One UI 8 bars with a Dark mode and a sound-mode button, a media card (Play music + Media output) and four shortcut pills (Device Center, Screen cast, Modes, Share). Portrait only; landscape keeps the stock page. Pair it with the \"One UI 8 (white on)\" tile finish and the One UI 8 slider styles, or use the One UI preset. Restart System UI.", Features.Type.CHOICE, CC_LAYOUTS, 0),
        new Features.Feature(QS_CC_ONEUI_BIG_CLOCK, "One UI 8 layout: keep the big clock and date", "Keeps RedMagic's large clock and the date at the top of the Control Center. Off = a small clock in the status-icon row, like One UI. Restart System UI."),
        new Features.Feature(QS_CC_ONEUI_NO_HEADER, "One UI 8 layout: stock header buttons", "Leaves the header buttons as RedMagic has them (gear and ⋮ menu) instead of One UI's Edit, Power and Settings. Restart System UI."),
        new Features.Feature(QS_CC_ONEUI_NO_MEDIA, "One UI 8 layout: hide the media card", "Removes the \"Play music / Media output\" card. RedMagic's own media player (⋮ → show media player) then stays at the top. Restart System UI."),
        new Features.Feature(QS_CC_ONEUI_NO_SHORTCUTS, "One UI 8 layout: hide the shortcut pills", "Removes the Device Center / Screen cast / Modes / Share pills at the bottom. Restart System UI."),
        new Features.Feature(QS_CC_HIDE_SEARCH, "Control Center: hide the search button", "Removes the search button from the Control Center / notification header."),
        new Features.Feature(QS_CC_HIDE_EDIT, "Control Center: hide the edit button", "Removes the tile-edit (pencil / menu) button from the Control Center header."),
        new Features.Feature(QS_CC_HIDE_USER, "Control Center: hide the user avatar", "Removes the multi-user avatar from the Control Center header."),
        new Features.Feature(QS_CC_HIDE_POWER, "Control Center: hide the power button", "Removes the power-menu button from the Control Center header."),
        new Features.Feature(QS_CC_HIDE_SETTINGS, "Control Center: hide the settings button", "Removes the Settings gear from the Control Center header."),
        new Features.Feature(QS_CC_HIDE_DATE, "Control Center: hide the date", "Removes the date line under the clock in the Control Center header.")
    );

    static {
        Previews.register(QS_COLS, (ctx, i) -> new Sketch(ctx, 120, 96, (c, w, h, d) -> QsArt.paintGridPreview(c, w, h, cols(i), 0, 0, 0, 0, false, accent(ctx), dim(ctx))), "Schematic of the pull-down panel");
        Previews.register(QS_ROWS, (ctx, i) -> new Sketch(ctx, 120, 96, (c, w, h, d) -> QsArt.paintGridPreview(c, w, h, 0, rows(i), 0, 0, 0, false, accent(ctx), dim(ctx))), "Maximum rows; fewer are shown when they do not fit");
        Previews.register(QS_LAND_COLS, (ctx, i) -> new Sketch(ctx, 120, 96, (c, w, h, d) -> QsArt.paintGridPreview(c, w, h, cols(i), 0, 0, 0, 0, true, accent(ctx), dim(ctx))));
        Previews.register(QS_LAND_ROWS, (ctx, i) -> new Sketch(ctx, 120, 96, (c, w, h, d) -> QsArt.paintGridPreview(c, w, h, 0, rows(i), 0, 0, 0, true, accent(ctx), dim(ctx))));
        Previews.register(QS_TOP_LAYOUT, (ctx, i) -> new Sketch(ctx, 120, 96, (c, w, h, d) -> QsArt.paintGridPreview(c, w, h, 0, 0, i, 0, 0, false, accent(ctx), dim(ctx))));
        Previews.register(QS_LABEL_POS, (ctx, i) -> new Sketch(ctx, 120, 96, (c, w, h, d) -> QsArt.paintGridPreview(c, w, h, 0, 0, 0, 0, i, false, accent(ctx), dim(ctx))));
        Previews.register(QS_BRIGHTNESS_POS, (ctx, i) -> new Sketch(ctx, 120, 96, (c, w, h, d) -> QsArt.paintGridPreview(c, w, h, 0, 0, 0, i, 0, false, accent(ctx), dim(ctx))));
        Previews.register(QS_SHAPE_SMALL, (ctx, i) -> i <= 0 ? null : new Sketch(ctx, 96, 96, (c, w, h, d) -> QsArt.paintShapePreview(c, QsArt.shapeId(i), w, h, false, ClockFeatures.dp(ctx, 10))));
        Previews.register(QS_SHAPE_LARGE, (ctx, i) -> i <= 0 ? null : new Sketch(ctx, 120, 64, (c, w, h, d) -> QsArt.paintShapePreview(c, QsArt.shapeId(i), w, h, true, ClockFeatures.dp(ctx, 8))));
        Previews.register(QS_TILE_STYLE, (ctx, i) -> new Sketch(ctx, 120, 96, (c, w, h, d) -> QsArt.paintTilePreview(c, i, w, h, d, ClockFeatures.dp(ctx, 6))), "Finish over a light and a dark tile");
        Previews.register(QS_BRIGHTNESS_STYLE, (ctx, i) -> new Sketch(ctx, 120, 64, (c, w, h, d) -> QsArt.paintBrightnessPreview(c, i, w, h, d, ClockFeatures.dp(ctx, 4))));
        Previews.register(QS_CC_COLS, (ctx, i) -> new Sketch(ctx, 120, 96, (c, w, h, d) -> QsArt.paintGridPreview(c, w, h, cols(i), 0, 1, 0, 2, false, accent(ctx), dim(ctx))), "Schematic of the Control Center page");
        Previews.register(QS_CC_TILE_GAP, (ctx, i) -> new Sketch(ctx, 120, 96, (c, w, h, d) -> paintGapPreview(c, w, h, d, ccGap(i))), "Round tiles of the Control Center, 5 per row");
        Previews.register(QS_CC_SLIDER_POS, (ctx, i) -> new Sketch(ctx, 120, 120, (c, w, h, d) -> QsArt.paintCcSliderPreview(c, w, h, i, QsArt.ONE_UI_BLUE)), "Schematic of the top of the Control Center page");
        Previews.register(QS_CC_LAYOUT, (ctx, i) -> new Sketch(ctx, 96, 150, (c, w, h, d) -> QsArt.paintCcLayoutPreview(c, w, h, i)), "Schematic of the Control Center page");
        Previews.register(QS_TILE_COLOR, (ctx, i) -> new Sketch(ctx, 120, 64, (c, w, h, d) -> paintColorPreview(c, w, h, d, QsArt.panelColors(ctx, i, true), true)), "A large and two small tiles that are on");
        Previews.register(QS_SLIDER_COLOR, (ctx, i) -> new Sketch(ctx, 120, 64, (c, w, h, d) -> paintColorPreview(c, w, h, d, QsArt.panelColors(ctx, i, true), false)), "The brightness bar");
        Previews.register(QS_CC_VOLUME_STYLE, (ctx, i) -> new Sketch(ctx, 120, 64, (c, w, h, d) -> QsArt.paintBrightnessPreview(c, i, w, h, d, ClockFeatures.dp(ctx, 4))));
    }

    static int accent(Context ctx) { return 0xffd3cbef; }
    static int dim(Context ctx) { return (ClockFeatures.textColor(ctx) & 0x00ffffff) | 0x66000000; }

    /** Two rows of five round tiles with the chosen circle scale and row spacing. */
    static void paintGapPreview(Canvas c, int w, int h, float d, int gap) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF1E1F22);
        c.drawRoundRect(0, 0, w, h, 12 * d, 12 * d, p);
        int cols = 5, rows = 2;
        float side = 6 * d, cell = (w - 2 * side) / cols;
        float circle = cell * 0.96f * CC_TILE_GAP_SCALE[gap];
        float rowGap = CC_TILE_GAP_ROW_DP[gap] * d * 0.5f, label = 9 * d;
        float rowH = cell * 0.96f + label + rowGap;
        float top = (h - rows * rowH + rowGap) / 2f;
        for (int r = 0; r < rows; r++) for (int k = 0; k < cols; k++) {
            float cx = side + k * cell + cell / 2f, cy = top + r * rowH + cell * 0.48f;
            p.setColor((r * cols + k) % 4 == 2 ? 0xFF3D7BF7 : 0xFF3A3B3F);
            c.drawCircle(cx, cy, circle / 2f, p);
            p.setColor(0x99FFFFFF);
            c.drawRect(cx - cell * 0.28f, cy + cell * 0.48f + 3 * d, cx + cell * 0.28f, cy + cell * 0.48f + 5 * d, p);
        }
    }

    /** Tiles that are on (tiles = true) or a brightness bar in the chosen colours; null = RedMagic's stock look. */
    static void paintColorPreview(Canvas c, int w, int h, float d, int[] colors, boolean tiles) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF1E1F22);
        c.drawRoundRect(0, 0, w, h, 12 * d, 12 * d, p);
        int[] col = colors != null ? colors : new int[]{0xFFE9E9EE, 0xFFE9E9EE};
        int icon = QsArt.onColor(col[0]);
        if (tiles) {
            android.graphics.RectF pill = new android.graphics.RectF(8 * d, 8 * d, w * 0.62f, h * 0.5f);
            QsArt.fillPaint(p, pill, col);
            c.drawRoundRect(pill, pill.height() / 2, pill.height() / 2, p);
            p.setShader(null); p.setColor(icon);
            c.drawCircle(pill.left + pill.height() / 2, pill.centerY(), pill.height() * 0.18f, p);
            c.drawRect(pill.left + pill.height() * 0.9f, pill.centerY() - 2 * d, pill.right - 10 * d, pill.centerY() + 2 * d, p);
            float r = h * 0.17f, cy = h * 0.76f;
            for (int k = 0; k < 3; k++) {
                float cx = 18 * d + k * (r * 2 + 10 * d);
                android.graphics.RectF b = new android.graphics.RectF(cx - r, cy - r, cx + r, cy + r);
                if (k == 1) { p.setShader(null); p.setColor(0xFF3A3B3F); } else QsArt.fillPaint(p, b, col);
                c.drawCircle(cx, cy, r, p);
                p.setShader(null); p.setColor(k == 1 ? 0xFFFFFFFF : icon);
                c.drawCircle(cx, cy, r * 0.32f, p);
            }
        } else {
            android.graphics.RectF track = new android.graphics.RectF(10 * d, h * 0.3f, w - 10 * d, h * 0.7f);
            p.setColor(0xFF3A3A3F);
            c.drawRoundRect(track, track.height() / 2, track.height() / 2, p);
            android.graphics.RectF fill = new android.graphics.RectF(track.left, track.top, track.left + track.width() * 0.62f, track.bottom);
            QsArt.fillPaint(p, track, col);
            c.drawRoundRect(fill, track.height() / 2, track.height() / 2, p);
            p.setShader(null); p.setColor(icon);
            c.drawCircle(track.left + track.height() / 2, track.centerY(), track.height() * 0.16f, p);
        }
    }

    /** A fixed-size drawable that delegates to a painter; shared by the QS and Surface previews. */
    public interface Painter { void paint(Canvas c, int w, int h, float density); }

    public static final class Sketch extends Drawable {
        private final int w, h; private final float density; private final Painter painter;
        public Sketch(Context ctx, int wDp, int hDp, Painter painter) {
            w = ClockFeatures.dp(ctx, wDp); h = ClockFeatures.dp(ctx, hDp); density = ctx.getResources().getDisplayMetrics().density; this.painter = painter;
        }
        @Override public int getIntrinsicWidth() { return w; }
        @Override public int getIntrinsicHeight() { return h; }
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            int save = c.save();
            try { c.translate(b.left, b.top); c.clipRect(0, 0, b.width(), b.height()); painter.paint(c, b.width(), b.height(), density); } catch (Throwable ignored) { } finally { c.restoreToCount(save); }
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
