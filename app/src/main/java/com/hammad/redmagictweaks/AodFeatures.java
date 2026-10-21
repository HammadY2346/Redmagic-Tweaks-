package com.hammad.redmagictweaks;

import android.content.Context;

/**
 * Always-on display customization, hooked in com.android.systemui by xposed/AodHooks (ZTE's com.zte.feature.doze).
 * Shown in Lock screen & AOD › Always-on display, next to the seconds and date switches of LockFeatures.
 */
public final class AodFeatures {
    private AodFeatures() {}

    public static final String BRIGHTNESS = "aod_brightness", TAP_TIME = "aod_tap_time", LOW_BATTERY = "aod_low_battery",
            POWER_SAVING = "aod_power_saving", BURN_IN = "aod_burn_in", FADE = "aod_fade", BATTERY = "aod_battery",
            NOTIFICATIONS = "aod_notifications", LUNAR = "aod_lunar", NOTIF_COUNT = "aod_notif_count", NOTIF_SIZE = "aod_notif_size",
            NOTIF_APP_ICONS = "aod_notif_app_icons", CLOCK_COLOR = "aod_clock_color", CLOCK_SIZE = "aod_clock_size";

    private static final String NEXT = " Applies the next time the AOD shows.";
    private static final String[] SHOW = {"Stock (per style)", "Always show", "Always hide"};

    public static final String[] CLOCK_COLORS = {"Stock (style colour)", "White", "Wallpaper colour", "RedMagic red", "Blue", "Green", "Purple", "Orange",
            "Pink", "Teal", "Gold", "Sunset gradient", "Ocean gradient", "RedMagic gradient", "Aurora gradient"};
    private static final int[][] CLOCK_RGB = {null, {0xFFFFFFFF}, null, {0xFFE8343B}, {0xFF3E8BFF}, {0xFF34C759}, {0xFF9B6BFF}, {0xFFFF9F0A},
            {0xFFFF5C93}, {0xFF2EC4B6}, {0xFFE6B84C}, {0xFFFF9F0A, 0xFFFF2D55}, {0xFF2EC4B6, 0xFF3E5BFF}, {0xFFFF5A3C, 0xFFC8102E}, {0xFF34C759, 0xFF9B6BFF}};

    /** {top, bottom} colours of the AOD clock for a CLOCK_COLORS choice, or null for the style's own colour. */
    public static int[] clockColors(Context ctx, int choice) {
        if (choice <= 0 || choice >= CLOCK_RGB.length) return null;
        if (choice == 2) {
            int a;
            try { a = ctx.getColor(android.R.color.system_accent1_200); } catch (Throwable t) { a = 0xFF3E8BFF; }
            return new int[]{a, a};
        }
        int[] c = CLOCK_RGB[choice];
        return c.length == 1 ? new int[]{c[0], c[0]} : c.clone();
    }

    public static final Features.Section SECTION = new Features.Section("aod", "Always-on display",
        "Brightness, timing, burn-in shift, what each style shows, notification icons and the clock",
        R.drawable.ic_cat_lockscreen,
        new String[]{"com.android.systemui"},
        new Features.Feature(BRIGHTNESS, "AOD brightness", "Fixes how bright the always-on display is instead of following the light sensor. Low saves the most power; High reads best in daylight." + NEXT,
                Features.Type.CHOICE, new String[]{"Automatic (stock)", "Low", "Medium", "High"}, 0),
        new Features.Feature(TAP_TIME, "Tap-to-show time", "With the AOD set to show when you tap the screen, how long it stays before it fades out." + NEXT,
                Features.Type.CHOICE, new String[]{"Stock (10 s)", "5 s", "20 s", "30 s", "1 minute"}, 0),
        new Features.Feature(LOW_BATTERY, "Low-battery cut-off", "With RedMagic's \"turn the AOD off at low battery\" on, the battery level at which it goes off, or never." + NEXT,
                Features.Type.CHOICE, new String[]{"Stock (10 %)", "Never turn off", "5 %", "15 %", "20 %", "30 %"}, 0),
        new Features.Feature(POWER_SAVING, "Keep the AOD in super power saving", "Super power saving normally turns the always-on display off; this keeps it on (it uses a little battery)." + NEXT),
        new Features.Feature(BURN_IN, "Burn-in shift", "Every minute the AOD moves a few pixels so the OLED panel does not burn in. Off keeps it still (not recommended for long periods); Stronger moves further every 30 seconds." + NEXT,
                Features.Type.CHOICE, new String[]{"Stock (every minute)", "Off (stays still)", "Gentle (smaller steps)", "Stronger (every 30 s)"}, 0),
        new Features.Feature(FADE, "Fade in and out", "How long the AOD takes to fade in when the screen goes off and out when it hides." + NEXT,
                Features.Type.CHOICE, new String[]{"Stock (1 s)", "Instant", "Quick (0.3 s)", "Slow (2 s)"}, 0),
        new Features.Feature(BATTERY, "Battery on AOD", "Shows or hides the battery line on every AOD style, also on styles that have no switch for it." + NEXT,
                Features.Type.CHOICE, SHOW, 0),
        new Features.Feature(NOTIFICATIONS, "Notification icons on AOD", "Shows or hides the icons of waiting notifications on every AOD style." + NEXT,
                Features.Type.CHOICE, SHOW, 0),
        new Features.Feature(LUNAR, "Lunar date on AOD", "Shows or hides the Chinese lunar date on the styles that have one." + NEXT,
                Features.Type.CHOICE, SHOW, 0),
        new Features.Feature(NOTIF_COUNT, "How many notification icons", "How many app icons the AOD shows before the \"more\" icon. Restart System UI.",
                Features.Type.CHOICE, new String[]{"Stock (4)", "3", "6", "8"}, 0),
        new Features.Feature(NOTIF_SIZE, "Notification icon size", "Makes the AOD notification icons smaller or bigger. Restart System UI.",
                Features.Type.CHOICE, new String[]{"Stock", "80 %", "120 %", "140 %"}, 0),
        new Features.Feature(NOTIF_APP_ICONS, "Colour app icons on AOD", "The AOD shows each app's own colour icon instead of the small white notification icon." + NEXT),
        new Features.Feature(CLOCK_COLOR, "AOD clock colour", "Colours the AOD clock digits: white, your wallpaper colour, plain colours or a gradient. Works on the styles that colour their digits (most digital and text styles)." + NEXT,
                Features.Type.CHOICE, CLOCK_COLORS, 0),
        new Features.Feature(CLOCK_SIZE, "AOD clock size", "Makes the AOD clock (with its date and battery) smaller or bigger." + NEXT,
                Features.Type.CHOICE, new String[]{"Stock", "80 %", "90 %", "110 %", "120 %", "130 %"}, 0)
    );
}
