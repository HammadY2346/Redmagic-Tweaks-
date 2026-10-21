package com.hammad.redmagictweaks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Boot animation changer (nubia-ly's feature, done RM Tweaks' way): the chosen archive is staged in
 * /data/adb/rmtweaks/boot and a small helper module (assets/bootmodule, installed by the app as
 * /data/adb/modules/rmtweaks_bootanim) bind-mounts it over the stock bootanimation.zip at every boot. The original
 * file is never written; two boots in a row that don't finish switch back to stock.
 */
public final class BootAnim {
    private BootAnim() {}

    /** Per app (RM Tweaks and RedMagic Tweaks keep their own), see BuildConfig.DATA_ID. */
    public static final String ROOT_DIR = "/data/adb/" + BuildConfig.DATA_ID;
    public static final String DIR = ROOT_DIR + "/boot";
    public static final String MODULE = "/data/adb/modules/" + BuildConfig.DATA_ID + "_bootanim";

    /** Archive of an entry: bundled (assets/bootanimations/<id>.zip), imported (files/bootanim/custom.zip) or the stock one. */
    public static boolean available(Context ctx, int index) {
        String id = BootAnimFeatures.id(index);
        if ("stock".equals(id)) return true;
        if ("custom".equals(id)) return customFile(ctx).isFile();
        try (InputStream in = ctx.getAssets().open("bootanimations/" + id + ".zip")) { return true; } catch (Throwable t) { return false; }
    }

    public static File customFile(Context ctx) { return new File(new File(ctx.getFilesDir(), "bootanim"), "custom.zip"); }

    /** The archive the stock player shows (what the helper module covers). */
    public static File stockFile() {
        String v = sysProp("persist.vendor.custom.variant.id");
        if (v != null && v.matches("[A-Za-z0-9_-]{1,32}")) {
            File f = new File("/system/media/" + v + "/bootanimation.zip");
            if (f.isFile()) return f;
        }
        return new File("/system/media/bootanimation.zip");
    }

