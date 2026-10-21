package com.hammad.redmagictweaks.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.hammad.redmagictweaks.BuildConfig;
import com.hammad.redmagictweaks.Catalog;
import com.hammad.redmagictweaks.LayoutStore;
import com.hammad.redmagictweaks.LookPreview;
import com.hammad.redmagictweaks.Looks;
import com.hammad.redmagictweaks.ModuleStatus;
import com.hammad.redmagictweaks.Pending;
import com.hammad.redmagictweaks.Presets;
import com.hammad.redmagictweaks.R;
import com.hammad.redmagictweaks.Root;
import com.hammad.redmagictweaks.UiPrefs;

/** First run: enable the module in LSPosed (scope list to copy), reboot once, pick a starting look. */
public class FirstRunActivity extends BaseActivity {
    @Override protected void onSettingsChanged() { }

    private int look = -1;   // -1 keep as is, 0 One UI 8, 1 stock
    private MaterialCardView oneUiCard, stockCard;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_run);
        ((CollapsingToolbarLayout) findViewById(R.id.collapsing)).setTitle("Welcome");
        LinearLayout c = findViewById(R.id.content);
        c.addView(text(BuildConfig.APP_NAME + " gives your REDMAGIC a One UI 8 look and " + Catalog.total(this) + " tweaks. Three steps and you're set.",
                15, R.color.text_dim, 400, 4));

        final String[] scope = getResources().getStringArray(R.array.xposed_scope);
        LinearLayout s1 = step(1, "Turn it on in LSPosed",
                "Open LSPosed › Modules › " + BuildConfig.APP_NAME + ", switch it on and tick every app in its scope (" + scope.length
                        + " apps, starting with System Framework). LSPosed may warn about system apps; that is expected.");
        LinearLayout b1 = buttons();
        b1.addView(button("Copy scope", v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("LSPosed scope", String.join("\n", scope)));
            Toast.makeText(this, "Scope list copied", Toast.LENGTH_SHORT).show();
        }, false));
        TextView state = text(ModuleStatus.isActive() ? "Module is active" : "Not active yet", 13, ModuleStatus.isActive() ? R.color.ok : R.color.text_dim, 600, 0);
        state.setGravity(Gravity.CENTER_VERTICAL);
        state.setPadding(Ui.dp(this, 12), 0, 0, 0);
        b1.addView(state);
        s1.addView(b1);
        c.addView(s1);

        LinearLayout s2 = step(2, "Reboot once",
                "The framework tweaks load when Android starts, and the hooked apps can only read the settings after one reboot.");
        LinearLayout b2 = buttons();
        b2.addView(button("Reboot now", v -> new Thread(() -> {
            UiPrefs.setFirstRunDone(this);
            Root.Result r = Root.reboot();
            if (!r.ok()) runOnUiThread(() -> Toast.makeText(this, "Root command failed: " + r.out, Toast.LENGTH_LONG).show());
        }).start(), false));
        s2.addView(b2);
        c.addView(s2);

        LinearLayout s3 = step(3, "Pick a starting look", "You can change every part of it later, or switch back any time.");
        LinearLayout looks = new LinearLayout(this);
        looks.setOrientation(LinearLayout.HORIZONTAL);
        looks.setPadding(0, Ui.dp(this, 12), 0, 0);
        Looks.V live = Looks.live(this);
        oneUiCard = lookCard("One UI 8 look", Looks.with(live, Presets.ONE_UI), 0);
        stockCard = lookCard("Stock RedMagic", Looks.with(live, Presets.STOCK), 1);
        looks.addView(oneUiCard);
        looks.addView(stockCard);
        s3.addView(looks);
        c.addView(s3);
        Ui.fonts(c);

        MaterialButton done = findViewById(R.id.done);
        done.setOnClickListener(v -> finishSetup());
    }

    private void finishSetup() {
        if (look >= 0) {
            Object[][] set = look == 0 ? Presets.ONE_UI : Presets.STOCK;
            try { LayoutStore.saveCurrent(this, BaseActivity.AUTO_BACKUP); } catch (Throwable ignored) { }
            Presets.apply(this, set);
            for (Object[] e : set) Pending.add(this, Catalog.item((String) e[0]));
        }
        UiPrefs.setFirstRunDone(this);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    @Override public void onBackPressed() { UiPrefs.setFirstRunDone(this); super.onBackPressed(); }

    private MaterialCardView lookCard(String name, Looks.V values, final int index) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(getColor(R.color.surface_hi));
        card.setRadius(Ui.dp(this, 20));
        card.setCardElevation(0);
        card.setStrokeColor(getColor(R.color.outline));
        card.setStrokeWidth(Ui.dp(this, 1));
        LinearLayout in = new LinearLayout(this);
        in.setOrientation(LinearLayout.VERTICAL);
        in.setGravity(Gravity.CENTER_HORIZONTAL);
        in.setPadding(Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 10));
        ImageView img = new ImageView(this);
        img.setImageDrawable(LookPreview.phone(this, values, LookPreview.CC, 96));
        in.addView(img);
        in.addView(text(name, 14, R.color.text, 600, 8));
        card.addView(in);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginEnd(index == 0 ? Ui.dp(this, 10) : 0);
        card.setLayoutParams(lp);
        card.setOnClickListener(v -> {
            look = index;
            oneUiCard.setStrokeColor(getColor(look == 0 ? R.color.accent : R.color.outline));
            oneUiCard.setStrokeWidth(Ui.dp(this, look == 0 ? 2 : 1));
            stockCard.setStrokeColor(getColor(look == 1 ? R.color.accent : R.color.outline));
            stockCard.setStrokeWidth(Ui.dp(this, look == 1 ? 2 : 1));
        });
        return card;
    }

    private LinearLayout step(int n, String title, String body) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.card_bg);
        int p = Ui.dp(this, 20);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 14);
        l.setLayoutParams(lp);
        TextView num = text(String.valueOf(n), 15, R.color.accent, 700, 0);
        num.setGravity(Gravity.CENTER);
        num.setBackgroundResource(R.drawable.icon_circle);
        l.addView(num, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)));
        l.addView(text(title, 18, R.color.text, 700, 12));
        l.addView(text(body, 14, R.color.text_dim, 400, 4));
        return l;
    }

    private LinearLayout buttons() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, Ui.dp(this, 12), 0, 0);
        return l;
    }

    private MaterialButton button(String label, View.OnClickListener click, boolean filled) {
        MaterialButton b = new MaterialButton(this, null, filled ? com.google.android.material.R.attr.materialButtonStyle : com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setCornerRadius(Ui.dp(this, 20));
        b.setOnClickListener(click);
        return b;
    }

    private TextView text(String s, float sp, int color, int weight, int topDp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(getColor(color));
        t.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        t.setPadding(0, Ui.dp(this, topDp), 0, 0);
        return t;
    }
}
