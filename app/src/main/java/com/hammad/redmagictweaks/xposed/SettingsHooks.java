package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.content.Intent;

import com.hammad.redmagictweaks.SettingsFeatures;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/** com.android.settings — hidden developer options, screen timeout, USB mode / pop-up, ADB install auth, network type. */
final class SettingsHooks {
    private SettingsHooks() {}

    private static final String PROPS = "com.zte.settings.utils.SystemPropertiesZTE";
    private static final String PROJECT_UTILS = "com.zte.settings.utils.ZteProjectUtils";
    private static final String TIMEOUT_ADAPT = "com.zte.adapt.display.ScreenTimeoutSettingsAdapt";
    private static final String TIMEOUT_CTRL = "com.android.settings.display.ScreenTimeoutPreferenceController";
    private static final String GAME_LAUNCH = "com.zte.settings.development.GameSpaceLaunchModePreferenceController";
    private static final String USB_RECEIVER = "com.zte.settings.connecteddevice.ZTEUsbConnectReceiver";
    private static final String USB_UTILS = "com.zte.settings.connecteddevice.UsbModeUtils";
    private static final String ADB_INSTALL = "com.zte.settings.development.EnableAdbInstallPreferenceController";
    private static final String NET_MODE = "com.zte.settings.network.telephony.ZteEnabledNetworkModePreferenceController";

    private static final String PROP_TIMEOUT = "ro.vendor.feature.zte_feature_change_screen_timeout";
    private static final String PROP_DEL_REFRESH = "ro.vendor.feature.setting_delete_show_and_lock_refresh_rate";
    private static final String PROP_HIDE_5G_DEBUG = "ro.vendor.feature.zte_feature_hide_5g_msg_debug";

    static void install(ClassLoader cl) {
        featureProps(cl);
        devOptions(cl);
        screenTimeout(cl);
        usb(cl);
        adbInstall(cl);
        networkType(cl);
        // the 165 Hz choice on Android 15 caused flicker and reboots on global firmware: RM Tweaks only
        if (!com.hammad.redmagictweaks.FlavorBits.publicBuild() || android.os.Build.VERSION.SDK_INT >= 36) {
            try { SettingsRates.install(cl); } catch (Throwable e) { Lx.e("Settings: refresh-rate page", e); }
        }
        Lx.i("Settings: hooks ready");
    }