    static String sysProp(String k) {
        try { return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, k); }
        catch (Throwable t) { return null; }
    }

    // ------------------------------------------------------------------ previews
    public static Drawable thumb(Context ctx, int index) {
        String id = BootAnimFeatures.id(index);
        if ("stock".equals(id)) return zipFrame(ctx, stockFile(), 0.5f);
        if ("custom".equals(id)) return zipFrame(ctx, customFile(ctx), 0.5f);
        return Previews.asset(ctx, "bootanimations/" + id + "-thumb.webp");
    }

    public static Drawable[] frames(Context ctx, int index) {
        String id = BootAnimFeatures.id(index);
        if ("stock".equals(id)) return zipFrames(ctx, stockFile(), 36);
        if ("custom".equals(id)) return zipFrames(ctx, customFile(ctx), 36);
        List<Drawable> out = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Drawable d = Previews.asset(ctx, String.format(java.util.Locale.US, "bootanimations/%s/%02d.webp", id, i));
            if (d == null) break;
            out.add(d);
        }
        return out.isEmpty() ? null : out.toArray(new Drawable[0]);
    }

    private static List<ZipEntry> imageEntries(ZipFile z) {
        List<ZipEntry> l = new ArrayList<>();
        for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
            ZipEntry ze = e.nextElement();
            String n = ze.getName().toLowerCase(java.util.Locale.ROOT);
            if (!ze.isDirectory() && n.startsWith("part") && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp"))) l.add(ze);
        }
        Collections.sort(l, (a, b) -> a.getName().compareTo(b.getName()));
        return l;
    }

    private static Drawable decode(Context ctx, ZipFile z, ZipEntry ze) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = z.getInputStream(ze)) { BitmapFactory.decodeStream(in, null, o); }
            int s = 1;
            while (o.outWidth / (s * 2) >= 270) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap b;
            try (InputStream in = z.getInputStream(ze)) { b = BitmapFactory.decodeStream(in, null, o2); }
            return b == null ? null : new BitmapDrawable(ctx.getResources(), b);
        } catch (Throwable t) { return null; }
    }

    static Drawable zipFrame(Context ctx, File f, float at) {
        if (f == null || !f.isFile()) return null;
        try (ZipFile z = new ZipFile(f)) {
            List<ZipEntry> l = imageEntries(z);
            return l.isEmpty() ? null : decode(ctx, z, l.get(Math.min(l.size() - 1, (int) (l.size() * at))));
        } catch (Throwable t) { return null; }
    }

    static Drawable[] zipFrames(Context ctx, File f, int max) {
        if (f == null || !f.isFile()) return null;
        try (ZipFile z = new ZipFile(f)) {
            List<ZipEntry> l = imageEntries(z);
            if (l.isEmpty()) return null;
            int step = Math.max(1, (l.size() + max - 1) / max);
            List<Drawable> out = new ArrayList<>();
            for (int i = 0; i < l.size(); i += step) { Drawable d = decode(ctx, z, l.get(i)); if (d != null) out.add(d); }
            return out.isEmpty() ? null : out.toArray(new Drawable[0]);
        } catch (Throwable t) { return null; }
    }

    // ------------------------------------------------------------------ import
    /** A user archive: must be a zip with desc.txt and part folders of frames. */
    public static String importZip(Context ctx, Uri uri) {
        File dir = customFile(ctx).getParentFile();
        dir.mkdirs();
        File tmp = new File(dir, "import.tmp");
        try (InputStream in = ctx.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
            byte[] b = new byte[65536]; long total = 0; int n;
            while ((n = in.read(b)) > 0) { total += n; if (total > 300L * 1024 * 1024) throw new java.io.IOException("larger than 300 MB"); out.write(b, 0, n); }
        } catch (Throwable t) { tmp.delete(); return "Could not read that file: " + t.getMessage(); }
        try (ZipFile z = new ZipFile(tmp)) {
            ZipEntry desc = z.getEntry("desc.txt");
            if (desc == null) throw new java.io.IOException("no desc.txt inside (not a boot animation)");
            if (imageEntries(z).isEmpty()) throw new java.io.IOException("no part*/ frames inside");
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                ZipEntry ze = e.nextElement();
                if (!ze.isDirectory() && ze.getMethod() != ZipEntry.STORED)
                    throw new java.io.IOException("files inside must be stored uncompressed (zip -0); \"" + ze.getName() + "\" is compressed, the boot player can't read it");
            }
        } catch (Throwable t) { tmp.delete(); return "Not a usable boot animation: " + t.getMessage(); }
        File dst = customFile(ctx);
        dst.delete();
        if (!tmp.renameTo(dst)) return "Could not save the file";
        return null;
    }

    // ------------------------------------------------------------------ apply / status (root)
    /** Stage entry `index` for the next boot (stock = helper does nothing). Returns a message for the user. */
    public static String apply(Context ctx, int index) {
        String id = BootAnimFeatures.id(index);
        File stage = new File(ctx.getCacheDir(), "bootanim");
        deleteTree(stage);
        stage.mkdirs();
        try {
            for (String f : new String[]{"module.prop", "post-fs-data.sh", "service.sh", "uninstall.sh"}) copyModuleFile(ctx, "bootmodule/" + f, new File(stage, f));
            if (!"stock".equals(id)) {
                File zip = new File(stage, "bootanimation.zip");
                if ("custom".equals(id)) {
                    if (!customFile(ctx).isFile()) return "Import your own boot animation first.";
                    copyFile(customFile(ctx), zip);
                } else copyAsset(ctx, "bootanimations/" + id + ".zip", zip);
            }
        } catch (Throwable t) { return "Could not prepare the files: " + t.getMessage(); }
        stage.setReadable(true, false); stage.setExecutable(true, false);
        for (File f : stage.listFiles()) f.setReadable(true, false);
        String s = stage.getAbsolutePath();
        String sh =
            "D=" + DIR + "; M=" + MODULE + "; S=" + s + "\n" +
            "mkdir -p $D $M && chmod 700 " + ROOT_DIR + " $D || { echo 'cannot create '$D; exit 1; }\n" +
            "for f in module.prop post-fs-data.sh service.sh uninstall.sh; do cp -f $S/$f $M/$f || exit 1; done\n" +
            "chmod 644 $M/module.prop; chmod 755 $M/*.sh; touch $M/skip_mount; rm -f $M/disable $M/remove\n" +
            "v=`getprop persist.vendor.custom.variant.id`; c=`getprop persist.sys.customanim.boot`\n" +
            "if [ -n \"$c\" ] && [ -f \"$c\" ]; then t=$c\n" +
            "elif [ -n \"$v\" ] && [ -f /system/media/$v/bootanimation.zip ]; then t=/system/media/$v/bootanimation.zip\n" +
            "else t=/system/media/bootanimation.zip; fi\n" +
            "echo $t > $D/target\n" +
            ("stock".equals(id)
                ? "echo stock > $D/selection; rm -f $D/bootanimation.zip $D/attempts; echo restored > $D/result\n"
                : "cp -f $S/bootanimation.zip $D/bootanimation.zip.new && mv -f $D/bootanimation.zip.new $D/bootanimation.zip || { echo 'copy failed'; exit 1; }\n" +
                  "chmod 644 $D/bootanimation.zip; echo " + id + " > $D/selection; rm -f $D/attempts; echo staged > $D/result\n") +
            "echo \"`date '+%m-%d %H:%M:%S'` app: " + id + " staged for $t\" >> $D/log.txt\n" +
            "ls $M/module.prop >/dev/null && echo OK\n";
        Root.Result r = Root.run(sh);
        deleteTree(stage);
        if (!r.ok() || !r.out.contains("OK")) return ("Root step failed (grant " + com.hammad.redmagictweaks.BuildConfig.APP_NAME + " root in SukiSU): ") + r.out;
        String nubia = Root.run("cat /data/adb/nubia_ly_guard/boot/selection 2>/dev/null").out.trim();
        String warn = !nubia.isEmpty() && !"stock".equals(nubia)
            ? "\n\nnubia-ly also has a boot animation selected (" + nubia + "): restore stock in nubia-ly so the two don't overlap." : "";
        return ("stock".equals(id)
            ? "Stock boot animation on the next boot."
            : BootAnimFeatures.CHOICES[index] + " is ready: it shows from the next boot on.") + warn;
    }

    public static String status() {
        Root.Result r = Root.run(
            "D=" + DIR + "; M=" + MODULE + "\n" +
            "echo \"helper module: `[ -f $M/module.prop ] && { [ -f $M/disable ] && echo 'installed, DISABLED in SukiSU' || echo installed; } || echo 'not installed yet (applied on first use)'`\"\n" +
            "echo \"selected: `cat $D/selection 2>/dev/null || echo stock`\"\n" +
            "echo \"last result: `cat $D/result 2>/dev/null || echo none`\"\n" +
            "echo \"covers: `cat $D/target 2>/dev/null || echo -`\"\n" +
            "f=`getprop persist.vendor.sys.fansanim.boot`; [ -n \"$f\" ] && [ \"$f\" != 0 ] && echo \"note: RedMagic fans boot animation is on ($f)\"\n" +
            "n=`cat /data/adb/nubia_ly_guard/boot/selection 2>/dev/null`; [ -n \"$n\" ] && [ \"$n\" != stock ] && echo \"note: nubia-ly also selects a boot animation ($n)\"\n" +
            "echo; echo 'log:'; tail -n 8 $D/log.txt 2>/dev/null\n");
        return r.ok() ? r.out : "Root step failed: " + r.out;
    }

    // ------------------------------------------------------------------ files
    private static void copyAsset(Context ctx, String path, File dst) throws java.io.IOException {
        try (InputStream in = ctx.getAssets().open(path); OutputStream out = new FileOutputStream(dst)) { pipe(in, out); }
    }
    /** The helper module's files name the app's own data folder, module id and name (assets say rmtweaks / RM Tweaks). */
    private static void copyModuleFile(Context ctx, String path, File dst) throws java.io.IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        try (InputStream in = ctx.getAssets().open(path)) { pipe(in, b); }
        String s = new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("rmtweaks", BuildConfig.DATA_ID).replace("RM Tweaks", BuildConfig.APP_NAME);
        try (OutputStream out = new FileOutputStream(dst)) { out.write(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    private static void copyFile(File src, File dst) throws java.io.IOException {
        try (InputStream in = new java.io.FileInputStream(src); OutputStream out = new FileOutputStream(dst)) { pipe(in, out); }
    }
    private static void pipe(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] b = new byte[65536]; int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
    }
    private static void deleteTree(File f) {
        if (f.isDirectory()) { File[] l = f.listFiles(); if (l != null) for (File c : l) deleteTree(c); }
        f.delete();
    }
}
