package com.hammad.redmagictweaks;

/** Status bar, Quick Settings, notification and System UI dialog tweaks (hooked in com.android.systemui by xposed/StatusBarHooks). */
public final class StatusBarFeatures {
    private StatusBarFeatures() {}

    // sizes (xposed/StatusBarSizeHooks)
    public static final String SB_HEIGHT = "sb_height";
    public static final String SB_SIDE_PAD = "sb_side_padding";
    public static final String SB_NET_SIZE = "sb_net_size";
    // clock
    public static final String SB_CLOCK_SECONDS = "sb_clock_seconds";
    public static final String SB_CLOCK_FORMAT = "sb_clock_format";
    // battery
    public static final String SB_BATTERY_STYLE = "sb_battery_style";
    public static final String SB_BATTERY_INFO = "sb_battery_info";
    public static final String SB_BATTERY_INFO_POS = "sb_battery_info_pos";
    public static final String SB_BATTERY_INTERVAL = "sb_battery_interval";
    // network speed
    public static final String SB_NET_SHOW = "sb_net_show";
    public static final String SB_NET_FAST = "sb_net_fast";
    public static final String SB_NET_STYLE = "sb_net_style";
    // icon hiding
    public static final String SB_HIDE_VPN = "sb_hide_vpn";
    public static final String SB_HIDE_NFC = "sb_hide_nfc";
    public static final String SB_HIDE_ALARM = "sb_hide_alarm";
    public static final String SB_HIDE_VIBRATE = "sb_hide_vibrate";
    public static final String SB_HIDE_HEADSET = "sb_hide_headset";
    public static final String SB_HIDE_HD = "sb_hide_hd";
    public static final String SB_HIDE_MOBILE = "sb_hide_mobile";
    public static final String SB_HIDE_WIFI = "sb_hide_wifi";
    public static final String SB_HIDE_BLUETOOTH = "sb_hide_bluetooth";
    public static final String SB_HIDE_HOTSPOT = "sb_hide_hotspot";
    public static final String SB_HIDE_LOCATION = "sb_hide_location";
    public static final String SB_HIDE_CAST = "sb_hide_cast";
    public static final String SB_HIDE_ROTATE = "sb_hide_rotate";
    public static final String SB_HIDE_DATA_SAVER = "sb_hide_data_saver";
    public static final String SB_HIDE_ETHERNET = "sb_hide_ethernet";
    public static final String SB_HIDE_IME = "sb_hide_ime";
    public static final String SB_HIDE_WORK = "sb_hide_work";
    public static final String SB_HIDE_PRIVACY = "sb_hide_privacy";
    // notifications
    public static final String SB_NOTIF_MAX = "sb_notif_max";
    public static final String SB_NOTIF_NATIVE_ICON = "sb_notif_native_icon";
    // quick settings
    public static final String SB_QS_COLS = "sb_qs_cols";
    public static final String SB_QS_ROWS = "sb_qs_rows";
    public static final String SB_QS_CARRIER = "sb_qs_carrier";
    public static final String SB_QS_SEARCH = "sb_qs_search";
    // dialogs & misc
    public static final String SB_VOL_NO_WARNING = "sb_volume_no_warning";
    public static final String SB_VOL_NO_VIBRATE = "sb_volume_no_vibrate";
    public static final String SB_CLIPBOARD_OVERLAY = "sb_clipboard_overlay";
    public static final String SB_USB_AUTO_ALLOW = "sb_usb_debug_auto_allow";
    public static final String SB_ONEHAND_NO_HINT = "sb_onehand_no_hint";

    public static final String[] HEIGHTS = {"Stock", "85 %", "90 %", "95 %", "105 %", "110 %", "115 %", "120 %", "130 %", "140 %"};
    public static final int[] HEIGHT_PERCENT = {100, 85, 90, 95, 105, 110, 115, 120, 130, 140};
    public static final String[] SIDE_PADS = {"Stock", "4 dp closer to the edges", "2 dp closer to the edges", "+2 dp", "+4 dp", "+6 dp", "+8 dp", "+12 dp", "+16 dp"};
    public static final int[] SIDE_PAD_DP = {0, -4, -2, 2, 4, 6, 8, 12, 16};
    public static final String[] TEXT_SIZES = {"Stock", "80 %", "90 %", "110 %", "120 %", "130 %", "140 %"};
    public static final int[] TEXT_SIZE_PERCENT = {100, 80, 90, 110, 120, 130, 140};
    static int pick(int[] values, int choice) { return choice > 0 && choice < values.length ? values[choice] : values[0]; }
    public static int heightPercent(int choice) { return pick(HEIGHT_PERCENT, choice); }
    public static int sidePadDp(int choice) { return pick(SIDE_PAD_DP, choice); }
    public static int netPercent(int choice) { return pick(TEXT_SIZE_PERCENT, choice); }

