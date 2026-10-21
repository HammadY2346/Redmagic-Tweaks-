package com.hammad.redmagictweaks.ui;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.hammad.redmagictweaks.BootAnim;
import com.hammad.redmagictweaks.BootAnimFeatures;
import com.hammad.redmagictweaks.Catalog;
import com.hammad.redmagictweaks.CosmeticsFeatures;
import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.FingerprintCustomFeatures;
import com.hammad.redmagictweaks.FlavorBits;
import com.hammad.redmagictweaks.Frames;
import com.hammad.redmagictweaks.LookPreview;
import com.hammad.redmagictweaks.Looks;
import com.hammad.redmagictweaks.Pending;
import com.hammad.redmagictweaks.Prefs;
import com.hammad.redmagictweaks.Presets;
import com.hammad.redmagictweaks.Previews;
import com.hammad.redmagictweaks.R;
import com.hammad.redmagictweaks.Root;
import com.hammad.redmagictweaks.UiPrefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A category: its live preview on top (a slim strip stays pinned once it scrolls away), then the settings in small
 * groups. Each row shows the current value in red, a dot when it differs from stock and a badge when it needs a
 * restart; (i) or a long press shows what it does and "Reset to stock". Choices open {@link OptionSheet}.
 */
public class CategoryActivity extends BaseActivity {
    public static final String EXTRA_CATEGORY = "category", EXTRA_QUERY = "query";

    private Catalog.Category cat;
    private String[] screens;
    private RecyclerView list;
    private ImageView strip;
    private final Map<String, Drawable> thumbs = new HashMap<>();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener prefWatch;
    private String pendingAction;

    private static final int PREVIEW = 0, NOTE = 1, SUBHEAD = 2, SETTING = 3;

    private static final class Row {
        final int type; final String text; final Catalog.Item item; final boolean first, last;
        Row(int type, String text, Catalog.Item item, boolean first, boolean last) { this.type = type; this.text = text; this.item = item; this.first = first; this.last = last; }
    }
    private final List<Row> rows = new ArrayList<>();

    // ============================================================================================ file pickers

