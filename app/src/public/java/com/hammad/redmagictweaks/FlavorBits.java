package com.hammad.redmagictweaks;

import android.app.Activity;

/**
 * What one build flavour adds to the shared code. The public build (RedMagic Tweaks) adds no tweaks and leaves some out
 * (see leftOut, hiddenChoice); the private build of the author's own repo has its own copy of this class.
 */
public final class FlavorBits {
    private FlavorBits() {}

    /** Extra rows at the end of a section, or null. */
    public static Features.Feature[] extraFeatures(String sectionId) { return null; }

    /** Extra hooks for a hooked package. */
    public static void installHooks(String pkg, ClassLoader cl) { }

    /** Button text of an action row this flavour handles. */
    public static String actionLabel(android.content.Context c, String key) { return "Open"; }

    /** Runs an action row this flavour handles (onDone refreshes the rows); false = not ours. */
    public static boolean runAction(Activity activity, String key, Runnable onDone) { return false; }

    /** The app's own icon (About). */
    public static int appIconRes(android.content.Context c) { return R.mipmap.ic_launcher; }

    /** The public build (RedMagic Tweaks). */
    public static boolean publicBuild() { return true; }

    /** China-only keys of this build's own tweaks (Region): none. */
    public static java.util.List<String> cnOnlyKeys() { return java.util.Collections.emptyList(); }

    /**
     * Tweaks the public build leaves out. They stay in RM Tweaks. Hidden rows read as off in the hooks too (Cfg).
     *  - 22 Oct 2026: risky for people who don't know what they do, or untested outside A's own phone.
     *  - 23 Oct 2026, v1.1: the One UI layouts and everything added after v1.0 went out.
     *  - 5 Nov 2026, v1.2 (A): they come back: the One UI layouts, frame interpolation for every game and its target,
     *    AOD clock position, battery health, charge separation at 100 %, Google international mode, game full screen,
     *    game view, launcher folder grid, hidden labels, icon pack (also from a zip), icon shape, label and icon size,
     *    lock screen video and AOD video, then (A) every plugin in every game, launch boost, Game Assist defer, Game Lab
     *    AI speed, the CN charging sets and the icon presets. Still out: the risky ones below.
     */
    public static boolean leftOut(String key) {
        if (key == null) return false;
        switch (key) {
            case "sb_usb_debug_auto_allow":   // anyone with a cable gets a shell
            case "fw_untrusted_touch":        // overlays can trick taps again
            case "fw_no_72h_lock":            // weakens the lock screen
            case "fw_volume_boost":           // can damage speakers
                return true;
            default:
                return false;
        }
    }

    /** v1.2: no option is hidden for being a One UI look any more. */
    private static final java.util.Set<String> HIDDEN_LABELS = java.util.Collections.emptySet();

    /** True: option {@code i} of setting {@code key} is not offered (and reads as stock in the hooks). */
    public static boolean hiddenChoice(String key, int i, String label) {
        if (i <= 0 || label == null) return false;
        if ("launcher_folder_shape".equals(key)) return false;            // its "One UI" is a plain shape (renamed)
        // v1.2 (A): the CN charging sets (cn_*) ship in the public build too
        return HIDDEN_LABELS.contains(label);
    }

    /** The option names shown: as they are (v1.1 renamed the One UI ones; v1.2 has the One UI looks back). */
    public static String[] labels(String key, String[] choices) { return choices; }

    /** Descriptions as they are. */
    public static String desc(String key, String desc) { return desc; }

    /** Subtitles as they are. */
    public static String categorySubtitle(String id, String subtitle) { return subtitle; }

    /** Nothing to clean up on start (v1.1 reset the One UI layouts once; v1.2 offers them again). */
    public static void onStart(final Activity a) { }

    /** True: China-only tweaks are left out (the public build works the same on China and global firmware). */
    public static boolean globalOnly() { return true; }

    /** A short About page {repository link, "made and tested on" line, credited name}, or null for the full one. */
    public static String[] shortAbout() { return null; }
}
