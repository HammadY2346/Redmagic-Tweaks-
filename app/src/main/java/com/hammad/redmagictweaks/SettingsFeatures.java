package com.hammad.redmagictweaks;

/** Settings app tweaks (hooked in com.android.settings by xposed/SettingsHooks, default-launcher part in com.android.permissioncontroller by xposed/PermissionHooks). */
public final class SettingsFeatures {
    private SettingsFeatures() {}

    public static final String SET_DEV_HIDDEN = "set_dev_hidden";
    public static final String SET_TIMEOUT_LONG = "set_timeout_long";
    public static final String SET_USB_MODE = "set_usb_mode";
    public static final String SET_USB_NO_POPUP = "set_usb_no_popup";
    public static final String SET_ADB_INSTALL_NOAUTH = "set_adb_install_noauth";
    public static final String SET_NET_MODE = "set_net_mode";
    public static final String SET_ANY_LAUNCHER = "set_any_launcher";

    /** Index -> UsbModeUtils mode value: 0 charge only, 2 MTP, 3 PTP, 4 casting (index 0 = stock, ask). */
    public static final String[] USB_MODES = {"Stock (ask every time)", "Charge only", "File transfer (MTP)", "Photo transfer (PTP)", "Screen casting"};
    public static final int[] USB_MODE_VALUES = {-1, 0, 2, 3, 4};

    public static final Features.Section SECTION = new Features.Section("settings", "Settings app",
        "Hidden developer options, screen timeout, USB behaviour, ADB install, default launcher",
        R.drawable.ic_settings,
        new String[]{"com.android.settings", "com.android.permissioncontroller"},
        new Features.Feature(SET_DEV_HIDDEN, "Show hidden developer options", "Shows the developer entries ZTE hides on this model: Show / Lock refresh rate, 5G debug messages and the Game Space launch mode picker. Reopen Developer options after changing."),
        new Features.Feature(SET_TIMEOUT_LONG, "Screen timeout 30 minutes / never", "Restores the two longest screen-timeout choices that ZTE removes (30 minutes and never) and stops Settings from resetting a long timeout back to 1 minute. Reopen the timeout page after changing."),
        new Features.Feature(SET_USB_MODE, "Default USB mode", "The USB mode that is switched to automatically when a cable is plugged in, instead of staying in charge-only until you pick one.", Features.Type.CHOICE, USB_MODES, 0),
        new Features.Feature(SET_USB_NO_POPUP, "Hide the USB mode pop-up", "No \"Use USB for\" dialog when a cable is connected. The USB notification still opens the picker when you tap it."),
        new Features.Feature(SET_ADB_INSTALL_NOAUTH, "Enable ADB install without account check", "Turning on \"Install via USB\" in Developer options no longer asks for the ZTE / nubia account password; the switch simply turns on."),
        new Features.Feature(SET_NET_MODE, "Show preferred network type", "Shows the mobile network type (5G/4G/3G/2G) picker in SIM settings even when ZTE hides it for the carrier or SIM."),
        new Features.Feature(SET_ANY_LAUNCHER, "Allow any launcher as default home", "The Default home app list (Settings > Apps > Default apps > Home) also lists third-party launchers, not only system ones.")
    );
}
