package com.hammad.redmagictweaks;

/**
 * Lock screen & Always-on display tweaks (hooked in com.android.systemui by xposed/LockHooks). The clock / date
 * appearance and widget switches are nubia-ly's lock-screen editor (SurfaceBackend); the lock clock font lives in
 * CosmeticsFeatures.
 */
public final class LockFeatures {
    private LockFeatures() {}

    public static final String LOCK_HIDE_STATUSBAR = "lock_hide_statusbar";
    public static final String LOCK_KEEP_NOTIFS = "lock_keep_notifs";
    public static final String LOCK_DT_SLEEP = "lock_double_tap_sleep";
    public static final String LOCK_DT_STATUSBAR = "lock_double_tap_statusbar";
    public static final String LOCK_HIDE_UNLOCK_HINT = "lock_hide_unlock_hint";
    public static final String LOCK_CHARGE_INFO = "lock_charge_info";
    public static final String LOCK_CHARGE_INTERVAL = "lock_charge_interval";
    public static final String AOD_SECONDS = "lock_aod_seconds";
    public static final String AOD_DATETIME = "lock_aod_datetime";
    // nubia-ly lock-screen editor (SurfaceBackend groups 2 = clock, 3 = date, 4..6 = widgets)
    public static final String CLOCK_HIDE = "lock_clock_hide";
    public static final String CLOCK_SIZE = "lock_clock_size";
    public static final String CLOCK_OPACITY = "lock_clock_opacity";
    public static final String CLOCK_DX = "lock_clock_dx";
    public static final String CLOCK_DY = "lock_clock_dy";
    public static final String CLOCK_ROT = "lock_clock_rot";
    public static final String DATE_HIDE = "lock_date_hide";
    public static final String DATE_SIZE = "lock_date_size";
    public static final String DATE_OPACITY = "lock_date_opacity";
    public static final String DATE_DX = "lock_date_dx";
    public static final String DATE_DY = "lock_date_dy";
    public static final String DATE_ROT = "lock_date_rot";
    public static final String HIDE_WEATHER = "lock_hide_weather_widget";
    public static final String HIDE_ALARM = "lock_hide_alarm_widget";
    public static final String HIDE_BATTERY = "lock_hide_battery_widget";
    // One UI 8 lock screen (xposed/OneUiLockHooks)
    public static final String ONEUI_CLOCK = "lock_oneui_clock";
    public static final String ONEUI_LOCK_ICON = "lock_oneui_lock_icon";
    public static final String ONEUI_TILES = "lock_oneui_tiles";
    public static final String ONEUI_SHORTCUTS = "lock_oneui_shortcuts";
    public static final String ONEUI_POSITION = "lock_oneui_position";
    public static final String[] ONEUI_POSITIONS = {"One UI (upper third)", "Higher", "Lower", "Where the stock clock is"};
    public static final String ONEUI_FACE = "lock_oneui_face";
    public static final String ONEUI_SIZE = "lock_oneui_size";
    public static final String[] ONEUI_SIZES = {"Big", "Bigger", "Biggest", "One UI 8 size"};
    /** Main digit size in dp for each ONEUI_SIZES entry (index 0 = default). */
    public static final int[] ONEUI_SIZE_DP = {108, 124, 140, 88};

    /** nubia-ly ranges: size 80..120 % (step 5), opacity 25..100 % (step 5), offsets -8..8 dp (step 2), rotation -20..20 deg (step 5). */
    public static final String[] SCALES = {"Stock (100 %)", "80 %", "85 %", "90 %", "95 %", "105 %", "110 %", "115 %", "120 %"};
    public static final int[] SCALE_PERCENT = {100, 80, 85, 90, 95, 105, 110, 115, 120};
    public static final String[] OPACITIES = {"Stock (100 %)", "90 %", "80 %", "70 %", "60 %", "50 %", "40 %", "30 %", "25 %"};
    public static final int[] OPACITY_PERCENT = {100, 90, 80, 70, 60, 50, 40, 30, 25};
    public static final String[] OFFSETS_X = {"Stock", "2 dp left", "4 dp left", "6 dp left", "8 dp left", "2 dp right", "4 dp right", "6 dp right", "8 dp right"};
    public static final String[] OFFSETS_Y = {"Stock", "2 dp up", "4 dp up", "6 dp up", "8 dp up", "2 dp down", "4 dp down", "6 dp down", "8 dp down"};
    public static final int[] OFFSET_DP = {0, -2, -4, -6, -8, 2, 4, 6, 8};
    public static final String[] ROTATIONS = {"Stock", "5° left", "10° left", "15° left", "20° left", "5° right", "10° right", "15° right", "20° right"};
    public static final int[] ROTATION_DEG = {0, -5, -10, -15, -20, 5, 10, 15, 20};

    public static final String[] CHARGE_INFO = {
        "Off",
        "Battery temperature",
        "Current · voltage · power",
        "Temperature + current · voltage · power",
    };
    /** Refresh interval labels and the matching seconds (index 0 = default). */
    public static final String[] INTERVALS = {"1 second", "2 seconds", "3 seconds", "5 seconds", "10 seconds"};
    public static final int[] INTERVAL_SECONDS = {1, 2, 3, 5, 10};