    /** Clock patterns: labels and the matching SimpleDateFormat pattern (index 0 = stock). */
    public static final String[] CLOCK_FORMATS = {
        "Stock", "12:34:56", "Mon 12:34", "Mon 12:34:56", "03-21 12:34", "Mon 03-21 12:34", "12:34 pm",
    };
    public static final String[] CLOCK_PATTERNS = {
        "", "HH:mm:ss", "EEE HH:mm", "EEE HH:mm:ss", "MM-dd HH:mm", "EEE MM-dd HH:mm", "h:mm a",
    };
    public static final String[] BATTERY_STYLES = {
        "Stock", "Icon only", "Percent inside the icon", "Percent next to the icon", "Percent only (no icon)", "Hide battery completely",
    };
    public static final String[] BATTERY_INFO = {
        "Off", "Temperature", "Current", "Power", "Temperature + power", "Temperature + current + power",
    };
    public static final String[] BATTERY_INFO_POS = {"Next to the clock (left)", "Before the battery icon (right)"};
    public static final String[] INTERVALS = {"1 second", "2 seconds", "3 seconds", "5 seconds", "10 seconds"};
    public static final int[] INTERVAL_SECONDS = {1, 2, 3, 5, 10};
    public static final String[] NET_STYLES = {"Stock (total speed)", "Upload ↑ / download ↓ in two rows", "Upload / download, slightly larger text"};
    public static final String[] NOTIF_MAX = {"Stock", "5", "6", "8", "10", "As many as fit"};
    public static final int[] NOTIF_MAX_VALUES = {0, 5, 6, 8, 10, Integer.MAX_VALUE};
    public static final String[] QS_COLS = {"Stock", "3", "4", "5", "6"};
    public static final int[] QS_COLS_VALUES = {0, 3, 4, 5, 6};
    public static final String[] QS_ROWS = {"Stock", "2", "3", "4", "5"};
    public static final int[] QS_ROWS_VALUES = {0, 2, 3, 4, 5};

