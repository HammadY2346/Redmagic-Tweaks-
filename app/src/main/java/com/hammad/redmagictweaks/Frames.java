package com.hammad.redmagictweaks;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Frame stores served to hooked processes through {@link LogProvider}:
 * <ul>
 *   <li>charging: built-in sets in assets/charging/&lt;set&gt;/NNN.webp, imported frames in files/charging/custom</li>
 *   <li>fingerprint: built-in sets in assets/fingerprint/&lt;set&gt;/NNN.webp (+ optional icon.webp),
 *       imported frames in files/fingerprint/custom (+ optional icon.*)</li>
 *   <li>font: an imported lock-screen clock font in files/fonts/lockclock.ttf</li>
 * </ul>
 */
public final class Frames {
    private Frames() {}

    public static final String CUSTOM = "custom";
    private static final String CHARGING = "charging";
    private static final String FINGERPRINT = "fingerprint";
    private static final String ICON_PREFIX = "icon.";
    private static final String FRAME_RE = "\\d{3}\\.(png|webp|jpg|jpeg)";
    private static final String IMAGE_RE = ".*\\.(png|webp|jpg|jpeg)";

    // ------------------------------------------------------------------ charging (unchanged behaviour)

    public static int assetCount(Context c, String set) { return assetCountOf(c, CHARGING, set); }

    public static String assetPath(Context c, String set, int index) { return assetPathOf(c, CHARGING, set, index); }

    public static File dir(Context c) { return dirOf(c, CHARGING); }

    public static File[] list(Context c) { return listOf(dir(c)); }

    public static int count(Context c) { return list(c).length; }

    public static File frame(Context c, int index) { return frameOf(list(c), index); }

    /** Copy every image in the chosen folder (sorted by name) into the charging store. Returns how many were imported. */
    public static int importFolder(Context c, Uri tree) { return importInto(c, tree, dir(c), false); }

    // ------------------------------------------------------------------ fingerprint

    public static int fpAssetCount(Context c, String set) { return assetCountOf(c, FINGERPRINT, set); }

    public static String fpAssetPath(Context c, String set, int index) { return assetPathOf(c, FINGERPRINT, set, index); }

    /** assets/fingerprint/&lt;set&gt;/icon.* or null when the set has no dedicated idle icon. */
    public static String fpAssetIcon(Context c, String set) {
        try {
            String[] l = c.getAssets().list(FINGERPRINT + "/" + set);
            if (l == null) return null;
            for (String n : l) if (n.startsWith(ICON_PREFIX)) return FINGERPRINT + "/" + set + "/" + n;
        } catch (Exception ignored) { }
        return null;
    }

    public static File fpDir(Context c) { return dirOf(c, FINGERPRINT); }

    public static File[] fpList(Context c) { return listOf(fpDir(c)); }

    public static int fpCount(Context c) { return fpList(c).length; }

    public static File fpFrame(Context c, int index) { return frameOf(fpList(c), index); }

    /** files/fingerprint/custom/icon.* (an optional "icon.png|webp|jpg" copied from the imported folder) or null. */
    public static File fpIcon(Context c) {
        File[] f = fpDir(c).listFiles((d, n) -> n.startsWith(ICON_PREFIX) && n.toLowerCase(Locale.ROOT).matches(IMAGE_RE));
        return f == null || f.length == 0 ? null : f[0];
    }

    /**
     * Copy every image in the chosen folder (sorted by name) into the fingerprint store; a file named
     * icon.png/webp/jpg becomes the idle icon. Returns how many animation frames were imported.
     */
    public static int fpImportFolder(Context c, Uri tree) { return importInto(c, tree, fpDir(c), true); }

    // ------------------------------------------------------------------ lock-screen clock font

    public static File fontFile(Context c) {
        File d = new File(c.getFilesDir(), "fonts");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return new File(d, "lockclock.ttf");
    }

    public static boolean hasFont(Context c) { return fontFile(c).length() > 0; }

    /** Copy a single .ttf/.otf document into the font store. Returns true on success. */
    public static boolean fontImport(Context c, Uri doc) {
        File out = fontFile(c);
        try (InputStream in = c.getContentResolver().openInputStream(doc); FileOutputStream o = new FileOutputStream(out)) {
            if (in == null) return false;
            copy(in, o);
            return out.length() > 0;
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return false;
        }
    }

    // ------------------------------------------------------------------ shared internals

    private static int assetCountOf(Context c, String kind, String set) {
        try {
            String[] l = c.getAssets().list(kind + "/" + set);
            if (l == null) return 0;
            int n = 0;
            for (String s : l) if (s.matches(FRAME_RE)) n++;
            return n;
        } catch (Exception e) { return 0; }
    }

    private static String assetPathOf(Context c, String kind, String set, int index) {
        try {
            String[] l = c.getAssets().list(kind + "/" + set);
            if (l == null || l.length == 0) return null;
            List<String> frames = new ArrayList<>();
            for (String s : l) if (s.matches(FRAME_RE)) frames.add(s);
            if (frames.isEmpty()) return null;
            java.util.Collections.sort(frames);
            return kind + "/" + set + "/" + frames.get(Math.floorMod(index, frames.size()));
        } catch (Exception e) { return null; }
    }

    private static File dirOf(Context c, String kind) {
        File d = new File(c.getFilesDir(), kind + "/" + CUSTOM);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static File[] listOf(File dir) {
        File[] f = dir.listFiles((d, n) -> n.matches(FRAME_RE));
        if (f == null) return new File[0];
        Arrays.sort(f);
        return f;
    }

    private static File frameOf(File[] f, int index) {
        if (f.length == 0) return null;
        return f[Math.floorMod(index, f.length)];
    }

    private static void copy(InputStream in, FileOutputStream o) throws java.io.IOException {
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
    }

    private static int importInto(Context c, Uri tree, File dir, boolean withIcon) {
        DocumentFile root = DocumentFile.fromTreeUri(c, tree);
        if (root == null) return 0;
        List<DocumentFile> files = new ArrayList<>();
        DocumentFile icon = null;
        for (DocumentFile f : root.listFiles()) {
            String n = f.getName();
            if (!f.isFile() || n == null) continue;
            String lower = n.toLowerCase(Locale.ROOT);
            if (!lower.matches(IMAGE_RE)) continue;
            if (withIcon && lower.startsWith(ICON_PREFIX)) { icon = f; continue; }
            files.add(f);
        }
        files.sort((a, b) -> String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName())));
        File[] old = dir.listFiles();
        if (old != null) for (File o : old) //noinspection ResultOfMethodCallIgnored
            o.delete();
        int n = 0;
        for (DocumentFile f : files) {
            File out = new File(dir, String.format(Locale.ROOT, "%03d.%s", n, ext(f)));
            if (copyDoc(c, f, out)) n++;
            if (n >= 400) break;
        }
        if (icon != null) copyDoc(c, icon, new File(dir, "icon." + ext(icon)));
        return n;
    }

    private static String ext(DocumentFile f) {
        String name = String.valueOf(f.getName());
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext.equals("jpeg") ? "jpg" : ext;
    }

    private static boolean copyDoc(Context c, DocumentFile f, File out) {
        try (InputStream in = c.getContentResolver().openInputStream(f.getUri()); FileOutputStream o = new FileOutputStream(out)) {
            if (in == null) return false;
            copy(in, o);
            return true;
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return false;
        }
    }
}
