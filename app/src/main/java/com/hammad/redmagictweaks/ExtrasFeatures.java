package com.hammad.redmagictweaks;

/**
 * "More apps" — gates found by sweeping the remaining system apps (docs/sweep-findings.md).
 * Most of them are ZTE feature flags read through the framework class com.zte.feature.Feature, which
 * xposed/ExtrasHooks overrides per app; the rest are plain boolean helpers in the app.
 */
public final class ExtrasFeatures {
    private ExtrasFeatures() {}

    // Gestures (com.zte.zgesture)
    public static final String GES_DOUBLE_TAP_SLEEP = "ges_double_tap_sleep";
    public static final String GES_RAISE_WAKE = "ges_raise_wake";
    public static final String GES_SPLIT_SWIPE = "ges_split_swipe";
    public static final String GES_BLACK_LETTERS = "ges_black_letters";
    public static final String GES_RANGE_SHOT = "ges_range_shot";
    public static final String GES_POCKET = "ges_pocket";
    public static final String GES_SHAKE_LIGHT = "ges_shake_light";
    public static final String GES_MOTION = "ges_motion";
    // Battery (com.zte.powersavemode)
    public static final String BAT_SUPER_MODE = "bat_super_mode";
    public static final String BAT_REFRESH_RATE = "bat_refresh_rate";
    public static final String BAT_CHARGE_SEPARATION = "bat_charge_separation";
    public static final String BAT_LONG_CHARGE = "bat_long_charge";
    public static final String BAT_TIME = "bat_time";
    public static final String BAT_5G_SAVE = "bat_5g_save";
    // Key mapping (cn.nubia.keymapcenter)
    public static final String KEY_CONTINUOUS = "key_continuous";
    public static final String KEY_XGRAVITY = "key_xgravity";
    // Lights (cn.nubia.colorfullight)
    public static final String LIGHT_FAN = "light_fan";
    public static final String LIGHT_NEW_PROTOCOL = "light_new_protocol";
    // Phone Manager (com.zte.heartyservice)
    public static final String HS_NO_ADS = "hs_no_ads";
    public static final String HS_LANDSCAPE = "hs_landscape";
    // Magic Voice (cn.nubia.game.magicvoice)
    public static final String MV_CALLS = "mv_calls";
    // Screen cast (cn.nubia.touping)
    public static final String CAST_HIGH_FPS = "cast_high_fps";
    public static final String CAST_ALL_FEATURES = "cast_all_features";
    // NFC (com.android.nfc)
    public static final String NFC_MUTE = "nfc_mute";
    public static final String NFC_SCREEN_OFF = "nfc_screen_off";
    // App cloning (com.zte.cn.doubleapp)
    public static final String CLONE_ANY = "clone_any";
    public static final String CLONE_NO_LIMIT = "clone_no_limit";

    public static final String[] PACKAGES = {
        "com.zte.zgesture", "com.zte.powersavemode", "cn.nubia.keymapcenter", "cn.nubia.colorfullight",
        "com.zte.heartyservice", "cn.nubia.game.magicvoice", "cn.nubia.touping", "com.android.nfc", "com.zte.cn.doubleapp"
    };

