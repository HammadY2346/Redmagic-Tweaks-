package com.hammad.redmagictweaks.xposed;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps System UI drawable resource names to icon-pack keys (nubia-ly SystemIconPackPolicy, inspected on this
 * firmware) and strength resources to a 0..4 level (nubia-ly ConnectivityResourcePolicy). Exact aliases only:
 * unknown, no-internet, satellite and alert variants keep the stock artwork.
 */
final class IconKeys {
    private IconKeys() {}

    private static final Pattern WIFI = Pattern.compile("(?:(?:intl_)?ic_wifi_(?:[4-7]_)?signal_|statusbar_wifi(?:6|7)?(?:_(?:link|lock|link_lock|hs|mlo|link_mlo))?_)([0-4])");
    private static final Pattern WIFI_BAR = Pattern.compile("signal_wifi(?:6|7)?_([0-4])(?:_(?:link|lock|lock_link|hs|lock_hs))?_bar");
    private static final Pattern WIFI_NEW = Pattern.compile("ic_wifi_([0-3])");
    private static final Pattern MOBILE = Pattern.compile("(?:(?:sp_signal_cellular|statusbar_signal)_([0-5])|signal_cellular_([0-5])_bar_single)");

    private static final Pattern WIFI_NO_BAR = Pattern.compile("(?:qs_)?signal_wifi(?:6|7)?_([0-4])_no(?:_link|_hs)?_bar");
    private static final Pattern WIFI_NO_INTERNET = Pattern.compile("signal_wifi(?:6|7)?_statusbar_connected(?:_hs)?_no_?internet(?:_([0-4]))?");

    /** Level 0..4 of a Wi-Fi "connected, no internet" resource (statusbar / bar variants of this firmware), or -1. */
    static int wifiNoInternet(String resource) {
        if (resource == null) return -1;
        Matcher m = WIFI_NO_BAR.matcher(resource);
        if (m.matches()) return Integer.parseInt(m.group(1));
        m = WIFI_NO_INTERNET.matcher(resource);
        if (m.matches()) return m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
        return -1;
    }

    /** Strength level 0..4 encoded in a Wi-Fi / mobile resource name, or -1. */
    static int level(boolean wifi, String resource) {
        if (resource == null) return -1;
        Matcher m;
        if (wifi) {
            m = WIFI.matcher(resource); if (m.matches()) return Integer.parseInt(m.group(1));
            m = WIFI_BAR.matcher(resource); if (m.matches()) return Integer.parseInt(m.group(1));
            m = WIFI_NEW.matcher(resource); return m.matches() ? Math.round(Integer.parseInt(m.group(1)) * 4f / 3f) : -1;
        }
        m = MOBILE.matcher(resource);
        if (!m.matches()) return -1;
        String raw = m.group(1) == null ? m.group(2) : m.group(1);
        return Math.round(Integer.parseInt(raw) * 4f / 5f);
    }

    /** Semantic QS mapping: the tile spec decides the key, not the firmware's drawable name. null = keep native. */
    static String tileKey(String spec, boolean transientState, String nativeKey) {
        if (spec == null || spec.isEmpty()) return nativeKey;
        if (transientState) return null;
        switch (spec) {
            case "wifi": case "internet": return nativeKey != null && nativeKey.startsWith("wifi_") ? nativeKey : null;
            case "bt": case "bluetooth": return "bluetooth_connected".equals(nativeKey) ? nativeKey : "bluetooth";
            case "data": case "cell": return "data";
            case "flashlight": return "flashlight";
            case "hotspot": return "hotspot";
            case "airplane": return "airplane";
            case "location": return "location";
            case "rotation": return nativeKey;   // keep locked portrait / landscape versus auto-rotate
            case "sound": return nativeKey;      // keep the native three-way ringer mode
            case "dnd": case "modes": return "dnd";
            case "nfc": return "nfc";
            case "screenrecord": case "videoscreen": return "screenrecord";
            case "cast": return "cast_connected".equals(nativeKey) ? nativeKey : "cast";
            case "battery": case "saver": case "superpower": return "battery_saver";
            case "saver_data": case "data_saver": return "data_saver";
            case "night": case "night_display": case "readMode": return "night";
            case "alarm": return "alarm";
            case "controls": return "controls";
            case "qr_code_scanner": return "qr";
            case "nearby": case "custom(com.google.android.gms/.nearby.sharing.SharingTileService)": return "share";
            case "gamespace": return "game";
            case "lamp": case "lampMode": return "lighting";
            case "coolingFan": return "fan";
            case "liquidCooling": return "liquid";
            case "sync": return "sync";
            case "inversion": case "invert": return "invert";
            case "dark": case "ui_mode_night": return "dark";
            case "reduce_brightness": case "extra_dim": return "extra_dim";
            case "screenshot": return "screenshot";
            default: return null;                // unmapped vendor features and third-party tiles stay native
        }
    }