    public static final Features.Section SECTION = new Features.Section("lockscreen", "Lock screen & AOD",
        "Status bar, gestures, charging readout, clock & date appearance, widgets and Always-on display",
        R.drawable.ic_fingerprint,
        new String[]{"com.android.systemui"},
        new Features.Feature(ONEUI_CLOCK, "One UI clock", "The One UI lock screen clock in place of the stock clock style (the AOD keeps its own): the face chosen below, centred in the upper third, notifications below it. Uses the font from Look & feel → Lock screen clock font when one is chosen. Restart System UI."),
        new Features.Feature(ONEUI_FACE, "One UI clock face", "One UI 8's stacked clock, or one of the ClockFace-style faces: text, words, LCD, dot matrix, rings and analog dials. Restart System UI.", Features.Type.CHOICE, ClockFaces.NAMES, 0),
        new Features.Feature(ONEUI_SIZE, "One UI clock size", "How big the clock is drawn. Restart System UI.", Features.Type.CHOICE, ONEUI_SIZES, 0),
        new Features.Feature(ONEUI_TILES, "One UI widget tiles", "Rounded translucent tiles under the One UI clock: battery ring with the level, today's date and the next alarm (\"Off\" when none) — in place of RedMagic's lock screen widgets. Needs the One UI clock. Restart System UI."),
        new Features.Feature(ONEUI_SHORTCUTS, "One UI shortcut buttons", "Phone (left) and camera (right) in round translucent buttons in the bottom corners, the RedMagic slide tip hidden. Swipe them from the corner as before. Restart System UI."),
        new Features.Feature(ONEUI_POSITION, "One UI clock position", "Where the One UI clock sits.", Features.Type.CHOICE, ONEUI_POSITIONS, 0),
        new Features.Feature(LOCK_HIDE_STATUSBAR, "Hide status bar on lock screen", "The carrier name, battery and status icons at the top of the lock screen are hidden. Takes effect the next time the lock screen appears."),
        new Features.Feature(LOCK_KEEP_NOTIFS, "Keep notifications after unlocking", "Notifications you already saw on the lock screen keep showing there until you dismiss them, instead of disappearing after the first unlock."),
        new Features.Feature(LOCK_DT_SLEEP, "Double-tap lock screen to sleep", "Double-tap an empty part of the lock screen to turn the screen off. Taps on notifications, buttons and the PIN pad are ignored."),
        new Features.Feature(LOCK_DT_STATUSBAR, "Double-tap status bar to sleep", "Double-tap the status bar (clock / icons row) anywhere to turn the screen off."),
        new Features.Feature(LOCK_HIDE_UNLOCK_HINT, "Hide the unlock hint text", "Removes the \"swipe up to unlock\" style hint under the lock screen clock."),
        new Features.Feature(LOCK_CHARGE_INFO, "Charging readout on lock screen", "While charging, shows live battery temperature, current, voltage and power (read from the kernel) under the lock screen hint area. Restart System UI after changing.", Features.Type.CHOICE, CHARGE_INFO, 0),
        new Features.Feature(LOCK_CHARGE_INTERVAL, "Charging readout refresh", "How often the lock screen readout is refreshed.", Features.Type.CHOICE, INTERVALS, 0),
        new Features.Feature(AOD_SECONDS, "Seconds on the Always-on display", "The AOD clock also shows seconds and ticks every second (uses a little more power). Restart System UI after changing."),
        new Features.Feature(AOD_DATETIME, "Always show date & time on AOD", "AOD styles that normally hide the date / time line show it anyway. Restart System UI after changing."),
        new Features.Feature(CLOCK_HIDE, "Hide the lock screen clock", "The time of the lock screen clock style is not shown (the date and widgets stay). Lock and unlock once after changing."),
        new Features.Feature(CLOCK_SIZE, "Lock screen clock size", "Scales the time of the lock screen clock around its centre. Lock and unlock once after changing.", Features.Type.CHOICE, SCALES, 0),
        new Features.Feature(CLOCK_OPACITY, "Lock screen clock opacity", "Makes the time partly transparent.", Features.Type.CHOICE, OPACITIES, 0),
        new Features.Feature(CLOCK_DX, "Clock horizontal nudge", "Moves the time left or right by a few dp.", Features.Type.CHOICE, OFFSETS_X, 0),
        new Features.Feature(CLOCK_DY, "Clock vertical nudge", "Moves the time up or down by a few dp.", Features.Type.CHOICE, OFFSETS_Y, 0),
        new Features.Feature(CLOCK_ROT, "Clock rotation", "Tilts the time (text clock styles; dial hands are left alone).", Features.Type.CHOICE, ROTATIONS, 0),
        new Features.Feature(DATE_HIDE, "Hide the lock screen date", "The date line under the lock screen clock is not shown. Lock and unlock once after changing."),
        new Features.Feature(DATE_SIZE, "Lock screen date size", "Scales the date line around its centre.", Features.Type.CHOICE, SCALES, 0),
        new Features.Feature(DATE_OPACITY, "Lock screen date opacity", "Makes the date line partly transparent.", Features.Type.CHOICE, OPACITIES, 0),
        new Features.Feature(DATE_DX, "Date horizontal nudge", "Moves the date left or right by a few dp.", Features.Type.CHOICE, OFFSETS_X, 0),
        new Features.Feature(DATE_DY, "Date vertical nudge", "Moves the date up or down by a few dp.", Features.Type.CHOICE, OFFSETS_Y, 0),
        new Features.Feature(DATE_ROT, "Date rotation", "Tilts the date line.", Features.Type.CHOICE, ROTATIONS, 0),
        new Features.Feature(HIDE_WEATHER, "Hide the weather widget", "Removes the weather widget shown under the lock screen clock (all three layouts). Lock and unlock once after changing."),
        new Features.Feature(HIDE_ALARM, "Hide the alarm widget", "Removes the next-alarm widget shown under the lock screen clock. Lock and unlock once after changing."),
        new Features.Feature(HIDE_BATTERY, "Hide the battery widget", "Removes the battery widget shown under the lock screen clock. Lock and unlock once after changing.")
    );

    static { Previews.register(ONEUI_FACE, ClockFaces::preview, "Shown at 9:18 · notifications go below the clock"); }
}