    public static final Features.Section SECTION = new Features.Section("extras", "More apps",
        "Gestures, battery, key mapping, lights, Phone Manager, Magic Voice, screen cast, NFC, app cloning",
        R.drawable.ic_extras, PACKAGES,
        new Features.Feature(GES_DOUBLE_TAP_SLEEP, "Gestures: double-tap to sleep", "Forces the ZTE_FEATURE_DOUBLE_TAP_TO_SLEEP / DOUBLETAP flags on so the Gestures app offers double-tap on the home screen / lock screen to turn the screen off. Restart the Gestures app (Settings reopens it)."),
        new Features.Feature(GES_RAISE_WAKE, "Gestures: raise to wake", "Forces ZTE_FEATURE_RAISEWAKE on — pick up the phone to wake the screen. Restart the Gestures app."),
        new Features.Feature(GES_SPLIT_SWIPE, "Gestures: swipe up for split screen", "Forces ZTE_FEATURE_SWIPE_TO_SPLIT_SCREEN on — three-finger swipe up enters split screen. Restart the Gestures app."),
        new Features.Feature(GES_BLACK_LETTERS, "Gestures: off-screen letter gestures", "Forces ZTE_FEATURE_BLACKCHAR on — draw letters on the sleeping screen to launch apps. Restart the Gestures app."),
        new Features.Feature(GES_RANGE_SHOT, "Gestures: three-finger area screenshot", "Forces ZTE_FEATURE_THREE_FINGER_RANGE_SHOT and FINGERSSHOT on — long-press with three fingers and drag to screenshot a region. Restart the Gestures app."),
        new Features.Feature(GES_POCKET, "Gestures: pocket mode", "Forces ZTE_FEATURE_POCKETMODE on — ignores touches while the proximity sensor is covered. Restart the Gestures app."),
        new Features.Feature(GES_SHAKE_LIGHT, "Gestures: shake for flashlight", "Forces ZTE_FEATURE_SHAKE_FLASH_LIGHT on. Restart the Gestures app."),
        new Features.Feature(GES_MOTION, "Gestures: motion for calls and alarms", "Forces MOTIONANSWER, MOTIONALARM and SWITCHSPEAKER on — raise to answer, flip to mute alarms, raise to ear to switch off speaker. Restart the Gestures app."),
        new Features.Feature(BAT_SUPER_MODE, "Battery: ultra power saving mode", "Shows the Super (ultra) power saving mode entry when ZTE removed it (ZTE_FEATURE_REMOVE_SUPER_MODE). Restart the Battery app."),
        new Features.Feature(BAT_REFRESH_RATE, "Battery: refresh-rate options in power saving", "Shows the refresh-rate items the power-saving pages hide (ZTE_FEATURE_POWER_SAVER_REMOVE_REFRESH_RATE and the save-mode item filter). Restart the Battery app."),
        new Features.Feature(BAT_CHARGE_SEPARATION, "Battery: charge separation switch", "Shows the bypass / charge-separation switch in the Battery app outside Game Space (ZTE_FEATURE_BYPASS_CHARGE_SEPARATION). Restart the Battery app."),
        new Features.Feature(BAT_LONG_CHARGE, "Battery: long-charging reminder switch", "Shows the \"remind when charging for a long time\" switch (ZTE_FEATURE_SHOW_LONG_CHARGE_NOTIFY). Restart the Battery app."),
        new Features.Feature(BAT_TIME, "Battery: remaining-time estimate", "Always shows the estimated remaining battery time on the Battery page. Restart the Battery app."),
        new Features.Feature(BAT_5G_SAVE, "Battery: 5G power saving menu", "Shows the 5G power-saving menu and per-app 5G setting. Restart the Battery app."),
        new Features.Feature(KEY_CONTINUOUS, "Key mapping: shoulder-trigger continuous click", "Forces ZTE_FEATURE_KEYMAP_CONTINUOUS_CLICK on in the key-mapping centre so a trigger can repeat taps while held. Restart Game Space."),
        new Features.Feature(KEY_XGRAVITY, "Key mapping: X-Gravity gamepad options", "Forces the operation-devices plugin flag on so the key-mapping centre shows the X-Gravity / gamepad options even when the accessory is not detected. Restart Game Space."),
        new Features.Feature(LIGHT_FAN, "Lights: fan light settings", "Forces COLORFULLIGHT_SUPPORT_DAZZLING, ZTE_FEATURE_GAME_FAN and ZTE_FEATURE_FAN_COLORFUL_LIGHT on so the Lights app shows the fan-light scene and colour options. Only does something if the fan has an RGB LED. Restart the Lights app."),
        new Features.Feature(LIGHT_NEW_PROTOCOL, "Lights: new light protocol", "Forces ZTE_FEATURE_COLORFUL_USE_NEW_PROTOCOL on (newer light-strip config format). Experimental — turn off if lighting stops reacting. Restart the Lights app."),
        new Features.Feature(HS_NO_ADS, "Phone Manager: no ads", "Phone Manager (heartyservice) never shows advertising: the advertising_control check and the app-lock ad switch always read as off. Restart Phone Manager."),
        new Features.Feature(HS_LANDSCAPE, "Phone Manager: allow landscape", "Phone Manager reports landscape support (ro.build.supportLandscape) so its pages rotate. Restart Phone Manager."),
        new Features.Feature(MV_CALLS, "Magic Voice: allow in phone calls", "Removes the dialer / contacts / in-call UI from Magic Voice's block list so the global voice changer also applies during calls. Restart Magic Voice."),
        new Features.Feature(CAST_HIGH_FPS, "Screen cast: high FPS PC mirror", "TouPingStatusUtils.isSupportHightFPS always true — the PC mirror (touping) negotiates its high-frame-rate mode even when the host did not advertise it. Restart Screen cast."),
        new Features.Feature(NFC_MUTE, "NFC: no tap sound", "Silences the NFC start/end/error sounds (NfcService.playSound). Restart NFC service (toggle NFC off/on)."),
        new Features.Feature(NFC_SCREEN_OFF, "NFC: read tags with the screen off / locked", "Reports the screen as on-and-unlocked to the NFC stack so tag reading and card emulation keep working while locked or off (MSG_APPLY_SCREEN_STATE rewrite). Toggle NFC off/on."),
        new Features.Feature(CLONE_ANY, "App cloning: clone any app", "The App cloner list offers every installed app with a launcher icon, not only ZTE's supported list. Restart App cloner."),
        new Features.Feature(CLONE_NO_LIMIT, "App cloning: no clone-count limit", "Removes the memory-based limit on how many apps can be cloned (showLimitedApps). Restart App cloner."),
        new Features.Feature(CAST_ALL_FEATURES, "Screen cast: show every projection option", "Forces the touping support checks on: wired HDMI/DP projection, audio split, main-machine mode, expand (desktop) projection, IM, multi-channel and cloud multicast. Restart Screen cast.")
    );
}
