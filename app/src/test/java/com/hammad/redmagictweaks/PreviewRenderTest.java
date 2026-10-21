package com.hammad.redmagictweaks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLog;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Renders the LookPreview pictures to app/build/previews/*.png (CI publishes them to ci-logs/previews/) so the drawings
 * can be checked without a phone. Not a correctness test: it only fails when drawing throws.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w412dp-h912dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PreviewRenderTest {

    @Test public void render() throws Exception {
        ShadowLog.stream = System.out;
        Context ctx = RuntimeEnvironment.getApplication();
        File out = new File("build/previews");
        out.mkdirs();
        Looks.V stock = Looks.live(ctx);
        Looks.V oneUi = Looks.with(stock, Presets.ONE_UI);
        int w = LookPreview.ALL.length * 150 + (LookPreview.ALL.length - 1) * 10;
        save(LookPreview.row(ctx, stock, LookPreview.ALL, w), new File(out, "1-stock.png"));
        save(LookPreview.row(ctx, oneUi, LookPreview.ALL, w), new File(out, "2-one-ui-8-look.png"));
        save(LookPreview.strip(ctx, stock, 412), new File(out, "3-statusbar-stock.png"));
        save(LookPreview.strip(ctx, oneUi, 412), new File(out, "4-statusbar-one-ui.png"));
        Looks.V page = Looks.with(oneUi, QsFeatures.QS_CC_LAYOUT, 1);
        Looks.V below = Looks.with(oneUi, QsFeatures.QS_CC_SLIDER_POS, 2);
        Looks.V classic = Looks.with(oneUi, QsFeatures.QS_CC_ENABLE, Boolean.FALSE);
        save(LookPreview.row(ctx, page, new String[]{LookPreview.CC}, 300), new File(out, "5-cc-one-ui-page.png"));
        save(LookPreview.row(ctx, below, new String[]{LookPreview.CC}, 300), new File(out, "6-cc-bars-below.png"));
        save(LookPreview.row(ctx, classic, new String[]{LookPreview.SHADE}, 300), new File(out, "7-shade-classic-qs.png"));
        // recent apps: every style, then the picker cards of each option
        String[] styles = com.hammad.redmagictweaks.xposed.RecentsArt.STYLES;
        for (int i = 0; i < styles.length; i++) {
            Looks.V v = Looks.with(stock, com.hammad.redmagictweaks.xposed.RecentsArt.KEY_STYLE, i);
            save(LookPreview.row(ctx, v, new String[]{LookPreview.RECENTS}, 300), new File(out, "8-recents-" + i + ".png"));
        }
        String[] keys = {com.hammad.redmagictweaks.xposed.RecentsArt.KEY_STYLE, com.hammad.redmagictweaks.xposed.RecentsArt.KEY_HEADER,
                com.hammad.redmagictweaks.xposed.RecentsArt.KEY_CLEAR, com.hammad.redmagictweaks.xposed.RecentsArt.KEY_CORNERS, com.hammad.redmagictweaks.xposed.RecentsArt.KEY_SIZE};
        for (String k : keys) {
            Features.Feature f = Features.feature(k);
            for (int i = 0; i < f.choices.length; i++)
                save(LookPreview.recentsCrop(ctx, Looks.with(stock, k, i), k), new File(out, "9-pick-" + k + "-" + i + ".png"));
        }
    }

    private static void save(Drawable d, File f) throws Exception {
        int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF0A0A0E);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        try (FileOutputStream o = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
        System.out.println("preview " + f + " " + w + "x" + h);
    }
}
