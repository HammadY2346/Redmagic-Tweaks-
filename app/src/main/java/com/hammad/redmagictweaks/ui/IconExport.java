package com.hammad.redmagictweaks.ui;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports every launcher app's icon in the One UI shape, ready to drop into a ZTE / nubia theme (.zmtp,
 * overlays/com.zte.mifavor.launcher.resource/raw/icons_cur.zip): 230 x 230 PNGs named like the theme engine looks them
 * up (package name with '.' -> '_'), in Download/RMTweaks-app-icons.zip under icon/. A theme icon for a package always
 * wins over the app's own icon, whatever shape the firmware gives adaptive icons, so this needs no root.
 *  - Adaptive icons: background + foreground layers, full bleed, clipped to the squircle.
 *  - Old-style icons that fill their square: zoomed a little and clipped to the squircle.
 *  - Other old-style icons: centred on a white squircle.
 */
final class IconExport {
    private IconExport() {}

    static final int SIZE = 230;

    static void run(final Activity a) {
        Toast.makeText(a, "Exporting app icons…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String msg;
            try {
                int n = export(a);
                msg = "Saved " + n + " app icons to Download/RMTweaks-app-icons.zip";
            } catch (Throwable t) {
                msg = "Icon export failed: " + t;
            }
            final String m = msg;
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(a, m, Toast.LENGTH_LONG).show());
        }, "rmtweaks-icon-export").start();
    }

    private static int export(Activity a) throws Exception {
        PackageManager pm = a.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        StringBuilder list = new StringBuilder("package\tlabel\tkind\n");
        Set<String> done = new HashSet<>();
        int n = 0;
        try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
            for (ResolveInfo ri : apps) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (!done.add(pkg)) continue;
                Drawable d;
                try { d = ri.activityInfo.loadUnbadgedIcon(pm); } catch (Throwable t) { d = null; }
                if (d == null) continue;
                String[] kind = new String[1];
                Bitmap b = render(d, kind);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.PNG, 100, png);
                zip.putNextEntry(new ZipEntry("icon/" + pkg.replace('.', '_') + ".png"));
                zip.write(png.toByteArray());
                zip.closeEntry();
                CharSequence label = ri.loadLabel(pm);
                list.append(pkg).append('\t').append(label == null ? "" : label.toString().replace('\t', ' ')).append('\t').append(kind[0]).append('\n');
                n++;
            }
            zip.putNextEntry(new ZipEntry("apps.txt"));
            zip.write(list.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "RMTweaks-app-icons.zip");
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        Uri uri = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new IllegalStateException("can't create the file in Download");
        try (OutputStream out = a.getContentResolver().openOutputStream(uri)) {
            out.write(zipBytes.toByteArray());
        }
        return n;
    }

    /** The One UI squircle (the shape of the theme's own icons), in a SIZE x SIZE box. */
    static Path squircle(float s) {
        float k = s / 100f;
        Path p = new Path();
        p.moveTo(50 * k, 0);
        p.cubicTo(92.7f * k, 0, 100 * k, 7.3f * k, 100 * k, 50 * k);
        p.cubicTo(100 * k, 92.7f * k, 92.7f * k, 100 * k, 50 * k, 100 * k);
        p.cubicTo(7.3f * k, 100 * k, 0, 92.7f * k, 0, 50 * k);
        p.cubicTo(0, 7.3f * k, 7.3f * k, 0, 50 * k, 0);
        p.close();
        return p;
    }

    static Bitmap render(Drawable d, String[] kind) {
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.clipPath(squircle(SIZE));
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
            // a layer is 108 dp with the middle 72 dp visible: draw it 1.5x the box, centred
            int full = Math.round(SIZE * 1.5f), off = (full - SIZE) / 2;
            Drawable bg = ad.getBackground(), fg = ad.getForeground();
            if (bg != null) { bg.setBounds(-off, -off, SIZE + off, SIZE + off); bg.draw(c); }
            else c.drawColor(Color.WHITE);
            if (fg != null) { fg.setBounds(-off, -off, SIZE + off, SIZE + off); fg.draw(c); }
            kind[0] = "adaptive";
            return out;
        }
        // old-style icon: does it fill its square (opaque near the corners)?
        Bitmap probe = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas pc = new Canvas(probe);
        d.setBounds(0, 0, SIZE, SIZE);
        d.draw(pc);
        int in = Math.round(SIZE * 0.14f);
        boolean fills = Color.alpha(probe.getPixel(in, in)) > 200 && Color.alpha(probe.getPixel(SIZE - 1 - in, in)) > 200
                && Color.alpha(probe.getPixel(in, SIZE - 1 - in)) > 200 && Color.alpha(probe.getPixel(SIZE - 1 - in, SIZE - 1 - in)) > 200;
        if (fills) {
            int z = Math.round(SIZE * 0.06f);                 // zoom past its own rounded corners
            d.setBounds(-z, -z, SIZE + z, SIZE + z);
            d.draw(c);
            kind[0] = "legacy-full";
        } else {
            c.drawColor(Color.WHITE);
            int pad = Math.round(SIZE * 0.14f);
            d.setBounds(pad, pad, SIZE - pad, SIZE - pad);
            d.draw(c);
            kind[0] = "legacy";
        }
        probe.recycle();
        return out;
    }
}
