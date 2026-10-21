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
        // One UI 8 volume panel and power menu: a few combinations, then every option card of each setting in a strip
        Looks.V vol = Looks.with(Looks.with(stock, SurfaceFeatures.VOL_ONEUI, Boolean.TRUE), SurfaceFeatures.PWR_ONEUI, Boolean.TRUE);
        Object[][][] combos = {
            {},
            {{SurfaceFeatures.VOL_ONEUI_WIDTH, 1}, {SurfaceFeatures.VOL_ONEUI_HEIGHT, 3}},
            {{SurfaceFeatures.VOL_ONEUI_SHAPE, 1}, {SurfaceFeatures.VOL_ONEUI_COLOR, 3}, {SurfaceFeatures.VOL_ONEUI_LEVEL, Boolean.TRUE}},
            {{SurfaceFeatures.VOL_ONEUI_SHAPE, 4}, {SurfaceFeatures.VOL_ONEUI_COLOR, 10}, {SurfaceFeatures.VOL_ONEUI_TRACK, 3}},
            {{SurfaceFeatures.VOL_ONEUI_ICON, 1}, {SurfaceFeatures.VOL_ONEUI_HEIGHT, 1}, {SurfaceFeatures.VOL_ONEUI_WIDTH, 4}},
            {{SurfaceFeatures.PWR_ONEUI_SHAPE, 1}, {SurfaceFeatures.PWR_ONEUI_COLORS, 1}},
            {{SurfaceFeatures.PWR_ONEUI_SHAPE, 3}, {SurfaceFeatures.PWR_ONEUI_COLORS, 5}, {SurfaceFeatures.PWR_ONEUI_SIZE, 3}, {SurfaceFeatures.PWR_ONEUI_LABELS, 2}},
            // RedMagic's own bar restyled (One UI panel off)
            {{SurfaceFeatures.VOL_ONEUI, Boolean.FALSE}, {SurfaceFeatures.VOL_ONEUI_COLOR, 3}, {SurfaceFeatures.VOL_ONEUI_LEVEL, Boolean.TRUE}},
            {{SurfaceFeatures.VOL_ONEUI, Boolean.FALSE}, {SurfaceFeatures.VOL_ONEUI_SHAPE, 7}, {SurfaceFeatures.VOL_ONEUI_COLOR, 12}, {SurfaceFeatures.VOL_ONEUI_WIDTH, 7}, {SurfaceFeatures.VOL_ONEUI_HEIGHT, 2}},
            {{SurfaceFeatures.VOL_ONEUI, Boolean.FALSE}, {SurfaceFeatures.VOL_ONEUI_HEIGHT, 3}},
        };
        for (int i = 0; i < combos.length; i++) {
            Looks.V v = vol;
            for (Object[] kv : combos[i]) v = Looks.with(v, (String) kv[0], kv[1]);
            save(LookPreview.row(ctx, v, new String[]{LookPreview.VOLUME, LookPreview.POWER}, 310), new File(out, "10-oneui-volume-power-" + i + ".png"));
        }
        // always-on display: stock and customized
        Looks.V aod = Looks.with(Looks.with(Looks.with(Looks.with(Looks.with(stock, AodFeatures.CLOCK_COLOR, 13), AodFeatures.CLOCK_SIZE, 4),
                AodFeatures.NOTIF_COUNT, 2), AodFeatures.NOTIF_APP_ICONS, Boolean.TRUE), AodFeatures.LUNAR, 1);
        save(LookPreview.row(ctx, stock, new String[]{LookPreview.AOD}, 150), new File(out, "12-aod-stock.png"));
        save(LookPreview.row(ctx, aod, new String[]{LookPreview.AOD}, 150), new File(out, "12-aod-custom.png"));
        String[] keys = {SurfaceFeatures.VOL_ONEUI_WIDTH, SurfaceFeatures.VOL_ONEUI_HEIGHT, SurfaceFeatures.VOL_ONEUI_SHAPE, SurfaceFeatures.VOL_ONEUI_COLOR,
                SurfaceFeatures.VOL_ONEUI_TRACK, SurfaceFeatures.VOL_ONEUI_ICON, SurfaceFeatures.PWR_ONEUI_SIZE, SurfaceFeatures.PWR_ONEUI_SHAPE,
                SurfaceFeatures.PWR_ONEUI_COLORS, SurfaceFeatures.PWR_ONEUI_LABELS};
        for (String k : keys) {
            Features.Feature f = Features.feature(k);
            Drawable[] cards = new Drawable[f.choices.length];
            for (int i = 0; i < cards.length; i++) cards[i] = Previews.of(k).get(ctx, i);
            saveStrip(cards, new File(out, "11-cards-" + k + ".png"));
        }
    }

    /** Option cards side by side, 7 per line. */
    private static void saveStrip(Drawable[] cards, File f) throws Exception {
        int cw = cards[0].getIntrinsicWidth(), ch = cards[0].getIntrinsicHeight(), gap = 12, per = 7;
        int cols = Math.min(per, cards.length), rows = (cards.length + per - 1) / per;
        Bitmap b = Bitmap.createBitmap(cols * (cw + gap) + gap, rows * (ch + gap) + gap, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF0A0A0E);
        for (int i = 0; i < cards.length; i++) {
            int x = gap + (i % per) * (cw + gap), y = gap + (i / per) * (ch + gap);
            cards[i].setBounds(x, y, x + cw, y + ch);
            cards[i].draw(c);
        }
        try (FileOutputStream o = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
        System.out.println("preview " + f + " " + b.getWidth() + "x" + b.getHeight());
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