    public static final Features.Section SECTION = new Features.Section("statusbar", "Status bar & notifications",
        "Clock, battery, network speed, status icons, Quick Settings and System UI dialogs",
        R.drawable.ic_sysui,
        new String[]{"com.android.systemui"},
        // ---- sizes: everything that changes how big the status bar looks, in one place (the clock / icon / battery
        // entries share their key with the Status bar clock, Status bar icons and Look & feel sections, so both places stay in sync)
        new Features.Feature(SB_HEIGHT, "Status bar height", "Makes the whole status bar taller or shorter; apps and the lock screen follow. Keep it at 95 % or more if icons get cut. Restart System UI after changing.", Features.Type.CHOICE, HEIGHTS, 0),
        new Features.Feature(ClockFeatures.CLK_SIZE, "Clock size", "Size of the status bar clock (same setting as in \"Status bar clock\"). Applies at the next clock tick.", Features.Type.CHOICE, ClockFeatures.SIZES, 0),
        new Features.Feature(IconPackFeatures.SBI_SCALE, "Status icon size", "Wi-Fi, signal, Bluetooth and the other system icons on the right (same setting as in \"Status bar icons\"). Applies within a minute.", Features.Type.CHOICE, IconPackFeatures.SCALES, 0),
        new Features.Feature(CosmeticsFeatures.COS_BATTERY_SCALE, "Battery size", "The whole battery icon with its percentage (same setting as in \"Look & feel\"). Applies within a minute.", Features.Type.CHOICE, IconPackFeatures.SCALES, 0),
        new Features.Feature(CosmeticsFeatures.COS_BATTERY_STYLE, "Battery shape", CosmeticsFeatures.STYLE_DESC + " Same setting as in \"Look & feel\".", Features.Type.CHOICE, CosmeticsFeatures.BATTERY_STYLES, 0),
        new Features.Feature(CosmeticsFeatures.COS_BATTERY_STOCK_READABLE, "Readable number in the stock battery", CosmeticsFeatures.STOCK_READABLE_DESC),
        new Features.Feature(IconPackFeatures.SBI_NOTIF_SCALE, "Notification icon size", "App icons of notifications on the left (same setting as in \"Status bar icons\"). Applies within a minute.", Features.Type.CHOICE, IconPackFeatures.SCALES, 0),
        new Features.Feature(SB_NET_SIZE, "Network speed text size", "Size of the network speed readout (works with every network speed style). Restart System UI after changing.", Features.Type.CHOICE, TEXT_SIZES, 0),
        new Features.Feature(SB_SIDE_PAD, "Status bar side margins", "Moves the clock and the battery further from the screen edges, or a little closer. Restart System UI after changing.", Features.Type.CHOICE, SIDE_PADS, 0),
        new Features.Feature(SB_CLOCK_SECONDS, "Seconds in the status bar clock", "The status bar clock ticks every second. Restart System UI after changing."),
        new Features.Feature(SB_CLOCK_FORMAT, "Status bar clock format", "Geek formats with weekday, date or seconds (examples shown for Monday 21 March, 12:34:56).", Features.Type.CHOICE, CLOCK_FORMATS, 0),
        new Features.Feature(SB_BATTERY_STYLE, "Battery icon style", "Where the battery percentage is shown, or hide the icon / the whole battery. Restart System UI after changing.", Features.Type.CHOICE, BATTERY_STYLES, 0),
        new Features.Feature(SB_BATTERY_INFO, "Battery readout in the status bar", "Shows live battery temperature, charge / discharge current and power as text in the status bar (read from the kernel).", Features.Type.CHOICE, BATTERY_INFO, 0),
        new Features.Feature(SB_BATTERY_INFO_POS, "Battery readout position", "Restart System UI after changing.", Features.Type.CHOICE, BATTERY_INFO_POS, 0),
        new Features.Feature(SB_BATTERY_INTERVAL, "Battery readout refresh", "How often the status bar readout is refreshed.", Features.Type.CHOICE, INTERVALS, 0),
        new Features.Feature(SB_NET_SHOW, "Always show network speed", "Turns the network speed indicator on and shows it in the top status bar too, not only in the pull-down header. Restart System UI after changing."),
        new Features.Feature(SB_NET_FAST, "Network speed refreshes every second", "Stock updates the speed every 3 seconds."),
        new Features.Feature(SB_NET_STYLE, "Network speed style", "Split upload and download speeds into two small rows instead of one total value. Restart System UI after changing.", Features.Type.CHOICE, NET_STYLES, 0),
        new Features.Feature(SB_HIDE_VPN, "Hide VPN icon", "Hides the key / VPN status icon."),
        new Features.Feature(SB_HIDE_NFC, "Hide NFC icon", ""),
        new Features.Feature(SB_HIDE_ALARM, "Hide alarm icon", ""),
        new Features.Feature(SB_HIDE_VIBRATE, "Hide vibrate / Do-not-disturb icons", "Hides the ringer (vibrate / mute) and Do-not-disturb icons."),
        new Features.Feature(SB_HIDE_HEADSET, "Hide headset icon", ""),
        new Features.Feature(SB_HIDE_HD, "Hide HD / VoLTE / VoWiFi icon", ""),
        new Features.Feature(SB_HIDE_MOBILE, "Hide cellular signal icon", "Hides the mobile signal bars and network type (4G / 5G)."),
        new Features.Feature(SB_HIDE_WIFI, "Hide Wi-Fi icon", ""),
        new Features.Feature(SB_HIDE_BLUETOOTH, "Hide Bluetooth icon", ""),
        new Features.Feature(SB_HIDE_HOTSPOT, "Hide hotspot icon", ""),
        new Features.Feature(SB_HIDE_LOCATION, "Hide location icon", ""),
        new Features.Feature(SB_HIDE_CAST, "Hide cast / screen-mirroring icon", ""),
        new Features.Feature(SB_HIDE_ROTATE, "Hide rotation-lock icon", ""),
        new Features.Feature(SB_HIDE_DATA_SAVER, "Hide data-saver icon", ""),
        new Features.Feature(SB_HIDE_ETHERNET, "Hide Ethernet icon", ""),
        new Features.Feature(SB_HIDE_IME, "Hide keyboard / input-method icon", ""),
        new Features.Feature(SB_HIDE_WORK, "Hide work-profile icon", ""),
        new Features.Feature(SB_HIDE_PRIVACY, "Hide camera / microphone / sensors-off icons", "Hides the privacy indicators in the status bar icon row (the green dot overlay is unaffected)."),
        new Features.Feature(SB_NOTIF_NATIVE_ICON, "Original notification icons", "Notifications and the status bar show the small icon the app designed instead of the app's launcher icon."),
        new Features.Feature(SB_QS_CARRIER, "Carrier name in Quick Settings", "Shows the carrier / SIM name in the pull-down header. Restart System UI after changing."),
        new Features.Feature(SB_QS_SEARCH, "Search button in Quick Settings", "Shows the search button in the pull-down header (needs the nubia / Mifavor search app). Restart System UI after changing."),
        new Features.Feature(SB_VOL_NO_WARNING, "No high-volume warning dialog", "System UI stops showing the \"listening at high volume\" dialog when raising media volume with headphones. The system volume cap itself is not changed."),
        new Features.Feature(SB_VOL_NO_VIBRATE, "No vibration on volume-key long press", "Removes the haptic tick when a volume key is held down."),
        new Features.Feature(SB_CLIPBOARD_OVERLAY, "Show clipboard overlay", "Enables Android's copy preview bubble in the bottom corner after copying text or screenshots (hidden on the Chinese firmware)."),
        new Features.Feature(SB_USB_AUTO_ALLOW, "Auto-allow USB debugging", "The \"Allow USB debugging?\" dialog is confirmed automatically with \"always allow from this computer\". Only enable on a phone you keep physically safe."),
        new Features.Feature(SB_ONEHAND_NO_HINT, "Hide one-handed mode hint", "Removes the \"swipe down to exit\" tutorial panel shown in the empty area of one-handed mode.")
    );
}