    private final ActivityResultLauncher<Uri> pickFolder = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
        if (uri == null) return;
        boolean fp = FingerprintCustomFeatures.FP_IMPORT.equals(pendingAction);
        int n = fp ? Frames.fpImportFolder(this, uri) : Frames.importFolder(this, uri);
        Toast.makeText(this, n == 0 ? "No image frames found in that folder" : n + " frames imported", Toast.LENGTH_LONG).show();
        if (n > 0) Pending.add(this, Catalog.item(fp ? "fpc_anim_set" : "charge_set"));
        refresh();
    });
    private final ActivityResultLauncher<String[]> pickFont = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri == null) return;
        boolean ok = Frames.fontImport(this, uri);
        Toast.makeText(this, ok ? "Font imported" : "Could not read that file", Toast.LENGTH_LONG).show();
        if (ok) Pending.add(this, Catalog.item(CosmeticsFeatures.COS_CLOCK_FONT));
        refresh();
    });
    private final ActivityResultLauncher<String[]> pickBootZip = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri == null) return;
        busy("Importing…", () -> {
            String err = BootAnim.importZip(this, uri);
            runOnUiThread(() -> {
                if (err != null) { new MaterialAlertDialogBuilder(this).setTitle("Import failed").setMessage(err).setPositiveButton(android.R.string.ok, null).show(); return; }
                Prefs.set(this, BootAnimFeatures.BOOTANIM, BootAnimFeatures.DEFS.length - 1);
                thumbs.clear(); refresh();
                applyBootAnim();
            });
        });
    });

    /** Long work off the UI thread behind a small progress dialog. */
    private void busy(String msg, Runnable work) {
        final android.app.ProgressDialog dlg = new android.app.ProgressDialog(this);
        dlg.setMessage(msg); dlg.setCancelable(false); dlg.show();
        new Thread(() -> { try { work.run(); } finally { runOnUiThread(dlg::dismiss); } }).start();
    }

    void applyBootAnim() {
        final int idx = Prefs.choice(this, BootAnimFeatures.BOOTANIM);
        final String[] res = new String[1];
        busy("Preparing the boot animation…", () -> {
            res[0] = BootAnim.apply(getApplicationContext(), idx);
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this).setTitle("Boot animation").setMessage(res[0])
                    .setPositiveButton("Reboot now", (d, w) -> new Thread(Root::reboot).start())
                    .setNegativeButton("Later", null).show());
        });
    }

    private void bootAnimStatus() {
        final String[] res = new String[1];
        busy("Checking…", () -> {
            res[0] = BootAnim.status();
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this).setTitle("Boot animation status").setMessage(res[0])
                    .setPositiveButton("Restore stock", (d, w) -> { Prefs.set(this, BootAnimFeatures.BOOTANIM, 0); thumbs.clear(); refresh(); applyBootAnim(); })
                    .setNegativeButton(android.R.string.ok, null).show());
        });
    }

    // ============================================================================================ setup

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String q = getIntent().getStringExtra(EXTRA_QUERY);
        cat = q != null ? Catalog.search(this, q) : Catalog.category(getIntent().getStringExtra(EXTRA_CATEGORY));
        if (cat == null) { finish(); return; }
        setContentView(R.layout.activity_category);
        CollapsingToolbarLayout col = findViewById(R.id.collapsing);
        col.setTitle(q != null ? "“" + q + "”" : cat.title);
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> finish());
        tb.getMenu().findItem(R.id.action_advanced).setChecked(UiPrefs.advanced(this));
        tb.getMenu().findItem(R.id.action_reset_page).setVisible(q == null);
        tb.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_advanced) { toggleAdvanced(item); return true; }
            if (item.getItemId() == R.id.action_reset_page) { resetPage(); return true; }
            return false;
        });
        screens = q != null ? null : LookPreview.screensFor(cat.id);
        strip = findViewById(R.id.strip);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        buildRows();
        list.setAdapter(new Adapter());
        if (screens != null) {
            strip.setOnClickListener(v -> { list.smoothScrollToPosition(0); ((com.google.android.material.appbar.AppBarLayout) findViewById(R.id.appbar)).setExpanded(true, true); });
            list.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) { updateStrip(); }
            });
            prefWatch = (sp, key) -> { if (key != null) { refreshPreview(); } };
            Prefs.get(this).registerOnSharedPreferenceChangeListener(prefWatch);
        }
        if (cat.advanced && !UiPrefs.advancedWarned(this)) {
            new MaterialAlertDialogBuilder(this).setTitle("Advanced")
                    .setMessage("These tweaks switch off protections (screenshots in banking apps, touches through overlays, the last warning before installing a risky APK) or let the phone run hotter than ZTE allows. Turn one on only when you know you need it.")
                    .setCancelable(false)
                    .setPositiveButton("I understand", (d, w) -> UiPrefs.setAdvancedWarned(this))
                    .setNegativeButton("Back", (d, w) -> finish()).show();
        }
    }

    @Override protected void onDestroy() {
        if (prefWatch != null) { try { Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefWatch); } catch (Throwable ignored) { } }
        pool.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onSettingsChanged() {
        if (getIntent().getStringExtra(EXTRA_QUERY) == null) cat = Catalog.category(cat.id);
        buildRows();
        refresh();
    }

    private void buildRows() {
        rows.clear();
        if (screens != null) rows.add(new Row(PREVIEW, null, null, false, false));
        if (cat.id.equals("search") && cat.groups.isEmpty()) rows.add(new Row(NOTE, "Nothing matches. Try another word, or turn on “Show advanced settings” in the menu.", null, true, true));
        List<Catalog.Group> groups = cat.id.equals("search") ? cat.groups : Catalog.shown(this, cat);
        for (Catalog.Group g : groups) {
            rows.add(new Row(SUBHEAD, g.title, null, false, false));
            for (int i = 0; i < g.items.size(); i++) rows.add(new Row(SETTING, null, g.items.get(i), i == 0, i == g.items.size() - 1));
        }
        if (!UiPrefs.advanced(this) && !cat.id.equals("search")) {
            int hidden = cat.items().size();
            for (Catalog.Group g : groups) hidden -= g.items.size();
            if (hidden > 0) rows.add(new Row(NOTE, hidden + (hidden == 1 ? " fine-tuning setting is" : " fine-tuning settings are") + " hidden. Menu › Show advanced settings shows them.", null, true, true));
        }
    }

    private void refresh() { if (list != null && list.getAdapter() != null) list.getAdapter().notifyDataSetChanged(); }

    /** Values the preview shows: the live settings (the Looks page: the One UI 8 look over them). */
    private Looks.V previewValues() {
        Looks.V live = Looks.live(this);
        return "looks".equals(cat.id) ? Looks.with(live, Presets.ONE_UI) : live;
    }

    private int previewWidthDp() { return Math.max(240, getResources().getConfiguration().screenWidthDp - 56); }

    private void refreshPreview() {
        if (list.getAdapter() != null && !rows.isEmpty() && rows.get(0).type == PREVIEW) list.getAdapter().notifyItemChanged(0);
        updateStrip();
    }

    /** The slim pinned strip shows once the big preview has scrolled out of sight. */
    private void updateStrip() {
        if (screens == null) return;
        LinearLayoutManager lm = (LinearLayoutManager) list.getLayoutManager();
        boolean gone = lm != null && lm.findFirstVisibleItemPosition() > 0;
        if (gone && strip.getVisibility() != View.VISIBLE) {
            strip.setImageDrawable(LookPreview.BAR.equals(screens[0]) ? LookPreview.strip(this, previewValues(), previewWidthDp())
                    : LookPreview.row(this, previewValues(), screens, previewWidthDp()));
            strip.setVisibility(View.VISIBLE);
        } else if (!gone && strip.getVisibility() == View.VISIBLE) strip.setVisibility(View.GONE);
    }

    private void resetPage() {
        new MaterialAlertDialogBuilder(this).setTitle("Reset " + cat.title + " to stock?")
                .setMessage("Every setting on this page goes back to stock (also the ones hidden in Simple mode).")
                .setPositiveButton("Reset", (d, w) -> {
                    for (Catalog.Item it : cat.items()) if (Catalog.changed(this, it)) { Catalog.reset(this, it); Pending.add(this, it); }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    // ============================================================================================ rows

    /** Text of the current value of a choice, or null for switches and actions. */
    private String valueText(Catalog.Item it) {
        Features.Feature f = it.feature;
        if (f.type == Features.Type.CHOICE) return f.choices[Catalog.choice(this, it)];
        if (f.type != Features.Type.ACTION) return null;
        if (Features.CHARGE_IMPORT.equals(f.key)) { int n = Frames.count(this); return n == 0 ? "Pick a folder" : n + " frames"; }
        if (FingerprintCustomFeatures.FP_IMPORT.equals(f.key)) { int n = Frames.fpCount(this); return n == 0 ? "Pick a folder" : n + " frames"; }
        if (CosmeticsFeatures.COS_FONT_IMPORT.equals(f.key)) return Frames.hasFont(this) ? "Imported" : "Pick a file";
        if (BootAnimFeatures.BOOTANIM_IMPORT.equals(f.key)) return BootAnim.customFile(this).isFile() ? "Imported" : "Pick a .zip";
        if (BootAnimFeatures.BOOTANIM_STATUS.equals(f.key)) return "Check";
        if (f.key.startsWith("preset_")) return f.key.startsWith("preset_one_ui") ? "Preview and apply" : "Preview and reset";
        if (Catalog.SAVED_LAYOUTS.equals(f.key)) { int n = com.hammad.redmagictweaks.LayoutStore.all(this).size(); return n == 0 ? "None yet" : n + " saved"; }
        return FlavorBits.actionLabel(f.key);
    }

    private void act(Catalog.Item it) {
        Features.Feature f = it.feature;
        if (Features.CHARGE_IMPORT.equals(f.key) || FingerprintCustomFeatures.FP_IMPORT.equals(f.key)) { pendingAction = f.key; pickFolder.launch(null); }
        else if (CosmeticsFeatures.COS_FONT_IMPORT.equals(f.key)) pickFont.launch(new String[]{"font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"});
        else if (BootAnimFeatures.BOOTANIM_IMPORT.equals(f.key)) pickBootZip.launch(new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        else if (BootAnimFeatures.BOOTANIM_STATUS.equals(f.key)) bootAnimStatus();
        else if (Catalog.SAVED_LAYOUTS.equals(f.key)) showLayouts();
        else if (f.key.startsWith("preset_")) {
            boolean apply = f.key.startsWith("preset_one_ui"), launcher = f.key.endsWith("_launcher");
            Object[][] set = launcher ? (apply ? Presets.ONE_UI_LAUNCHER : Presets.STOCK_LAUNCHER) : (apply ? Presets.ONE_UI : Presets.STOCK);
            showPreset(f.title, set, launcher);
        } else FlavorBits.runAction(this, f.key);
    }

    /** What it does, what it needs, and Reset to stock. */
    private void explain(Catalog.Item it) {
        String badge = Catalog.badge(it);
        String need = badge == null ? (it.restart == Catalog.Restart.LIVE ? "Applies without a restart." : "")
                : it.restart == Catalog.Restart.REBOOT ? "Needs a reboot to apply." : it.restart == Catalog.Restart.SYSTEM_UI ? "Needs a System UI restart to apply."
                : "Needs " + RestartBar.appName(it.packages().length > 0 ? it.packages()[0] : "") + " to restart to apply.";
        String desc = it.feature.desc == null ? "" : it.feature.desc;
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this).setTitle(it.feature.title)
                .setMessage(desc + (need.isEmpty() ? "" : (desc.isEmpty() ? "" : "\n\n") + need))
                .setPositiveButton(android.R.string.ok, null);
        if (Catalog.changed(this, it)) b.setNeutralButton("Reset to stock", (d, w) -> { Catalog.reset(this, it); Pending.add(this, it); refresh(); });
        b.show();
    }

    private void choose(Catalog.Item it) {
        new OptionSheet(this, it, screens != null && LookPreview.uses(it.key()) ? cat.id : null, (value, changed) -> {
            if (!changed) return;
            if ("fw_volume_boost".equals(it.key()) && value > 0) {
                new MaterialAlertDialogBuilder(this).setTitle("Volume boost")
                        .setMessage("Above the stock maximum the speakers can distort or wear out faster, and loud headphones can damage your hearing. Raise the volume slowly and turn the boost off if it crackles.")
                        .setPositiveButton("Turn it on", (d, w) -> applyChoice(it, value))
                        .setNegativeButton(android.R.string.cancel, null).show();
                return;
            }
            applyChoice(it, value);
        }).show();
    }

    private void applyChoice(Catalog.Item it, int value) {
        {
            Catalog.setChoice(this, it, value);
            Pending.add(this, it);
            if (BootAnimFeatures.BOOTANIM.equals(it.key())) applyBootAnim();
            thumbs.remove(it.key());
            refresh();
        }
    }

    private void loadThumb(Catalog.Item it, int index, ImageView target) {
        final String ck = it.key() + "/" + index;
        Drawable d = thumbs.get(ck);
        if (d != null || thumbs.containsKey(ck)) { target.setImageDrawable(d); return; }
        target.setImageDrawable(null);
        target.setTag(ck);
        final Previews.Provider pv = Previews.of(it.key());
        final android.content.Context app = getApplicationContext();
        pool.execute(() -> {
            Drawable r = null;
            try { r = pv == null ? null : pv.get(app, index); } catch (Throwable ignored) { }
            final Drawable res = r;
            runOnUiThread(() -> { thumbs.put(ck, res); if (ck.equals(target.getTag())) target.setImageDrawable(res); });
        });
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int pos) { return rows.get(pos).type; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            int layout = type == PREVIEW ? R.layout.item_preview : type == NOTE ? R.layout.item_banner : type == SUBHEAD ? R.layout.item_subhead : R.layout.item_setting;
            View v = inf.inflate(layout, parent, false);
            Ui.fonts(v);
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            Row row = rows.get(pos);
            View v = h.itemView;
            switch (row.type) {
                case PREVIEW: {
                    ImageView img = v.findViewById(R.id.preview);
                    img.setImageDrawable(LookPreview.BAR.equals(screens[0]) ? LookPreview.strip(CategoryActivity.this, previewValues(), previewWidthDp())
                            : LookPreview.row(CategoryActivity.this, previewValues(), screens, previewWidthDp()));
                    img.setOnClickListener(x -> PreviewDialogs.showAll(CategoryActivity.this, "looks".equals(cat.id) ? "One UI 8 look" : cat.title, previewValues()));
                    ((TextView) v.findViewById(R.id.preview_hint)).setText("looks".equals(cat.id)
                            ? "What the One UI 8 look gives with your settings. Tap for every screen."
                            : "Your current settings, drawn the way the phone shows them. Tap for every screen.");
                    break;
                }
                case NOTE:
                    ((TextView) v.findViewById(R.id.banner_text)).setText(row.text);
                    ((ImageView) v.findViewById(R.id.banner_icon)).setImageResource(R.drawable.ic_info_outline);
                    break;
                case SUBHEAD: ((TextView) v.findViewById(R.id.subhead)).setText(row.text); break;
                default: bindSetting(h, v, row); break;
            }
        }

        private void bindSetting(RecyclerView.ViewHolder h, View v, Row row) {
            final Catalog.Item it = row.item;
            final Features.Feature f = it.feature;
            v.setBackground(Ui.groupRow(CategoryActivity.this, row.first, row.last));
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.leftMargin = lp.rightMargin = Ui.dp(CategoryActivity.this, 4);
            lp.bottomMargin = row.last ? 0 : Ui.dp(CategoryActivity.this, 1);
            v.setLayoutParams(lp);
            ((TextView) v.findViewById(R.id.title)).setText(f.title);
            boolean changed = Catalog.changed(CategoryActivity.this, it);
            TextView value = v.findViewById(R.id.value);
            String val = valueText(it);
            value.setVisibility(val == null ? View.GONE : View.VISIBLE);
            if (val != null) value.setText(val);
            value.setTextColor(getColor(f.type == Features.Type.ACTION || changed ? R.color.accent : R.color.text_dim));
            v.findViewById(R.id.changed).setVisibility(changed && f.type != Features.Type.SWITCH ? View.VISIBLE : View.GONE);
            TextView badge = v.findViewById(R.id.badge);
            String b = Catalog.badge(it);
            badge.setVisibility(b == null ? View.GONE : View.VISIBLE);
            if (b != null) badge.setText(b);
            View info = v.findViewById(R.id.info);
            boolean hasDesc = f.desc != null && !f.desc.isEmpty();
            info.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
            info.setOnClickListener(x -> explain(it));
            v.setOnLongClickListener(x -> { explain(it); return true; });
            final MaterialSwitch sw = v.findViewById(R.id.toggle);
            ImageView thumb = v.findViewById(R.id.thumb);
            thumb.setVisibility(View.GONE);
            if (f.type == Features.Type.SWITCH) {
                sw.setVisibility(View.VISIBLE);
                sw.setOnCheckedChangeListener(null);
                sw.setChecked(Prefs.on(CategoryActivity.this, f.key));
                sw.setOnCheckedChangeListener((bt, checked) -> {
                    Prefs.set(CategoryActivity.this, f.key, checked);
                    Pending.add(CategoryActivity.this, it);
                    int p = h.getBindingAdapterPosition();
                    if (p != RecyclerView.NO_POSITION) notifyItemChanged(p);
                });
                v.setOnClickListener(x -> sw.toggle());
            } else {
                sw.setVisibility(View.GONE);
                if (f.type == Features.Type.CHOICE) {
                    if (Previews.has(f.key)) { thumb.setVisibility(View.VISIBLE); loadThumb(it, Catalog.choice(CategoryActivity.this, it), thumb); }
                    v.setOnClickListener(x -> choose(it));
                } else v.setOnClickListener(x -> act(it));
            }
        }
    }
}
