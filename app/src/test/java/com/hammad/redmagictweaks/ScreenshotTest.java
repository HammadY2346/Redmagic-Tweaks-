package com.hammad.redmagictweaks;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.View;

import com.hammad.redmagictweaks.ui.AboutActivity;
import com.hammad.redmagictweaks.ui.CategoryActivity;
import com.hammad.redmagictweaks.ui.MainActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLog;

import java.io.File;
import java.io.FileOutputStream;

import static org.robolectric.Shadows.shadowOf;

/**
 * Screenshots of the app's own screens (home, a category, an option sheet, About) in dark mode, rendered with
 * Robolectric native graphics into app/build/previews/screen-*.png: CI publishes them, the README uses them, and the
 * test fails when a screen can't even be built.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w412dp-h912dp-night-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ScreenshotTest {

    private static void setUp(Context c) {
        // the phone the screenshots pretend to be, with the module active
        Root.offline = true;
        Prefs.get(c);
        org.robolectric.util.ReflectionHelpers.setStaticField(Prefs.class, "worldReadable", true);
        ModuleStatus.testActive = true;
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class, "MODEL", "NX789J");
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class, "DEVICE", "NX789J");
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class, "PRODUCT", "NX789J");
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class, "FINGERPRINT", "nubia/NX789J/NX789J:16/BQ2A.250705.001/20260227:user/release-keys");
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.class, "DISPLAY", "RedMagicOS11.0.11_NX789J");
        org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION.class, "RELEASE", "16");
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("first_run_done", true).commit();
        Presets.apply(c, Presets.ONE_UI);
        Prefs.set(c, com.hammad.redmagictweaks.xposed.RecentsArt.KEY_STYLE, com.hammad.redmagictweaks.xposed.RecentsArt.ONE_UI);
        Prefs.set(c, "launcher_paged_drawer", true);
        Prefs.set(c, "fw_auto_rate_max", true);
        Pending.add(c, Catalog.item("qs_cc_layout"));
    }

    @Test public void screens() throws Exception {
        ShadowLog.stream = System.out;
        Context ctx = RuntimeEnvironment.getApplication();
        setUp(ctx);
        File out = new File("build/previews");
        out.mkdirs();

        Activity home = Robolectric.buildActivity(MainActivity.class).setup().get();
        snap(home.getWindow().getDecorView(), new File(out, "screen-" + BuildConfig.FLAVOR + "-1-home.png"));
        home.findViewById(R.id.list).scrollBy(0, dp(ctx, 760));
        snap(home.getWindow().getDecorView(), new File(out, "screen-" + BuildConfig.FLAVOR + "-2-home-categories.png"));

        Activity sb = Robolectric.buildActivity(CategoryActivity.class,
                new Intent(ctx, CategoryActivity.class).putExtra(CategoryActivity.EXTRA_CATEGORY, "statusbar")).setup().get();
        snap(sb.getWindow().getDecorView(), new File(out, "screen-" + BuildConfig.FLAVOR + "-3-status-bar.png"));

        Activity qp = Robolectric.buildActivity(CategoryActivity.class,
                new Intent(ctx, CategoryActivity.class).putExtra(CategoryActivity.EXTRA_CATEGORY, "quickpanel")).setup().get();
        snap(qp.getWindow().getDecorView(), new File(out, "screen-" + BuildConfig.FLAVOR + "-4-quick-panel.png"));

        Activity home2 = Robolectric.buildActivity(CategoryActivity.class,
                new Intent(ctx, CategoryActivity.class).putExtra(CategoryActivity.EXTRA_CATEGORY, "home")).setup().get();
        snap(home2.getWindow().getDecorView(), new File(out, "screen-" + BuildConfig.FLAVOR + "-5-home-and-recents.png"));
        // the option sheet of the recent apps style
        View row = findRow(home2.findViewById(R.id.list), "Recent apps style");
        if (row != null) {
            row.performClick();
            shadowOf(Looper.getMainLooper()).idle();
            Dialog d = ShadowDialog.getLatestDialog();
            if (d != null) snap(d.getWindow().getDecorView(), new File(out, "screen-" + BuildConfig.FLAVOR + "-6-option-sheet.png"));
        }

        Activity about = Robolectric.buildActivity(AboutActivity.class).setup().get();
        snap(about.getWindow().getDecorView(), new File(out, "screen-" + BuildConfig.FLAVOR + "-7-about.png"));

        // checks (not for the README): the totals, the volume page in Detailed mode with a One UI panel option, the
        // colour sheet, the fingerprint page
        StringBuilder totals = new StringBuilder("preview totals: all=" + Catalog.total(ctx));
        for (Catalog.Category c : Catalog.all()) totals.append(' ').append(c.id).append('=').append(Catalog.total(c));
        System.out.println(totals);
        UiPrefs.setDetailed(ctx, true);
        Prefs.set(ctx, "vol_oneui_color", 10);
        Prefs.set(ctx, "vol_oneui_level", true);
        Activity vol = Robolectric.buildActivity(CategoryActivity.class,
                new Intent(ctx, CategoryActivity.class).putExtra(CategoryActivity.EXTRA_CATEGORY, "volume")).setup().get();
        snap(vol.getWindow().getDecorView(), new File(out, "check-" + BuildConfig.FLAVOR + "-8-volume-detailed.png"));
        vol.findViewById(R.id.list).scrollBy(0, dp(ctx, 700));
        snap(vol.getWindow().getDecorView(), new File(out, "check-" + BuildConfig.FLAVOR + "-9-volume-options.png"));
        View colour = findRow(vol.findViewById(R.id.list), "Bar colour");
        if (colour != null) {
            colour.performClick();
            shadowOf(Looper.getMainLooper()).idle();
            Dialog d = ShadowDialog.getLatestDialog();
            if (d != null) snap(d.getWindow().getDecorView(), new File(out, "check-" + BuildConfig.FLAVOR + "-10-bar-colour.png"));
        }
        // a typed value: the width sheet with "Custom value…" picked and its slider
        Prefs.set(ctx, "vol_oneui_width_c", true);
        Prefs.set(ctx, "vol_oneui_width_v", 30);
        Activity vol2 = Robolectric.buildActivity(CategoryActivity.class,
                new Intent(ctx, CategoryActivity.class).putExtra(CategoryActivity.EXTRA_CATEGORY, "volume")).setup().get();
        View width = findRow(vol2.findViewById(R.id.list), "Panel width");
        if (width != null) {
            width.performClick();
            shadowOf(Looper.getMainLooper()).idle();
            Dialog d = ShadowDialog.getLatestDialog();
            if (d != null) snap(d.getWindow().getDecorView(), new File(out, "check-" + BuildConfig.FLAVOR + "-12-width-custom.png"));
        }
        Prefs.set(ctx, "vol_oneui_width_c", false);
        UiPrefs.setDetailed(ctx, false);
        Activity fp = Robolectric.buildActivity(CategoryActivity.class,
                new Intent(ctx, CategoryActivity.class).putExtra(CategoryActivity.EXTRA_CATEGORY, "fingerprint")).setup().get();
        snap(fp.getWindow().getDecorView(), new File(out, "check-" + BuildConfig.FLAVOR + "-11-fingerprint.png"));
    }

    private static View findRow(View root, String title) {
        if (root instanceof android.widget.TextView && title.contentEquals(((android.widget.TextView) root).getText())) {
            View v = root;
            while (v.getParent() instanceof View && !(v.getParent() instanceof androidx.recyclerview.widget.RecyclerView)) v = (View) v.getParent();
            return v;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) { View r = findRow(g.getChildAt(i), title); if (r != null) return r; }
        }
        return null;
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private static void snap(View root, File f) throws Exception {
        shadowOf(Looper.getMainLooper()).idle();
        Context c = root.getContext();
        int w = c.getResources().getDisplayMetrics().widthPixels, h = c.getResources().getDisplayMetrics().heightPixels;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        shadowOf(Looper.getMainLooper()).idle();
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(0xFF0A0A0E);
        root.draw(cv);
        // half size is plenty for a README
        Bitmap small = Bitmap.createScaledBitmap(b, w / 2, h / 2, true);
        try (FileOutputStream o = new FileOutputStream(f)) { small.compress(Bitmap.CompressFormat.PNG, 100, o); }
        System.out.println("preview " + f + " " + small.getWidth() + "x" + small.getHeight());
    }
}
