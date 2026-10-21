package com.hammad.redmagictweaks;

import android.app.Activity;

/**
 * What one build flavour adds to the shared code. The public build (RedMagic Tweaks) adds nothing; the private build
 * of the author's own repo has its own copy of this class.
 */
public final class FlavorBits {
    private FlavorBits() {}

    /** Extra rows at the end of a section, or null. */
    public static Features.Feature[] extraFeatures(String sectionId) { return null; }

    /** Extra hooks for a hooked package. */
    public static void installHooks(String pkg, ClassLoader cl) { }

    /** Button text of an action row this flavour handles. */
    public static String actionLabel(String key) { return "Open"; }

    /** Runs an action row this flavour handles; false = not ours. */
    public static boolean runAction(Activity activity, String key) { return false; }
}
