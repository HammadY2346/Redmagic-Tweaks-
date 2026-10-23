package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.SharedPreferences;

import com.hammad.redmagictweaks.xposed.QsArt;
import com.hammad.redmagictweaks.xposed.SurfaceArt;

/**
 * Ready-made combinations of existing settings. Each preset is {key, value}: an Integer for a CHOICE feature (the index
 * into its choices[]) or a Boolean for a SWITCH. Indexes are looked up by label at class-load time so a reordered
 * choices[] cannot silently select the wrong picture; the expected index is noted in the comment of every entry.
 */
public final class Presets {
    private Presets() {}

    /** Index of {@code label} in {@code choices}, 0 (stock) when the label is missing. */
    static int idx(String[] choices, String label) {
        for (int i = 0; i < choices.length; i++) if (choices[i].equals(label)) return i;
        return 0;
    }

    /**
     * Samsung One UI 8 look, Control Center as set up on the phone (Oct 2026): RedMagic's big clock and date, Wi-Fi / sound
     * mode / Bluetooth / Auto-rotate as four blue One UI pills in two rows with the brightness and volume bars standing
     * next to them (One UI 8 rounded fill), round blue / grey tiles with their names in four columns. Plus opaque rounded
     * notification cards, One UI 8 status icons, the One UI 8 battery (number inside), a regular-weight clock without
     * seconds and the One UI 8 lock screen.
     */
    public static final Object[][] ONE_UI = {
        // ---- Quick Settings (QsFeatures / xposed/QsHooks) — all live, no restart
        {QsFeatures.QS_TOP_LAYOUT, 0},                                                               // 0: four pills in two rows (2 x 2)
        {QsFeatures.QS_BRIGHTNESS_POS, 0},                                                           // 0: stock order is already large → brightness → small grid
        {QsFeatures.QS_BRIGHTNESS_NO_SUN, Boolean.TRUE},                                             // no sun icons at the slider ends
        {QsFeatures.QS_BRIGHTNESS_STYLE, QsArt.ONE_UI8_BRIGHTNESS},                                  // 5: One UI 8 bar — rounded track + rounded fill, sun inside its start
        {QsFeatures.QS_LABEL_POS, 0},                                                                // 0: name under each round tile
        {QsFeatures.QS_LABEL_LINES, 0},                                                              // 0: stock (two-line names like "Ultra PowerSaver" wrap)
        {QsFeatures.QS_COLS, 0},                                                                     // 0: four round tiles per row
        {QsFeatures.QS_ROWS, 0},                                                                     // 0: stock row count
        {QsFeatures.QS_SHAPE_SMALL, idx(QsArt.SHAPES, "Pill")},                                      // 4: pill on a square icon background = perfect circle
        {QsFeatures.QS_SHAPE_LARGE, idx(QsArt.SHAPES, "One UI pill")},                               // 12: fully rounded large pills
        {QsFeatures.QS_TILE_STYLE, QsArt.ONE_UI_TILE},                                               // 8: One UI colours: blue when on, dark grey when off, white icons
        // ---- Split shade / Control Center (QsFeatures.QS_CC_*, xposed/QsHooks) — switch is live (panel window rebuilt), the rest on the next open
        {QsFeatures.QS_CC_ENABLE, Boolean.TRUE},                                                     // notifications page + Control Center page, like One UI 8 (Settings.System use_control_panel = 1)
        {QsFeatures.QS_CC_COLS, 0},                                                                  // 0: four round tiles per row in the Control Center
        {QsFeatures.QS_CC_TILE_GAP, 1},                                                              // small gap between the round tiles
        {QsFeatures.QS_CC_VOLUME_STYLE, QsArt.ONE_UI8_BRIGHTNESS},                                   // 5: Control Center volume bar in the same One UI 8 look (dark speaker icon in the fill)
        {QsFeatures.QS_CC_SLIDER_POS, 0},                                                            // 0: brightness + volume as two vertical bars next to the pills
        {QsFeatures.QS_CC_SLIDER_SIZE, 0},                                                           // 0: 56 dp bars
        {QsFeatures.QS_CC_LAYOUT, 0},                                                                // 0: RedMagic page (big clock + date, gear / ⋮) restyled by the settings above
        {QsFeatures.QS_CC_ONEUI_BIG_CLOCK, Boolean.FALSE},                                           // small clock in the status-icon row
        {QsFeatures.QS_CC_ONEUI_NO_HEADER, Boolean.FALSE},                                           // One UI Edit / Power / Settings buttons
        {QsFeatures.QS_CC_ONEUI_NO_MEDIA, Boolean.FALSE},                                            // media card shown
        {QsFeatures.QS_CC_ONEUI_NO_SHORTCUTS, Boolean.FALSE},                                        // shortcut pills shown
        {QsFeatures.QS_CC_HIDE_SEARCH, Boolean.TRUE},                                                // One UI has no search button in the panel header
        {QsFeatures.QS_CC_HIDE_EDIT, Boolean.FALSE},                                                 // keep the tile editor reachable
        {QsFeatures.QS_CC_HIDE_USER, Boolean.FALSE},
        {QsFeatures.QS_CC_HIDE_POWER, Boolean.FALSE},                                                // One UI 7/8 keeps the power button in the header
        {QsFeatures.QS_CC_HIDE_SETTINGS, Boolean.FALSE},                                             // and the Settings gear
        {QsFeatures.QS_CC_HIDE_DATE, Boolean.FALSE},                                                 // and the date
        // ---- Notifications (SurfaceFeatures / xposed/SurfaceHooks) — live
        {SurfaceFeatures.NOTIF_STYLE, SurfaceArt.ONE_UI_CARD},                                       // 6: opaque dark card with a hairline edge
        {SurfaceFeatures.NOTIF_CORNERS, SurfaceArt.ONE_UI_CORNER},                                   // 4: 26 dp corners
        {SurfaceFeatures.NOTIF_OPACITY, 0},                                                          // 0: One UI cards are opaque
        // ---- Volume pop-up and power menu (SurfaceFeatures / xposed/SurfaceHooks) — next time they open
        {SurfaceFeatures.VOL_ONEUI, Boolean.TRUE},                                                   // full-pill sliders, light fill, icon inside
        {SurfaceFeatures.PWR_ONEUI, Boolean.TRUE},                                                   // blurred background, red / green round buttons
        // ---- Font (CosmeticsFeatures / xposed/FontHooks) — System UI restart
        {CosmeticsFeatures.COS_SYSUI_FONT, CosmeticsFeatures.SYSUI_FONT_ONE_UI},                     // Inter, close to One UI Sans
        // ---- Status bar icons (IconPackFeatures / xposed/IconPackHooks) — next redraw (pull the shade once)
        {IconPackFeatures.SBI_PACK, idx(IconPackFeatures.PACKS, "One UI 8")},                        // 17: our One UI 8 glyphs for status + tile icons (Wi-Fi / signal incl. no-internet, roaming)
        {IconPackFeatures.SBI_WIFI, 0},                                                              // 0: the pack supplies Wi-Fi; a separate Wi-Fi style would override it
        {IconPackFeatures.SBI_SIGNAL, 0},                                                            // 0: same for the signal bars
        // ---- Battery (CosmeticsFeatures / xposed/CosmeticsHooks) — next battery update (~1 min) or System UI restart
        {CosmeticsFeatures.COS_BATTERY_STYLE, idx(CosmeticsFeatures.BATTERY_STYLES, "One UI 8")},    // 25: One UI 8 pill, number always inside (readable on light and dark)
        {CosmeticsFeatures.COS_BATTERY_PERCENT, Boolean.FALSE},                                      // not needed: the One UI 8 shape always writes the number
        // ---- Status bar clock (ClockFeatures / xposed/ClockHooks, StatusBarFeatures) — next clock tick (≤ 1 min); seconds need a System UI restart
        {ClockFeatures.CLK_FONT, idx(ClockFeatures.FONTS, "Sans")},                                  // 1: regular sans weight (One UI clock is not bold)
        {ClockFeatures.CLK_BOLD, Boolean.FALSE},
        {ClockFeatures.CLK_ITALIC, Boolean.FALSE},
        {ClockFeatures.CLK_DATE, 0},                                                                 // 0: time only; the clock already sits on the left on this firmware
        {StatusBarFeatures.SB_CLOCK_SECONDS, Boolean.FALSE},                                         // no seconds
        {StatusBarFeatures.SB_CLOCK_FORMAT, 0},                                                      // 0: stock pattern
        // ---- Lock screen (LockFeatures / xposed/OneUiLockHooks) — System UI restart
        {LockFeatures.ONEUI_CLOCK, Boolean.TRUE},                                                    // stacked hours / minutes + date, upper third
        {LockFeatures.ONEUI_FACE, 0},                                                                // One UI 8 stacked face
        {LockFeatures.ONEUI_SIZE, 0},                                                                // big
        {LockFeatures.ONEUI_TILES, Boolean.TRUE},                                                    // battery ring / date / alarm tiles
        {LockFeatures.ONEUI_SHORTCUTS, Boolean.TRUE},                                                // phone + camera round buttons
        {LockFeatures.ONEUI_POSITION, 0},
    };