    /** ZTE feature flags are read through SystemPropertiesZTE.getBoolean (mostly once, in ZteProjectUtils' static init). */
    private static void featureProps(ClassLoader cl) {
        Class<?> props = H.cls(cl, PROPS);
        if (props == null) return;
        H.hook(H.find(props, "getBoolean", String.class, boolean.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                String k = (String) p.args[0];
                if (k == null) return;
                if (PROP_TIMEOUT.equals(k) && Cfg.on(SettingsFeatures.SET_TIMEOUT_LONG)) p.setResult(false);
                else if (Cfg.on(SettingsFeatures.SET_DEV_HIDDEN) && (PROP_DEL_REFRESH.equals(k) || PROP_HIDE_5G_DEBUG.equals(k))) p.setResult(false);
            }
        });
    }

    private static void devOptions(ClassLoader cl) {
        Class<?> g = H.cls(cl, GAME_LAUNCH);
        if (g != null) H.constant(g, "isAvailable", boolean.class, H.types(), true, () -> Cfg.on(SettingsFeatures.SET_DEV_HIDDEN));
    }

    private static void screenTimeout(ClassLoader cl) {
        // The static flag may already be initialised when the switch is turned on later: force it as well.
        Class<?> pu = H.cls(cl, PROJECT_UTILS);
        if (pu != null && Cfg.on(SettingsFeatures.SET_TIMEOUT_LONG)) {
            try { XposedHelpers.setStaticBooleanField(pu, "CHANGE_SCREEN_TOMEOUT_TO_TEN", false); }
            catch (Throwable t) { Lx.w("Settings: CHANGE_SCREEN_TOMEOUT_TO_TEN not writable: " + t); }
        }
        Class<?> adapt = H.cls(cl, TIMEOUT_ADAPT);
        if (adapt != null) {
            H.hook(H.find(adapt, "getInitialValuesLength", int.class, CharSequence[].class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!Cfg.on(SettingsFeatures.SET_TIMEOUT_LONG)) return;
                    CharSequence[] values = (CharSequence[]) p.args[1];
                    if (values != null) p.setResult(values.length);
                }
            });
        }
        Class<?> ctrl = H.cls(cl, TIMEOUT_CTRL);
        if (ctrl != null) {
            // setDefaultTimeValue(long) writes screen_off_timeout = 60 s whenever the current value is "too long".
            H.hook(H.find(ctrl, "setDefaultTimeValue", long.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (Cfg.on(SettingsFeatures.SET_TIMEOUT_LONG)) {
                        Lx.i("Settings: blocked screen-timeout reset to 1 minute");
                        p.setResult("");
                    }
                }
            });
        }
    }

    private static int usbMode() {
        int i = Cfg.choice(SettingsFeatures.SET_USB_MODE);
        if (i <= 0 || i >= SettingsFeatures.USB_MODE_VALUES.length) return -1;
        return SettingsFeatures.USB_MODE_VALUES[i];
    }

    /** USB plugged in -> ZTEUsbConnectReceiver.startUsbModeChooserActivity(Intent) shows the "Use USB for" dialog. */
    private static void usb(ClassLoader cl) {
        Class<?> rcv = H.cls(cl, USB_RECEIVER);
        final Class<?> utils = H.cls(cl, USB_UTILS);
        if (rcv == null || utils == null) return;
        final Method setMode = H.find(utils, "setCurrentMode", android.hardware.usb.UsbManager.class, int.class);
        if (setMode == null) Lx.w("Settings: UsbModeUtils.setCurrentMode not found");
        H.hook(H.find(rcv, "startUsbModeChooserActivity", Intent.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                int mode = usbMode();
                boolean hide = Cfg.on(SettingsFeatures.SET_USB_NO_POPUP);
                if (mode < 0 && !hide) return;
                if (mode >= 0 && setMode != null) {
                    try {
                        Context c = Lx.context();
                        if (c == null) c = (Context) XposedHelpers.getObjectField(p.thisObject, "mContext");
                        Object usb = c == null ? null : c.getSystemService("usb");
                        if (usb != null) {
                            setMode.invoke(null, usb, mode);
                            Lx.i("Settings: USB mode set to " + mode);
                        }
                    } catch (Throwable t) { Lx.e("Settings: set USB mode", t); }
                }
                if (hide) {
                    Lx.i("Settings: USB mode pop-up suppressed");
                    p.setResult(null);
                }
            }
        });
    }

    /** "Install via USB" switch: the account password check is replaced by the success result it would have produced. */
    private static void adbInstall(ClassLoader cl) {
        Class<?> c = H.cls(cl, ADB_INSTALL);
        if (c == null) return;
        XC_MethodHook skip = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!Cfg.on(SettingsFeatures.SET_ADB_INSTALL_NOAUTH)) return;
                try {
                    // onActivityResult(1002, RESULT_OK, null): writes adb_install_enabled=1, checks the switch, informs PackageManager.
                    XposedHelpers.callMethod(p.thisObject, "onActivityResult", 1002, -1, (Intent) null);
                    Lx.i("Settings: ADB install enabled without account check");
                    p.setResult(null);
                } catch (Throwable t) { Lx.e("Settings: ADB install skip", t); }
            }
        };
        int n = 0;
        n += H.hook(H.find(c, "launchAuthCompActivity"), skip) ? 1 : 0;
        n += H.hook(H.find(c, "launchNubiaActivity"), skip) ? 1 : 0;
        if (n == 0) Lx.w("Settings: ADB install auth launchers not found");
    }

    /** Preferred network type picker: CONDITIONALLY_UNAVAILABLE (2) -> AVAILABLE (0). */
    private static void networkType(ClassLoader cl) {
        Class<?> c = H.cls(cl, NET_MODE);
        if (c == null) return;
        H.hook(H.find(c, "getAvailabilityStatus", int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (Cfg.on(SettingsFeatures.SET_NET_MODE) && Integer.valueOf(2).equals(p.getResult())) p.setResult(0);
            }
        });
    }
}
