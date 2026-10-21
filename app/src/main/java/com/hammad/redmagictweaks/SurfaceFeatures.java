package com.hammad.redmagictweaks;

import com.hammad.redmagictweaks.xposed.SurfaceArt;

/**
 * Notification cards, heads-up behaviour, status-bar notification icons, volume sliders and the power menu, ported
 * from nubia-ly (NotificationBackend, NotificationBehaviorBackend, NotificationIconBackend, VolumeBackend,
 * PowerMenuBackend). Hooked in com.android.systemui by xposed/SurfaceHooks.
 * Supersedes CosmeticsFeatures.COS_NOTIF_STYLE / COS_NOTIF_OPACITY / COS_POWER_STYLE / COS_VOLUME_STYLE and
 * StatusBarFeatures.SB_NOTIF_MAX.
 */
public final class SurfaceFeatures {
    private SurfaceFeatures() {}

    public static final String NOTIF_STYLE = "notif_style";
    public static final String NOTIF_OPACITY = "notif_opacity";
    public static final String NOTIF_CORNERS = "notif_corners";
    public static final String NOTIF_NO_AUTO_HEADSUP = "notif_no_auto_headsup";
    public static final String NOTIF_OVERFLOW_COUNT = "notif_overflow_count";
    public static final String NOTIF_ICON_LIMIT = "notif_icon_limit";
    public static final String VOL_STYLE = "vol_style";
    public static final String PWR_STYLE = "pwr_style";
    public static final String VOL_ONEUI = "vol_oneui8";
    public static final String PWR_ONEUI = "pwr_oneui8";

    public static final String[] ICON_LIMITS = {"Stock", "1", "2", "3", "4", "5", "6", "7", "8", "10", "As many as fit"};
    public static final int[] ICON_LIMIT_VALUES = {0, 1, 2, 3, 4, 5, 6, 7, 8, 10, Integer.MAX_VALUE};

    public static int iconLimit(int choice) { return choice > 0 && choice < ICON_LIMIT_VALUES.length ? ICON_LIMIT_VALUES[choice] : 0; }

    public static final Features.Section SECTION = new Features.Section("surface", "Notifications, volume & power menu",
        "Notification card looks and behaviour, status-bar notification icons, volume slider and power-menu styles",
        R.drawable.ic_sysui,
        new String[]{"com.android.systemui"},
        new Features.Feature(NOTIF_STYLE, "Notification card finish", "Decorative finish drawn over each notification card background; the card colours and blur stay stock, except \"One UI\", which makes the card an opaque Samsung-style dark (or light) surface with a hairline edge. Applies immediately.", Features.Type.CHOICE, SurfaceArt.NOTIF_STYLES, 0),
        new Features.Feature(NOTIF_OPACITY, "Notification card opacity", "Makes the card backgrounds partly transparent (text and icons stay solid). Applies immediately.", Features.Type.CHOICE, SurfaceArt.OPACITIES, 0),
        new Features.Feature(NOTIF_CORNERS, "Notification card corners", "Rounds the card background corners more than stock (26 dp matches One UI 7/8). Applies immediately.", Features.Type.CHOICE, SurfaceArt.CORNERS, 0),
        new Features.Feature(NOTIF_NO_AUTO_HEADSUP, "No automatic heads-up pop-ups", "Notifications no longer drop down as a floating banner over what you are doing; they go straight to the shade. Banners you pinned yourself (and full-screen alarms / calls) still show."),
        new Features.Feature(NOTIF_OVERFLOW_COUNT, "Show the hidden-notification count", "Replaces the small overflow dot in the status bar with \"+N\", the number of notification icons that did not fit."),
        new Features.Feature(NOTIF_ICON_LIMIT, "Notification icons in the status bar", "How many notification icons are shown before the overflow dot / count. \"As many as fit\" fills the free space.", Features.Type.CHOICE, ICON_LIMITS, 0),
        new Features.Feature(VOL_ONEUI, "One UI 8 volume panel", "The pop-up you get with the volume keys looks like Samsung's: each slider fills its whole pill, the fill is light and starts as a circle at the bottom, and the speaker / bell icon sits inside the fill (tap it to mute, as before). Works collapsed and expanded. Overrides the slider style below. Applies the next time the pop-up opens."),
        new Features.Feature(PWR_ONEUI, "One UI 8 power menu", "Samsung-style buttons in the power menu: Power off is a solid red circle, Restart a green one and the other buttons grey, all a little bigger (72 dp), with bold white names under them. RedMagic's blurred background and tap-twice confirmation stay. Overrides the button finish below. Applies the next time the menu opens."),
        new Features.Feature(VOL_STYLE, "Volume slider style", "Outline, gradient, neumorphic or layered look for the sliders of the volume pop-up. Applies the next time the pop-up opens.", Features.Type.CHOICE, SurfaceArt.VOLUME_STYLES, 0),
        new Features.Feature(PWR_STYLE, "Power menu button finish", "Decorative finish on the round Power and Restart buttons of the power menu. Applies the next time the menu opens.", Features.Type.CHOICE, SurfaceArt.CARD_STYLES, 0)
    );

    static {
        Previews.register(NOTIF_STYLE, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> SurfaceArt.paintNotificationPreview(c, i, w, h, d, 0, 0)), "Finish over two sample cards");
        Previews.register(NOTIF_OPACITY, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> SurfaceArt.paintNotificationPreview(c, 0, w, h, d, SurfaceArt.opacity(i), 0)));
        Previews.register(NOTIF_CORNERS, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> SurfaceArt.paintNotificationPreview(c, 0, w, h, d, 0, SurfaceArt.cornerDp(i))));
        Previews.register(NOTIF_ICON_LIMIT, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 40, (c, w, h, d) -> SurfaceArt.paintOverflowPreview(c, false, iconLimit(i), w, h, QsFeatures.accent(ctx), QsFeatures.dim(ctx))));
        Previews.register(VOL_STYLE, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 96, (c, w, h, d) -> SurfaceArt.paintVolumePreview(c, i, w, h, d)));
        Previews.register(PWR_STYLE, (ctx, i) -> new QsFeatures.Sketch(ctx, 120, 72, (c, w, h, d) -> SurfaceArt.paintPowerPreview(c, i, w, h, d)));
    }
}
