package com.hammad.redmagictweaks;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An icon pack from a zip file instead of an installed icon-pack app (Features.LAUNCHER_ICON_PACK = "zip:&lt;version&gt;").
 * Picked in the app (ui/IconPackPicker), copied into the app's files, served read-only to the launcher by LockVideoProvider
 * (content://…lockvideo/iconzip) and read by xposed/LauncherIcons.
 *
 * Accepted layouts (searched in every folder of the zip):
 * <ul>
 *   <li>an ADW / Nova pack's files: appfilter.xml plus the images it names (drawable-xxhdpi/whatsapp.png …)</li>
 *   <li>images named after the app: com.whatsapp.png, or com.android.chrome#com.google.android.apps.chrome.Main.png
 *       for one activity (Xiaomi theme style)</li>
 *   <li>ZTE / RedMagic theme names: com_android_chrome.png, com_android_chrome-com_google_android_apps_chrome_Main.png
 *       (dots as underscores, app and activity joined by "-")</li>
 *   <li>a Xiaomi theme (.mtz) or any zip holding such a pack as an inner zip named "icons", or a RedMagic theme (.zmtp)
 *       with overlays/com.zte.mifavor.launcher.resource/raw/icons_cur.zip: that inner zip is used</li>
 * </ul>
 * Built-in presets (private build: assets/iconpacks/*.zip) go through the same files.
 * Optional iconback / iconmask / iconupon images give apps without their own icon the pack's look.
 */
public final class IconZip {
    private IconZip() {}

    public static final long MAX_BYTES = 300L * 1024 * 1024;
    public static final String PREFIX = "zip:";

    public static final class Meta {
        public long version;
        public int icons;
        public boolean appfilter;
        public String name;
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "iconzip");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }
    public static File pack(Context c) { return new File(dir(c), "pack.zip"); }
    private static File metaFile(Context c) { return new File(dir(c), "pack.json"); }

    public static boolean isZipValue(String v) { return v != null && v.startsWith(PREFIX); }

    public static synchronized Meta meta(Context c) {
        File f = metaFile(c);
        if (!f.isFile() || pack(c).length() == 0) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 8192)];
            int n = in.read(b);
            JSONObject o = new JSONObject(new String(b, 0, Math.max(0, n), "UTF-8"));
            Meta m = new Meta();
            m.version = o.optLong("version");
            m.icons = o.optInt("icons");
            m.appfilter = o.optBoolean("appfilter");
            m.name = o.optString("name", "icon zip");
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    /** "Icon zip: name (N icons)" for the picker and the row value. */
    public static String label(Context c) {
        Meta m = meta(c);
        if (m == null) return "Icon zip";
        return String.format(Locale.US, "%s (%d icons)", m.name, m.icons);
    }

    /** Copies the zip in, unwraps an inner "icons" zip, checks it holds icons. Returns null on success, else why not. */
    public static synchronized String importFrom(Context c, Uri uri) {
        File tmp = new File(dir(c), "import.tmp"), inner = new File(dir(c), "inner.tmp");
        try {
            String name = displayName(c, uri);
            long copied;
            try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) return "Couldn't open that file.";
                copied = copy(in, out, MAX_BYTES);
            }
            if (copied < 0) return "That file is bigger than 300 MB.";
            File use = tmp;
            Scan s;
            try (ZipFile z = new ZipFile(tmp)) {
                s = scan(z);
                if (s.icons == 0) {
                    // Xiaomi theme (.mtz): the icons live in an inner zip called "icons"
                    ZipEntry e = z.getEntry("icons");
                    if (e == null || e.isDirectory()) e = innerIconsCur(z);   // RedMagic theme (.zmtp)
                    if (e != null && !e.isDirectory()) {
                        try (InputStream in = z.getInputStream(e); OutputStream out = new FileOutputStream(inner)) { copy(in, out, MAX_BYTES); }
                        try (ZipFile z2 = new ZipFile(inner)) { s = scan(z2); }
                        use = inner;
                    }
                }
            } catch (java.util.zip.ZipException ze) {
                return "That isn't a zip file.";
            }
            if (s.icons == 0) return "No app icons found in that zip. It needs an appfilter.xml with its images, or images named after apps (for example com.whatsapp.png).";
            File dst = pack(c);
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            if (!use.renameTo(dst)) return "Couldn't save the icons.";
            JSONObject o = new JSONObject();
            long version = System.currentTimeMillis();
            o.put("version", version);
            o.put("icons", s.icons);
            o.put("appfilter", s.appfilter);
            o.put("name", name == null ? "icon zip" : name.replaceAll("(?i)\\.(zip|mtz)$", ""));
            try (OutputStream out = new FileOutputStream(metaFile(c))) { out.write(o.toString().getBytes("UTF-8")); }
            Prefs.setStr(c, Features.LAUNCHER_ICON_PACK, PREFIX + version);
            return null;
        } catch (Exception e) {
            return "Import failed: " + e.getMessage();
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            //noinspection ResultOfMethodCallIgnored
            inner.delete();
        }
    }

    public static synchronized void remove(Context c) {
        //noinspection ResultOfMethodCallIgnored
        pack(c).delete();
        //noinspection ResultOfMethodCallIgnored
        metaFile(c).delete();
        if (isZipValue(Prefs.str(c, Features.LAUNCHER_ICON_PACK))) Prefs.setStr(c, Features.LAUNCHER_ICON_PACK, null);
    }

    private static ZipEntry innerIconsCur(ZipFile z) {
        Enumeration<? extends ZipEntry> en = z.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (!e.isDirectory() && e.getName().endsWith("/icons_cur.zip")) return e;
        }
        return null;
    }

    // ------------------------------------------------------------------ built-in presets (assets/iconpacks/<id>.zip)

    /** Built-in icon presets: asset id -> name shown in the picker. Empty in the public build (no assets). */
    public static java.util.Map<String, String> presets(Context c) {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        try {
            String[] list = c.getAssets().list("iconpacks");
            if (list != null) for (String f : list) if (f.endsWith(".zip")) {
                String id = f.substring(0, f.length() - 4);
                out.put(id, PRESET_NAMES.containsKey(id) ? PRESET_NAMES.get(id) : id);
            }
        } catch (Exception ignored) { }
        return out;
    }
    private static final java.util.Map<String, String> PRESET_NAMES = new java.util.HashMap<>();
    static {
        PRESET_NAMES.put("oneui8_square", "One UI 8 square icons (A's RedMagic theme)");
        PRESET_NAMES.put("oneui85_restyled", "ONE UI 8.5 Restyled (A's RedMagic theme)");
    }

    /** Uses a built-in preset as the icon zip. Returns null on success, else why not. Runs off the UI thread. */
    public static synchronized String applyPreset(Context c, String id) {
        File tmp = new File(dir(c), "import.tmp");
        try (InputStream in = c.getAssets().open("iconpacks/" + id + ".zip"); OutputStream out = new FileOutputStream(tmp)) {
            copy(in, out, MAX_BYTES);
        } catch (Exception e) {
            return "Couldn't read the preset: " + e.getMessage();
        }
        int icons;
        try (ZipFile z = new ZipFile(tmp)) { icons = scan(z).icons; } catch (Exception e) { return "The preset is damaged."; }
        File dst = pack(c);
        //noinspection ResultOfMethodCallIgnored
        dst.delete();
        if (!tmp.renameTo(dst)) return "Couldn't save the icons.";
        try {
            JSONObject o = new JSONObject();
            long version = System.currentTimeMillis();
            o.put("version", version);
            o.put("icons", icons);
            o.put("appfilter", false);
            String name = presets(c).get(id);
            o.put("name", name == null ? id : name);
            o.put("preset", id);
            try (OutputStream out = new FileOutputStream(metaFile(c))) { out.write(o.toString().getBytes("UTF-8")); }
            Prefs.setStr(c, Features.LAUNCHER_ICON_PACK, PREFIX + version);
            return null;
        } catch (Exception e) {
            return "Couldn't save the icons: " + e.getMessage();
        }
    }

    /** The preset id the current icon zip came from, or null. */
    public static String presetOf(Context c) {
        File f = metaFile(c);
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 8192)];
            int n = in.read(b);
            String p = new JSONObject(new String(b, 0, Math.max(0, n), "UTF-8")).optString("preset", "");
            return p.isEmpty() ? null : p;
        } catch (Exception e) { return null; }
    }

    private static final class Scan { int icons; boolean appfilter; }

    /** Counts the icons the launcher side (xposed/LauncherIcons.ZipPack) will be able to use. */
    private static Scan scan(ZipFile z) {
        Scan s = new Scan();
        java.util.Set<String> images = new java.util.HashSet<>();
        int byName = 0;
        Enumeration<? extends ZipEntry> en = z.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            String n = e.getName(), base = n.substring(n.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            if (base.equals("appfilter.xml")) { s.appfilter = true; continue; }
            if (!(base.endsWith(".png") || base.endsWith(".webp") || base.endsWith(".jpg"))) continue;
            String stem = base.substring(0, base.lastIndexOf('.'));
            images.add(stem);
            if (stem.indexOf('.') > 0) byName++;            // com.whatsapp / com.android.chrome#…
            else if (stem.indexOf('_') > 0 && n.toLowerCase(Locale.ROOT).contains("icon")) byName++;   // ZTE: com_whatsapp
        }
        s.icons = s.appfilter ? images.size() : byName;
        return s;
    }

    private static long copy(InputStream in, OutputStream out, long max) throws java.io.IOException {
        byte[] b = new byte[64 * 1024];
        long total = 0;
        for (int n; (n = in.read(b)) > 0; ) {
            total += n;
            if (total > max) return -1;
            out.write(b, 0, n);
        }
        return total;
    }

    private static String displayName(Context c, Uri uri) {
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Throwable ignored) { }
        return null;
    }
}
