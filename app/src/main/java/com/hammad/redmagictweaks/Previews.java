package com.hammad.redmagictweaks;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * Visual previews for CHOICE features. A feature that registers a provider is shown as a picture grid
 * (ui/OptionSheet) instead of a plain radio list: each card shows the drawable for that index, tapping a
 * card opens a large preview with "Use". Providers may return null for an index (card shows the label only)
 * and must be cheap enough to run on the UI thread for ~30 items, or do their own caching.
 *
 * Register from the static initializer of the *Features class that owns the key:
 *   static { Previews.register(MY_KEY, (ctx, i) -> ...); }
 * Helpers below cover the common sources: an app resource, a drawable from another installed package
 * (System UI's own battery / charging artwork), or an asset / imported frame.
 */
public final class Previews {
    private Previews() {}

    public interface Provider { Drawable get(Context ctx, int index); }

    private static final Map<String, Provider> MAP = new HashMap<>();
    /** Optional per-key subtitle shown under the grid title, e.g. "Tap to preview · plays on the lock screen". */
    private static final Map<String, String> HINTS = new HashMap<>();
    /** Optional per-key animated preview: index -> frame drawables, for the large preview dialog. */
    private static final Map<String, FrameProvider> FRAMES = new HashMap<>();

    public interface FrameProvider { /** @return frames in order, or null if the index is not animated */ Drawable[] get(Context ctx, int index); }

    public static void register(String key, Provider p) { MAP.put(key, p); }
    public static void register(String key, Provider p, String hint) { MAP.put(key, p); HINTS.put(key, hint); }
    public static void registerFrames(String key, FrameProvider f) { FRAMES.put(key, f); }

    public static Provider of(String key) { return MAP.get(key); }
    public static String hint(String key) { return HINTS.get(key); }
    public static FrameProvider frames(String key) { return FRAMES.get(key); }
    public static boolean has(String key) { return MAP.containsKey(key); }

    // ---- helpers -------------------------------------------------------------------------------

    /** A drawable from this app's resources; 0 → null. */
    public static Drawable res(Context ctx, int id) {
        if (id == 0) return null;
        try { return ctx.getDrawable(id); } catch (Throwable t) { return null; }
    }

    /** A drawable from another installed package by resource name (e.g. System UI's "stat_sys_battery_full"). */
    public static Drawable fromPackage(Context ctx, String pkg, String name) {
        if (name == null) return null;
        try {
            android.content.res.Resources r = ctx.getPackageManager().getResourcesForApplication(pkg);
            int id = r.getIdentifier(name, "drawable", pkg);
            if (id == 0) id = r.getIdentifier(name, "mipmap", pkg);
            return id == 0 ? null : r.getDrawable(id, r.newTheme());
        } catch (Throwable t) { return null; }
    }

    /** First image file of an asset folder (used for the built-in charging / fingerprint frame sets). */
    public static Drawable asset(Context ctx, String path) {
        try (java.io.InputStream in = ctx.getAssets().open(path)) {
            return Drawable.createFromStream(in, path);
        } catch (Throwable t) { return null; }
    }

    public static Drawable file(java.io.File f) {
        if (f == null || !f.isFile()) return null;
        try { return Drawable.createFromPath(f.getAbsolutePath()); } catch (Throwable t) { return null; }
    }
}
