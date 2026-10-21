package com.hammad.redmagictweaks;

/** `android` (system_server) tweaks. Everything here needs a reboot (or soft reboot) to apply. */
public final class FrameworkFeatures {
    private FrameworkFeatures() {}

    public static final String FREEFORM = "fw_freeform";
    public static final String FREEFORM_NOLIMIT = "fw_freeform_nolimit";
    public static final String ORIENTATION = "fw_orientation_free";
    public static final String PHANTOM = "fw_phantom_off";
    public static final String NO_ALERT_NOTIF = "fw_no_alert_notif";
    public static final String SECURE_SHOT = "fw_secure_screenshot";
    public static final String AIRPLANE_WIFI = "fw_airplane_wifi";
    public static final String AIRPLANE_BT = "fw_airplane_bt";
    public static final String NO_SAFE_VOLUME = "fw_no_safe_volume";
    public static final String DND_VOLUME = "fw_dnd_volume";
    public static final String NO_72H = "fw_no_72h_lock";
    public static final String NO_TELEMETRY = "fw_no_telemetry";
    public static final String NO_THERMAL_LIMIT = "fw_no_thermal_limit";
    public static final String SCREEN_NEVER = "fw_screen_never_off";
    public static final String VOLUME_BOOST = "fw_volume_boost";
    public static final String[] VOLUME_BOOST_LEVELS = {"Off", "+25 %", "+50 %", "+100 %", "+150 %"};
    public static final String FORCE_RATE = "fw_force_rate";
    public static final String[] FORCE_RATES = {"Off (ZTE auto / Settings)", "Highest the panel offers (165 Hz with the DTBO mod)", "144 Hz", "120 Hz", "90 Hz", "60 Hz"};
    public static final int[] FORCE_RATE_VALUES = {0, -1, 144, 120, 90, 60};
    public static final String FORCE_RATE_VIDEO_60 = "fw_force_rate_video60";
    public static final String AUTO_RATE_MAX = "fw_auto_rate_max";
    public static final String POWER_ASSIST = "fw_power_assist";
    public static final String UNTRUSTED_TOUCH = "fw_untrusted_touch";
    public static final String QUIET_UNLOCKED = "fw_quiet_when_unlocked";

    public static final String[] FREEFORM_SCOPE = {"Stock", "All user apps", "All apps (incl. system)"};

    public static final Features.Section SECTION = new Features.Section("framework", "Android framework",
        "System-level behaviour: small windows, rotation, background, screenshots, audio",
        R.drawable.ic_settings,
        new String[]{"android"},
        new Features.Feature(FREEFORM, "Small window for every app", "Lets any app open in a floating small window (WindowReply) from recents / Game Space, not just the whitelist. Reboot.", Features.Type.CHOICE, FREEFORM_SCOPE, 0),
        new Features.Feature(FREEFORM_NOLIMIT, "No 3-window limit", "Removes the cap on how many small windows can be open at once and its warning toast. Reboot."),
        new Features.Feature(ORIENTATION, "Ignore apps' fixed orientation", "Apps that lock themselves to portrait follow the phone rotation instead (user apps only; same path Android 16 uses for large screens). Reboot."),
        new Features.Feature(PHANTOM, "Phantom-process limit off", "Stops Android from killing background child processes (Termux, emulators, sync tools). Reboot."),
        new Features.Feature(NO_ALERT_NOTIF, "Hide 'displaying over other apps' notification", "No persistent notification for apps drawing overlays. Reboot."),
        new Features.Feature(SECURE_SHOT, "Screenshots in protected apps", "Allows screenshots and screen recording in apps that set FLAG_SECURE (banking, streaming). Apps are not told a capture happened. Reboot."),
        new Features.Feature(AIRPLANE_WIFI, "Airplane mode keeps Wi-Fi on", "Turning airplane mode on no longer switches Wi-Fi / hotspot off. Reboot."),
        new Features.Feature(AIRPLANE_BT, "Airplane mode keeps Bluetooth on", "Turning airplane mode on no longer switches Bluetooth off. Reboot."),
        new Features.Feature(NO_SAFE_VOLUME, "No 'safe volume' warning", "Headphone volume is never clamped and the hearing-safety dialog never appears. Reboot."),
        new Features.Feature(DND_VOLUME, "Adjust volume under Do Not Disturb", "Volume keys still change muted streams while DND is on. Reboot."),
        new Features.Feature(NO_72H, "No 72-hour password re-prompt", "The lock screen never forces PIN/password instead of fingerprint after 72 h. Reboot."),
        new Features.Feature(NO_TELEMETRY, "Block ZTE telemetry", "Disables both built-in usage trackers in the system server. Reboot."),
        new Features.Feature(NO_THERMAL_LIMIT, "No thermal throttling of refresh rate / effects", "The system never reports a thermal status above NONE to apps and System UI (except emergency shutdown), so refresh rate and blur are not reduced when warm. Reboot."),
        new Features.Feature(SCREEN_NEVER, "Screen never turns off", "Overrides the screen-off timeout with 'never' (~24 days) regardless of Settings. Reboot."),
        new Features.Feature(POWER_ASSIST, "Long-press power opens assistant", "Holding the power button launches the default assistant instead of the power menu (power menu stays on the long-press of volume-down + power). Reboot."),
        new Features.Feature(UNTRUSTED_TOUCH, "Allow touches through overlays", "Disables the untrusted-touch block so apps still get taps while another app draws an overlay. Reboot."),
        new Features.Feature(AUTO_RATE_MAX, "Auto refresh rate goes up to 165 Hz", "In the Auto (smart) refresh-rate mode ZTE caps the high step at 120 Hz. With this on, Auto ramps between 60 Hz and the highest rate the panel offers (165 Hz with the DTBO mod) exactly like stock does between 60 and 120 — apps, scrolling and games get 165, idle still drops. Settings keeps working. Takes effect after a reboot or after changing the refresh-rate setting once."),
        new Features.Feature(FORCE_RATE, "Force refresh rate in every app", "Overrides ZTE's smart refresh-rate engine: every refresh-rate decision while the screen is on (per-app lists, scrolling, idle fallback, brightness, Game Space votes) is replaced by this rate, like the OnePlus Force165 tool but system-wide. Screen-off / AOD rates are left alone. Applies within a second; no reboot.", Features.Type.CHOICE, FORCE_RATES, 0),
        new Features.Feature(FORCE_RATE_VIDEO_60, "…but 60 Hz in video apps", "With the forced rate on, YouTube, Netflix, Prime Video, Disney+, VLC, MX Player and the stock video player still get 60 Hz to avoid judder."),
        new Features.Feature(VOLUME_BOOST, "Volume boost past maximum", "When media volume is already at 100 %, every playing media / game session gets an extra loudness gain (LoudnessEnhancer). Louder than the speaker was tuned for — can clip or distort; start low. Reboot.", Features.Type.CHOICE, VOLUME_BOOST_LEVELS, 0),
        new Features.Feature(QUIET_UNLOCKED, "Silent notifications while unlocked", "No notification sound or vibration while the screen is on and unlocked; lights and the shade still work. Reboot.")
    );
}
