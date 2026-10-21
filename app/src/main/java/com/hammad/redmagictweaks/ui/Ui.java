package com.hammad.redmagictweaks.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.hammad.redmagictweaks.R;

import java.util.HashMap;
import java.util.Map;

/** Small UI helpers: dp, the app font (Inter when the build has it, like the One UI style System UI font), card shapes. */
public final class Ui {
    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    private static final Map<Integer, Typeface> FONTS = new HashMap<>();
    private static Boolean hasInter;

    /** Inter at {@code weight} (variable 'wght' axis), or the system sans when the font is not in this build. */
    public static synchronized Typeface font(Context c, int weight) {
        Typeface t = FONTS.get(weight);
        if (t != null) return t;
        if (hasInter == null) {
            try { c.getAssets().open("fonts/oneui_sans.ttf").close(); hasInter = true; } catch (Throwable e) { hasInter = false; }
        }
        if (hasInter) {
            try {
                t = new Typeface.Builder(c.getAssets(), "fonts/oneui_sans.ttf").setFontVariationSettings("'wght' " + weight).setWeight(weight).build();
            } catch (Throwable ignored) { }
        }
        if (t == null) t = Typeface.create(Typeface.SANS_SERIF, weight, false);
        FONTS.put(weight, t);
        return t;
    }

    /** Gives every TextView under {@code root} the app font, keeping bold text bold. */
    public static void fonts(View root) {
        if (root == null) return;
        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            Typeface cur = tv.getTypeface();
            int w = cur == null ? 400 : cur.getWeight();
            if (cur != null && cur.isBold() && w < 600) w = 650;
            tv.setTypeface(font(root.getContext(), w));
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) fonts(g.getChildAt(i));
        }
    }

    /**
     * Background of a row inside a grouped card: the first row of a group has the top corners rounded, the last the
     * bottom ones, a single row all four (24 dp, like the cards).
     */
    public static GradientDrawable groupRow(Context c, boolean first, boolean last) {
        float r = dp(c, 24);
        GradientDrawable g = new GradientDrawable();
        g.setColor(c.getColor(R.color.surface));
        g.setCornerRadii(new float[]{first ? r : 0, first ? r : 0, first ? r : 0, first ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0, last ? r : 0});
        return g;
    }
}
