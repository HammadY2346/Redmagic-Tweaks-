package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;

import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.FingerprintCustomFeatures;
import com.hammad.redmagictweaks.FpArtwork;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Fingerprint art inside hooked processes: reads the settings, loads RisingOS atlases / icons from the module APK
 * (XModuleResources, like IconPackHooks) and frame-store sets from the module app's ContentProvider
 * (content://&lt;app id&gt;.log/fingerprint/&lt;set&gt;/&lt;index|icon&gt;), and builds the idle-icon drawable.
 */
final class FpArt {
    private FpArt() {}

    static final String PROVIDER = "content://" + com.hammad.redmagictweaks.LogProvider.AUTHORITY;
    /** Frame-store sets are capped here (each frame is kept at source size, ~360 KB for 300 px). */
    static final int MAX_FRAMES = 120;

    // ------------------------------------------------------------------------------------------ settings

    static String set() {
        int i = Cfg.choice(FingerprintCustomFeatures.FP_ANIM_SET);
        if (i < 0 || i >= FingerprintCustomFeatures.ANIM_SET_DEFS.length) i = 0;
        return FingerprintCustomFeatures.ANIM_SET_DEFS[i][1];
    }

    /** Milliseconds per frame for frame-store sets; 0 = stretch over the stock animation. */
    static int intervalMs() {
        int i = Cfg.choice(FingerprintCustomFeatures.FP_ANIM_INTERVAL);
        if (i < 0 || i >= FingerprintCustomFeatures.INTERVAL_MS.length) i = 0;
        return Math.max(0, Cfg.num(FingerprintCustomFeatures.FP_ANIM_INTERVAL, FingerprintCustomFeatures.INTERVAL_MS[i]));
    }

    static int animSizePercent() {
        int i = Cfg.choice(FingerprintCustomFeatures.FP_ANIM_SIZE);
        if (i < 0 || i >= FingerprintCustomFeatures.SIZE_PERCENT.length) i = 0;
        return Math.max(10, Cfg.num(FingerprintCustomFeatures.FP_ANIM_SIZE, FingerprintCustomFeatures.SIZE_PERCENT[i]));
    }

    static int iconStyle() {
        int i = Cfg.choice(FingerprintCustomFeatures.FP_ICON_STYLE);
        return i < 0 || i >= FingerprintCustomFeatures.ICON_STYLES.length ? 0 : i;
    }

    static int iconSizePercent() {
        int i = Cfg.choice(FingerprintCustomFeatures.FP_ICON_SIZE);
        if (i < 0 || i >= FingerprintCustomFeatures.ICON_SIZE_PERCENT.length) i = 0;
        return Math.max(10, Cfg.num(FingerprintCustomFeatures.FP_ICON_SIZE, FingerprintCustomFeatures.ICON_SIZE_PERCENT[i]));
    }

    static boolean iconHidden() { return Cfg.on(FingerprintCustomFeatures.FP_ICON_HIDE); }

    private static int pick(String key, int[] values) {
        int i = Cfg.choice(key);
        return Cfg.num(key, values[i > 0 && i < values.length ? i : 0]);
    }

    private static final java.util.Map<android.view.View, Boolean> placed = new java.util.WeakHashMap<>();

    /**
     * Opacity and nudge of the idle fingerprint symbol (stock or custom), on the lock screen sensor view and the PIN
     * screen icon. The image alpha (and the background's, where custom icons go) is used, not the view's, so System UI's
     * own fades keep working. Stock values leave a view alone unless it was changed before.
     */
    static void place(android.widget.ImageView v) {
        if (v == null) return;
        int opacity = Math.max(0, Math.min(100, pick(FingerprintCustomFeatures.FP_ICON_OPACITY, FingerprintCustomFeatures.ICON_OPACITY_PERCENT)));
        int dx = pick(FingerprintCustomFeatures.FP_ICON_DX, FingerprintCustomFeatures.ICON_NUDGE_DP);
        int dy = pick(FingerprintCustomFeatures.FP_ICON_DY, FingerprintCustomFeatures.ICON_NUDGE_DP);
        boolean stock = opacity == 100 && dx == 0 && dy == 0;
        if (stock && placed.remove(v) == null) return;
        int a = Math.round(opacity * 255 / 100f);
        v.setImageAlpha(a);
        android.graphics.drawable.Drawable bg = v.getBackground();
        if (bg != null) bg.mutate().setAlpha(a);
        float d = v.getResources().getDisplayMetrics().density;
        v.setTranslationX(dx * d);
        v.setTranslationY(dy * d);
        if (!stock) placed.put(v, Boolean.TRUE);
    }

    /** True when the idle icon should be touched at all (hidden or replaced). */
    static boolean iconActive() { return iconHidden() || Cfg.on(FingerprintCustomFeatures.FP_ICON); }

    /** Colour for the code-drawn glyphs: the configured one, or the keyguard colour when "follow lock screen". */
    static int iconColor(int keyguardColor) {
        int[] cc = Cfg.color(FingerprintCustomFeatures.FP_ICON_COLOR);
        if (cc != null) return cc[0];
        int i = Cfg.choice(FingerprintCustomFeatures.FP_ICON_COLOR);
        if (i > 0 && i < FingerprintCustomFeatures.ICON_COLOR_ARGB.length) return FingerprintCustomFeatures.ICON_COLOR_ARGB[i];
        return (keyguardColor >>> 24) == 0 ? 0xffffffff : keyguardColor;
    }

    /** Something that changes whenever the animation configuration changes (decides when frames are reloaded). */
    static String animSignature() {
        return Cfg.on(FingerprintCustomFeatures.FP_ANIM) + "|" + set() + "|" + intervalMs();
    }

    // ------------------------------------------------------------------------------------------ module resources

    private static Resources modRes;

    static synchronized Resources res() {
        if (modRes != null) return modRes;
        try { if (Entry.modulePath != null) modRes = XModuleResources.createInstance(Entry.modulePath, null); } catch (Throwable t) { Lx.w("fingerprint: module resources via path failed: " + t); }
        if (modRes == null) {
            try { Context c = Lx.context(); if (c != null) modRes = c.getPackageManager().getResourcesForApplication(Features.PKG); } catch (Throwable t) { Lx.w("fingerprint: module resources via PackageManager failed: " + t); }
        }
        return modRes;
    }

    /** A fresh drawable from our APK by name, or null (logged). */
    static Drawable art(String name) {
        Resources r = res();
        if (r == null || name == null || name.isEmpty()) return null;
        try {
            int id = r.getIdentifier(name, "drawable", Features.PKG);
            if (id == 0) { Lx.w("fingerprint: no artwork " + name); return null; }
            Drawable d = r.getDrawable(id, null);
            return d == null ? null : d.mutate();
        } catch (Throwable t) { Lx.w("fingerprint: artwork " + name + " failed: " + t); return null; }
    }

    // ------------------------------------------------------------------------------------------ frame store (provider)

    /** {count, hasIcon} for a frame-store set, asked from the module app; {0, 0} when unreachable. */
    static int[] frameInfo(Context c, String set) {
        try {
            Bundle b = c.getContentResolver().call(Uri.parse(PROVIDER), "fp_frame_count", set, null);
            if (b == null) return new int[]{0, 0};
            return new int[]{b.getInt("count", 0), b.getInt("icon", 0)};
        } catch (Throwable t) {
            Lx.w("fingerprint: frame count unavailable " + t);
            return new int[]{0, 0};
        }
    }

    static Bitmap decode(Context c, String set, String what) {
        try (InputStream in = c.getContentResolver().openInputStream(Uri.parse(PROVIDER + "/fingerprint/" + set + "/" + what))) {
            if (in == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            o.inScaled = false;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (b != null && Math.max(b.getWidth(), b.getHeight()) > 640) {
                float s = 640f / Math.max(b.getWidth(), b.getHeight());
                Bitmap small = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)), Math.max(1, Math.round(b.getHeight() * s)), true);
                if (small != b) b.recycle();
                b = small;
            }
            return b;
        } catch (Throwable t) {
            Lx.w("fingerprint: cannot read " + set + "/" + what + ": " + t);
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------ animation clip

    /**
     * The frames of the selected set, ready for drawing: either one shared atlas with tile rectangles or a list of
     * individual bitmaps. {@code frameFor(index, count, durationMs)} maps the stock animation's progress to one of
     * them (nubia-ly FingerprintAnimations.mappedFrame for atlases; frame timing for frame stores).
     */
    static final class Clip {
        final String signature;
        final Bitmap atlas;             // atlas sets
        final List<Bitmap> frames;      // frame-store sets
        final int count, intervalMs;

        Clip(String signature, Bitmap atlas, int atlasCount, List<Bitmap> frames, int intervalMs) {
            this.signature = signature; this.atlas = atlas; this.frames = frames; this.intervalMs = intervalMs;
            this.count = atlas != null ? atlasCount : frames == null ? 0 : frames.size();
        }

        boolean empty() { return count <= 0 || (atlas == null && (frames == null || frames.isEmpty())); }

        /** Index of our frame for stock frame {@code index} of {@code total} drawn every {@code durationMs}. */
        int frameFor(int index, int total, int durationMs) {
            if (count <= 0) return -1;
            if (atlas != null || intervalMs <= 0) {
                if (total <= 0) return 0;
                int f = (int) ((long) Math.max(0, index) * count / total);
                return Math.min(count - 1, Math.max(0, f));
            }
            long elapsed = (long) Math.max(0, index) * Math.max(1, durationMs);
            return (int) ((elapsed / intervalMs) % count);
        }

        Bitmap bitmap(int frame) { return atlas != null ? atlas : frames.get(frame); }

        Rect source(int frame) {
            if (atlas != null) return FpArtwork.tile(frame);
            Bitmap b = frames.get(frame);
            return new Rect(0, 0, b.getWidth(), b.getHeight());
        }

        void recycle() {
            try {
                if (atlas != null && !atlas.isRecycled()) atlas.recycle();
                if (frames != null) for (Bitmap b : frames) if (b != null && !b.isRecycled()) b.recycle();
            } catch (Throwable ignored) { }
        }
    }

    private static Clip clip;

    /** The clip for the current settings, loading it when they changed (call on one thread only; null when off / empty). */
    static synchronized Clip clip(Context c) {
        boolean on = Cfg.on(FingerprintCustomFeatures.FP_ANIM);
        String want = on ? animSignature() : "";
        if (clip != null && clip.signature.equals(want)) return clip.empty() ? null : clip;
        Clip old = clip;
        clip = null;
        if (old != null) old.recycle();
        if (!on) return null;
        long t0 = System.currentTimeMillis();
        Clip n = load(c, want);
        clip = n;
        if (n.empty()) { Lx.w("fingerprint: set " + set() + " has no frames (import a folder or pick another set)"); return null; }
        Lx.i("fingerprint: " + n.count + " frames ready for " + set() + " in " + (System.currentTimeMillis() - t0) + " ms");
        return n;
    }

    static synchronized void drop() { Clip old = clip; clip = null; if (old != null) old.recycle(); }

    private static Clip load(Context c, String signature) {
        String set = set();
        int atlasId = FingerprintCustomFeatures.atlasId(set);
        if (atlasId > 0) {
            Bitmap b = FpArtwork.decodeAtlas(res(), Features.PKG, atlasId);
            if (b == null) Lx.w("fingerprint: atlas rising_anim_atlas_" + atlasId + " missing or malformed");
            return new Clip(signature, b, b == null ? 0 : FpArtwork.atlasFrames(atlasId), null, 0);
        }
        List<Bitmap> frames = new ArrayList<>();
        int n = frameInfo(c, set)[0];
        if (n > MAX_FRAMES) { Lx.w("fingerprint: set " + set + " has " + n + " frames, using the first " + MAX_FRAMES); n = MAX_FRAMES; }
        for (int i = 0; i < n; i++) {
            Bitmap b = decode(c, set, String.valueOf(i));
            if (b == null) break;
            frames.add(b);
        }
        return new Clip(signature, null, 0, frames, intervalMs());
    }

    // ------------------------------------------------------------------------------------------ idle icon

    /** True when building the icon needs the provider (must not run on the main thread). */
    static boolean iconNeedsProvider() {
        int s = iconStyle();
        return !iconHidden() && (s == FingerprintCustomFeatures.ICON_SET_ICON || s == FingerprintCustomFeatures.ICON_SET_FRAME)
            && FingerprintCustomFeatures.atlasId(set()) == 0;
    }

    /** The idle icon picture for the frame-set styles, or null. */
    private static Bitmap setBitmap(Context c) {
        int style = iconStyle();
        String set = set();
        int atlasId = FingerprintCustomFeatures.atlasId(set);
        if (atlasId > 0) {
            Bitmap atlas = FpArtwork.decodeAtlas(res(), Features.PKG, atlasId);
            if (atlas == null) return null;
            Bitmap tile = Bitmap.createBitmap(atlas, 0, 0, FpArtwork.TILE, FpArtwork.TILE);
            if (tile != atlas) atlas.recycle();
            return tile;
        }
        if (style == FingerprintCustomFeatures.ICON_SET_ICON) {
            int[] info = frameInfo(c, set);
            return decode(c, set, info[1] > 0 ? "icon" : "0");
        }
        return decode(c, set, "0");
    }

    /**
     * Build the idle-icon drawable for the current settings, or null when the icon is hidden.
     * {@code keyguardColor} only matters for the code-drawn styles.
     */
    static Drawable icon(Context c, int keyguardColor) {
        if (iconHidden()) return null;
        int style = iconStyle();
        Drawable artwork = null;
        Bitmap bmp = null;
        if (style >= FingerprintCustomFeatures.ICON_FIRST_ART) {
            artwork = art(FingerprintCustomFeatures.iconResource(style));
            if (artwork == null) { Lx.w("fingerprint: icon artwork unavailable, drawing rings instead"); style = 0; }
        } else if (style == FingerprintCustomFeatures.ICON_SET_ICON || style == FingerprintCustomFeatures.ICON_SET_FRAME) {
            bmp = setBitmap(c);
            if (bmp == null) { Lx.w("fingerprint: no icon picture in set " + set() + " — drawing rings instead"); style = 0; }
        }
        return new FpArtwork.Glyph(style, iconColor(keyguardColor), artwork, bmp, iconSizePercent());
    }
}