    /** Icon-pack key for a System UI / framework drawable name, or null. */
    static String key(String name) {
        if (name == null) return null;
        int wifi = level(true, name);
        if (wifi >= 0) return "wifi_" + wifi;
        if (name.matches("ic_qs_wifi_(?:[4-7]_)?[0-4]")) return "wifi_" + name.charAt(name.length() - 1);
        if (name.matches("ic_signal_cellular_[0-4]_4_bar")) return "signal_" + name.charAt(19);
        switch (name) {
            case "airplanemode_off": case "airplanemode_on": case "airplaneoff_on": case "airplaneoff_on_fw": case "airplaneon_off": case "airplane_offtoon": case "outer_airplanemode": case "sp_airplanemode": case "statusbar_airplanemode": case "qs_airplane_icon_on": case "qs_airplane_icon_off": case "intl_ic_qs_airplane": case "stat_sys_airplane_mode": case "ic_signal_airplane": case "ic_qs_airplane": return "airplane";
            case "flashlight": case "flashlight_animation": case "flashlight_off": case "flashlight_offtoon": case "flashlight_ontooff": case "ic_flashlight_off": case "ic_flashlight_on": case "sp_flashlight": case "sp_flashlight_x": case "qs_flashlight_icon_on": case "qs_flashlight_icon_off": case "ic_eqs_flashlight_on": case "ic_eqs_flashlight_off": case "ic_eqs_flashlight_on_from_off_animation": case "ic_qs_flashlight_off": case "ic_signal_flashlight": case "stat_sys_flashlight": case "ic_qs_flashlight": return "flashlight";
            case "bluetooth": case "qs_bluetooth_icon_on": case "qs_bluetooth_icon_off": case "intl_ic_qs_bluetooth": case "ic_qs_bluetooth_on": case "ic_qs_bluetooth": case "stat_sys_data_bluetooth": return "bluetooth";
            case "bluetooth_connected": case "ic_bluetooth_connected": case "stat_sys_data_bluetooth_connected": case "ic_qs_bluetooth_connected": return "bluetooth_connected";
            case "ic_qs_screen_rotation_portrait_to_auto_rotate": case "ic_qs_screen_rotation_portrait_to_auto_rotate_animation": case "qs_auto_rotate_icon_on": case "ic_qs_auto_rotate": return "rotate";
            case "ic_qs_screen_rotation_portrait_from_auto_rotate": case "ic_qs_screen_rotation_portrait_from_auto_rotate_animation": case "stat_sys_rotate_portrait": case "stat_sys_rotate_landscape": case "statusbar_rotate_portrait": case "statusbar_rotate_landscape": case "qs_auto_rotate_icon_off": return "portrait";
            case "ic_wifi_4_hotspot": case "ic_wifi_5_hotspot": case "ic_wifi_6_hotspot": case "qs_hotspot_icon_on": case "qs_hotspot_icon_off": case "ic_hotspot": case "stat_sys_hotspot": return "hotspot";
            case "qs_location_icon_on": case "qs_location_icon_off": case "ic_location": case "ic_signal_location": case "stat_sys_location": return "location";
            case "stat_sys_alarm": case "stat_sys_alarm_dim": case "ic_alarm": case "ic_alarm_dim": case "ic_qs_alarm": return "alarm";
            case "notifications_off_lock": case "sp_sound_slient": case "stat_sys_ringer_silent": case "ic_qs_ringer_silent": case "ic_volume_ringer_mute": return "silent";
            case "vibration_lock": case "vibration": case "notifications_vibration": case "sp_vibrate": case "sp_vibration": case "stat_sys_ringer_vibrate": case "ic_qs_ringer_vibrate": case "ic_volume_ringer_vibrate": return "vibrate";
            case "notifications_full": case "sp_ringtone_notification": case "ic_qs_ringer_audible": case "ic_volume_ringer": return "ring";
            case "qs_dnd_icon_on": case "qs_dnd_icon_off": case "stat_sys_dnd": case "stat_sys_dnd_vec": case "ic_no_disturb": case "ic_qs_dnd": case "ic_qs_dnd_on": return "dnd";
            case "qs_data_saver_icon_on": case "qs_data_saver_icon_off": case "ic_data_saver": case "stat_sys_data_saver": case "stat_sys_data_saver_vec": return "data_saver";
            case "stat_sys_nfc": case "ic_qs_nfc": return "nfc";
            case "ic_cast": case "ic_qs_cast_off": return "cast";
            case "ic_cast_connected": case "ic_qs_cast_on": case "stat_sys_cast": return "cast_connected";
            case "qs_battery_saver_icon_on": case "qs_battery_saver_icon_off": case "ic_qs_battery_saver": case "ic_power_saver": return "battery_saver";
            case "qs_screen_record_icon_on": case "qs_screen_record_icon_off": case "ic_screenrecord": case "ic_screen_recorder": return "screenrecord";
            case "ic_swap_vert": case "ic_qs_cellular": return "data";
            case "stat_sys_vpn_ic": case "stat_sys_branded_vpn": return "vpn";
            case "roam_enable": case "stat_sys_roaming": case "ic_roaming": return "roaming";
            case "ic_headset": case "stat_sys_headset": return "headset";
            case "ic_headset_mic": case "stat_sys_headset_mic": return "headset_mic";
            case "qs_nightlight_icon_on": case "qs_nightlight_icon_off": case "ic_qs_night_display_on": case "ic_qs_ui_mode_night": return "night";
            default: return null;
        }
    }
}