    /** The same keys back to stock (0 / false). */
    public static final Object[][] STOCK = stockOf(ONE_UI);

    private static Object[][] stockOf(Object[][] preset) {
        Object[][] out = new Object[preset.length][];
        for (int i = 0; i < preset.length; i++) out[i] = new Object[]{preset[i][0], preset[i][1] instanceof Boolean ? Boolean.FALSE : (Object) 0};
        return out;
    }

    /** Writes every entry of a preset in one commit (the hooks pick the change up within a second). */
    /** One UI 8 style app drawer (launcher). Restart the launcher after applying. */
    public static final Object[][] ONE_UI_LAUNCHER = {
        {Features.LAUNCHER_PAGED, Boolean.TRUE}, {Features.LAUNCHER_PAGED_ROWS, 0}, {Features.LAUNCHER_CUSTOM_ORDER, Boolean.TRUE},
        {com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_FIXED_SLOTS, Boolean.TRUE}, {Features.LAUNCHER_DRAWER_DOTS, Boolean.TRUE},
        {com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_PAGE_PAD, 0}, {Features.LAUNCHER_NO_FASTSCROLL, Boolean.TRUE},
        {com.hammad.redmagictweaks.xposed.DrawerArt.KEY_SHAPE, 5}, {com.hammad.redmagictweaks.xposed.DrawerArt.KEY_COLOR, 0}, {com.hammad.redmagictweaks.xposed.DrawerArt.KEY_OPACITY, 0},
        {com.hammad.redmagictweaks.xposed.DrawerArt.KEY_GRID, 0}, {com.hammad.redmagictweaks.xposed.DrawerArt.KEY_LABEL, 0}, {com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_BG, 2},
        {com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_RADIUS, 2}, {com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_COLS, 0}, {com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_NO_NAMES, Boolean.FALSE},
        {com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_HIDE_SEARCH, Boolean.FALSE}, {com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_HIDE_MENU, Boolean.FALSE}, {com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_LABEL_SIZE, 1},
        {com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_SEARCH_POS, 1}, {com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_FOLDER_OPEN, 1},
    };
    public static final Object[][] STOCK_LAUNCHER = stockOf(ONE_UI_LAUNCHER);
    public static final String PRESET_ONE_UI_LAUNCHER = "preset_one_ui_launcher";
    public static final String PRESET_STOCK_LAUNCHER = "preset_stock_launcher";
    public static final String PRESET_ONE_UI = "preset_one_ui";
    public static final String PRESET_STOCK = "preset_stock_look";
    public static final Features.Section SECTION = new Features.Section("presets", "Looks (presets)",
        "One tap sets a whole group of tweaks across Quick Settings, status bar, notifications and battery",
        R.drawable.ic_theme, new String[]{"com.android.systemui", "com.zte.mifavor.launcher"},
        new Features.Feature(PRESET_ONE_UI, "One UI 8 look", "Control Center: big clock and date, Wi-Fi / Sound mode / Bluetooth / Auto-rotate as blue One UI pills in two rows with the brightness and volume bars standing next to them (One UI 8 rounded fill), round blue / grey tiles with their names, four per row. Plus opaque rounded notification cards, One UI 8 status icons and battery (number inside), a regular clock without seconds, the One UI 8 volume panel and power menu, and the One UI 8 lock screen (stacked clock, lock icon, widget tiles, round phone / camera buttons). The preview above shows the result before you apply it. You can fine-tune every piece afterwards in its own section. Restart System UI.", Features.Type.ACTION, null, 0),
        new Features.Feature(PRESET_ONE_UI_LAUNCHER, "One UI 8 app drawer", "Horizontal paged drawer with fixed slots and page dots (stock page gap), search bar with ⋮ at the bottom under the dots (⋮ opens Sort / Edit / Hidden apps), custom layout with folders (One UI folder shape, full-screen One UI folder view), small labels. Restart the launcher afterwards.", Features.Type.ACTION, null, 0),
        new Features.Feature(PRESET_STOCK_LAUNCHER, "Back to the stock drawer", "Resets every launcher setting the One UI drawer preset touched. Restart the launcher.", Features.Type.ACTION, null, 0),
        new Features.Feature(PRESET_STOCK, "Back to the stock look", "Resets every setting the One UI preset touched to stock. Other tweaks are left alone. Restart System UI.", Features.Type.ACTION, null, 0)
    );

    public static void apply(Context c, Object[][] preset) {
        SharedPreferences.Editor e = Prefs.get(c).edit();
        for (Object[] entry : preset) {
            String key = (String) entry[0];
            if (entry[1] instanceof Boolean) e.putBoolean(key, (Boolean) entry[1]);
            else {
                e.putInt(key, ((Number) entry[1]).intValue());
                e.putBoolean(key + Features.CUSTOM, false);        // the preset's value, not a typed value
                e.putBoolean(key + ColorPick.ON, false);           // nor a custom colour
            }
        }
        e.commit();
    }
}
