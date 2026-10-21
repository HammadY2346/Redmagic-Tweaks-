package com.hammad.redmagictweaks.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hammad.redmagictweaks.LookPreview;
import com.hammad.redmagictweaks.Looks;
import com.hammad.redmagictweaks.R;

/** Preview pictures inside dialogs: a title block with a picture, and the full gallery of every screen. */
public final class PreviewDialogs {
    private PreviewDialogs() {}

    static int dp(Context c, float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics())); }

    /** Width available for a picture inside a dialog, in dp. */
    static int dialogWidthDp(Context c) { return Math.max(220, c.getResources().getConfiguration().screenWidthDp - 96); }

    /** Title, a grey line under it, then the picture (tap it for the gallery when {@code onTap} is set). */
    static View header(Context c, String title, String subtitle, Drawable picture, View.OnClickListener onTap) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c, 24), dp(c, 20), dp(c, 24), dp(c, 4));
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(c.getColor(R.color.text));
        t.setTextSize(20);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        box.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = new TextView(c);
            s.setText(subtitle);
            s.setTextColor(c.getColor(R.color.text_dim));
            s.setTextSize(12.5f);
            s.setPadding(0, dp(c, 4), 0, 0);
            box.addView(s);
        }
        if (picture != null) {
            ImageView img = new ImageView(c);
            img.setTag("preview");
            img.setImageDrawable(picture);
            img.setAdjustViewBounds(true);
            img.setContentDescription("Preview");
            if (onTap != null) img.setOnClickListener(onTap);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(c, 12);
            box.addView(img, lp);
        }
        return box;
    }

    /** Every screen for {@code v}, big: the status bar on top, then the phones in a row you can scroll sideways. */
    public static void showAll(Activity a, String title, Looks.V v) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));
        TextView bar = caption(a, "Status bar");
        col.addView(bar);
        ImageView strip = new ImageView(a);
        strip.setImageDrawable(LookPreview.strip(a, v, dialogWidthDp(a)));
        col.addView(strip);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String s : LookPreview.ALL) {
            LinearLayout one = new LinearLayout(a);
            one.setOrientation(LinearLayout.VERTICAL);
            one.setPadding(0, 0, dp(a, 14), 0);
            one.addView(caption(a, LookPreview.title(s)));
            ImageView img = new ImageView(a);
            img.setImageDrawable(LookPreview.phone(a, v, s, 230));
            one.addView(img);
            row.addView(one);
        }
        hs.addView(row);
        col.addView(hs);
        ScrollView sv = new ScrollView(a);
        sv.addView(col);
        new MaterialAlertDialogBuilder(a).setTitle(title).setView(sv).setPositiveButton(android.R.string.ok, null).show();
    }

    private static TextView caption(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(c.getColor(R.color.text_dim));
        t.setTextSize(13);
        t.setPadding(0, dp(c, 10), 0, dp(c, 6));
        return t;
    }
}
