package com.hammad.redmagictweaks.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hammad.redmagictweaks.BuildConfig;
import com.hammad.redmagictweaks.R;

import java.io.InputStream;

/** About & credits: version, everyone whose work the app uses (with links), open-source licences, links to the repo. */
public class AboutActivity extends BaseActivity {
    @Override protected void onSettingsChanged() { }

    static final String REPO = "https://github.com/HammadY2346/Redmagic-Tweaks-";

    /** Name, what the app uses from them, link (or null). */
    static final String[][] CREDITS = {
        {"Hammad Yasin (HammadY2346)", "Author: NX789J research, the module and this app, the One UI 8 work", "https://github.com/HammadY2346"},
        {"EliteDarkKaiser / Austin Young · nubia-ly", "Quick Settings, status bar, clock, icon, notification, volume and power menu backends; fingerprint and charging catalogue; boot animation set. Used with permission", "https://github.com/austineyoung2000/nubia-ly"},
        {"RisingOS Revived", "Battery shapes, Wi-Fi and signal artwork, fingerprint icons and animations, boot animations (Apache 2.0)", "https://github.com/RisingOS-Revived"},
        {"RedMagic Helper by 酷安@卒迹", "Game Space hook targets: Game Assist everywhere, macros, Super Resolution with Diablo, combo and AI trigger speed (re-implemented here)", null},
        {"Icon pack authors", "Acherus, Aurora, Circular, Filled, Gradicon, Kai, Lorn, Nostalgic, OxygenOS, Outline, Plumpy, Pavlova UI, Rounded, Sam, Victor, Xperia (via nubia-ly)", null},
        {"Iconify by Mahmud0808", "Inspiration for an organised customisation app", "https://github.com/Mahmud0808/Iconify"},
        {"Inter (Rasmus Andersson) and Poppins (Indian Type Foundry)", "This app's font and the One UI style System UI font; One UI clock digits. SIL Open Font License 1.1", "https://rsms.me/inter/"},
        {"Vector / LSPosed and the Xposed API", "The hook framework", "https://github.com/JingMatrix/Vector"},
        {"Android Open Source Project, Material Components", "Platform and UI library, Apache License 2.0", "https://source.android.com"},
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        ((CollapsingToolbarLayout) findViewById(R.id.collapsing)).setTitle("About");
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> finish());
        LinearLayout c = findViewById(R.id.content);

        // the app
        LinearLayout app = card();
        app.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        app.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72)));
        app.addView(text(BuildConfig.APP_NAME, 22, R.color.text, 700, Gravity.CENTER, 8));
        app.addView(text("Version " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") · " + BuildConfig.FLAVOR + " build", 13, R.color.text_dim, 400, Gravity.CENTER, 2));
        app.addView(text("Made and tested on the REDMAGIC 10S Pro (NX789J) with the China firmware, RedMagic OS 11.0.11 (Android 16). "
                + "Global and EEA firmware differ a little and are not tested yet.", 13.5f, R.color.text, 400, Gravity.CENTER, 12));
        app.addView(text("Not affiliated with nubia, ZTE or REDMAGIC. REDMAGIC is their trademark. Samsung One UI is design inspiration only: "
                + "no Samsung files are shipped.", 12.5f, R.color.text_dim, 400, Gravity.CENTER, 10));
        c.addView(app);

        c.addView(subhead("Links"));
        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.VERTICAL);
        links.addView(row("Source code", "GitHub · GNU GPL v3.0", REPO, true, false));
        links.addView(row("Report a problem", "Attach the Logs (Logs › Share)", REPO + "/issues", false, false));
        links.addView(row("Open-source licences", "This app and the parts it bundles", null, false, true));
        c.addView(links);

        c.addView(subhead("Credits"));
        LinearLayout credits = new LinearLayout(this);
        credits.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < CREDITS.length; i++) credits.addView(row(CREDITS[i][0], CREDITS[i][1], CREDITS[i][2], i == 0, i == CREDITS.length - 1));
        c.addView(credits);
        Ui.fonts(c);
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 20);
        l.setPadding(p, p, p, p);
        return l;
    }

    private TextView text(String s, float sp, int color, int weight, int gravity, int topDp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(getColor(color));
        t.setGravity(gravity);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        t.setPadding(0, Ui.dp(this, topDp), 0, 0);
        return t;
    }

    private TextView subhead(String s) {
        TextView t = text(s, 14, R.color.text_dim, 700, Gravity.START, 22);
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 22), 0, Ui.dp(this, 8));
        return t;
    }

    private View row(String title, String sub, final String url, boolean first, boolean last) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
        r.setBackground(Ui.groupRow(this, first, last));
        r.addView(text(title, 16, R.color.text, 500, Gravity.START, 0));
        r.addView(text(sub, 12.5f, R.color.text_dim, 400, Gravity.START, 2));
        if (url != null) {
            TextView link = text(url.replace("https://", ""), 12.5f, R.color.accent, 500, Gravity.START, 4);
            r.addView(link);
            r.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Throwable ignored) { } });
        } else if ("Open-source licences".equals(title)) {
            r.setOnClickListener(v -> licences());
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = last ? 0 : Ui.dp(this, 1);
        r.setLayoutParams(lp);
        return r;
    }

    /** The licence texts the app ships (assets/licenses) plus the app's own licence. */
    private void licences() {
        final String[] files;
        try { files = getAssets().list("licenses"); } catch (Throwable t) { return; }
        final String[] names = new String[(files == null ? 0 : files.length) + 1];
        names[0] = BuildConfig.APP_NAME + " · GNU General Public License v3.0";
        for (int i = 0; files != null && i < files.length; i++) names[i + 1] = files[i].replace(".txt", "").replace('-', ' ');
        new MaterialAlertDialogBuilder(this).setTitle("Open-source licences").setItems(names, (d, which) -> {
            if (which == 0) { show(names[0], "This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.\n\nThis program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details: https://www.gnu.org/licenses/gpl-3.0.html\n\nSource: " + REPO); return; }
            try (InputStream in = getAssets().open("licenses/" + files[which - 1])) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
                show(names[which], b.toString("UTF-8"));
            } catch (Throwable ignored) { }
        }).setPositiveButton(android.R.string.ok, null).show();
    }

    private void show(String title, String body) {
        TextView t = new TextView(this);
        t.setText(body);
        t.setTextSize(12.5f);
        t.setTextColor(getColor(R.color.text));
        t.setTextIsSelectable(true);
        int p = Ui.dp(this, 22);
        t.setPadding(p, Ui.dp(this, 8), p, p);
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        new MaterialAlertDialogBuilder(this).setTitle(title).setView(sv).setPositiveButton(android.R.string.ok, null).show();
    }
}
