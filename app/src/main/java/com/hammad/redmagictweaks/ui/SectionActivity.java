package com.hammad.redmagictweaks.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.Frames;
import com.hammad.redmagictweaks.Prefs;
import com.hammad.redmagictweaks.Previews;
import com.google.android.material.snackbar.Snackbar;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import com.hammad.redmagictweaks.Root;
import com.hammad.redmagictweaks.R;

public class SectionActivity extends AppCompatActivity {
    private Features.Section section;
    /**
     * Sections with System UI looks start with a live preview of the current choices (LookPreview.screensFor); the
     * presets page shows what the One UI 8 look gives. Nothing is applied by it.
     */
    private boolean lockPreview;
    private String[] previewScreens;
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener prefWatch;
    private String pendingAction;
    private final ActivityResultLauncher<Uri> pickFolder = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                boolean fp = com.hammad.redmagictweaks.FingerprintCustomFeatures.FP_IMPORT.equals(pendingAction);
                int n = fp ? Frames.fpImportFolder(this, uri) : Frames.importFolder(this, uri);
                Toast.makeText(this, n == 0 ? "No image frames found in that folder" : n + " frames imported", Toast.LENGTH_LONG).show();
                refresh();
            });
    private final ActivityResultLauncher<String[]> pickFont = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                boolean ok = Frames.fontImport(this, uri);
                Toast.makeText(this, ok ? "Font imported — restart System UI to use it" : "Could not read that file", Toast.LENGTH_LONG).show();
                refresh();
            });

    private final ActivityResultLauncher<String[]> pickBootZip = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                busy("Importing…", () -> {
                    String err = com.hammad.redmagictweaks.BootAnim.importZip(this, uri);
                    runOnUiThread(() -> {
                        if (err != null) { new MaterialAlertDialogBuilder(this).setTitle("Import failed").setMessage(err).setPositiveButton(android.R.string.ok, null).show(); return; }
                        int custom = com.hammad.redmagictweaks.BootAnimFeatures.DEFS.length - 1;
                        Prefs.set(this, com.hammad.redmagictweaks.BootAnimFeatures.BOOTANIM, custom);
                        this.thumbs.clear(); refresh();
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

    private void applyBootAnim() {
        final int idx = Prefs.choice(this, com.hammad.redmagictweaks.BootAnimFeatures.BOOTANIM);
        final String[] res = new String[1];
        busy("Preparing the boot animation…", () -> {
            res[0] = com.hammad.redmagictweaks.BootAnim.apply(getApplicationContext(), idx);
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this).setTitle("Boot animation").setMessage(res[0])
                    .setPositiveButton("Reboot now", (d, w) -> new Thread(Root::reboot).start())
                    .setNegativeButton("Later", null).show());
        });
    }

    private void bootAnimStatus() {
        final String[] res = new String[1];
        busy("Checking…", () -> {
            res[0] = com.hammad.redmagictweaks.BootAnim.status();
            runOnUiThread(() -> new MaterialAlertDialogBuilder(this).setTitle("Boot animation status").setMessage(res[0])
                    .setPositiveButton("Restore stock", (d, w) -> { Prefs.set(this, com.hammad.redmagictweaks.BootAnimFeatures.BOOTANIM, 0); thumbs.clear(); refresh(); applyBootAnim(); })
                    .setNegativeButton(android.R.string.ok, null).show());
        });
    }

    private final java.util.Map<String, Drawable> thumbs = new java.util.HashMap<>();
    private final java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<Intent> pickVisual = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                thumbs.clear();
                refresh();
                if (r.getData() != null && r.getData().getBooleanExtra(PickerActivity.RESULT_CHANGED, false)) {
                    Features.Feature f = Features.feature(r.getData().getStringExtra(PickerActivity.EXTRA_KEY));
                    if (f != null) offerRestart(f);
                }
            });
    @Override protected void onDestroy() {
        if (prefWatch != null) { try { Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefWatch); } catch (Throwable ignored) { } }
        super.onDestroy();
        pool.shutdownNow();
    }

    /** What a feature needs after a change, derived from its description (same words the tags use). */
    private static String needs(Features.Feature f) {
        String d = f.desc == null ? "" : f.desc;
        if (d.contains("Reboot")) return "REBOOT";
        if (d.contains("Restart System UI") || d.contains("restart System UI") || d.contains("System UI restart")) return "SYSTEM UI RESTART";
        if (d.contains("estart")) return "APP RESTART";
        return null;
    }

    /** After a change: a snackbar with the matching restart action (per the user's request: fix + restart from the tweak itself). */
    private void offerRestart(Features.Feature f) {
        if (com.hammad.redmagictweaks.BootAnimFeatures.BOOTANIM.equals(f.key)) { applyBootAnim(); return; }
        String n = needs(f);
        if (n == null) return;
        View root = findViewById(R.id.list);
        if ("REBOOT".equals(n)) {
            Snackbar.make(root, "Takes effect after a reboot", Snackbar.LENGTH_LONG).setAction("Reboot", v -> new Thread(() -> Root.reboot()).start()).show();
        } else if ("SYSTEM UI RESTART".equals(n)) {
            Snackbar.make(root, "Takes effect after System UI restarts", Snackbar.LENGTH_LONG).setAction("Restart", v -> rootRestart("com.android.systemui")).show();
        } else {
            java.util.List<String> pk = new java.util.ArrayList<>(java.util.Arrays.asList(section.packages)); pk.remove("android");
            if (pk.isEmpty()) return;
            final String[] pkgs = pk.toArray(new String[0]);
            Snackbar.make(root, "Takes effect after the app restarts", Snackbar.LENGTH_LONG).setAction("Restart", v -> rootRestart(pkgs)).show();
        }
    }

    private void rootRestart(String... pkgs) {
        new Thread(() -> {
            Root.Result r = Root.restartApps(pkgs);
            runOnUiThread(() -> Toast.makeText(this, r.ok() ? "Restarted" : "Root command failed: " + r.out, Toast.LENGTH_LONG).show());
        }).start();
    }

    private void refresh() {
        RecyclerView list = findViewById(R.id.list);
        if (list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
    }

    /** Search mode: a virtual section made of every feature whose title/description matches. */
    private static Features.Section searchSection(String q) {
        String needle = q.toLowerCase(java.util.Locale.ROOT);
        java.util.List<Features.Feature> hits = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> pkgs = new java.util.LinkedHashSet<>();
        for (Features.Section s : Features.SECTIONS) {
            for (Features.Feature f : s.features) {
                if (f.title.toLowerCase(java.util.Locale.ROOT).contains(needle) || (f.desc != null && f.desc.toLowerCase(java.util.Locale.ROOT).contains(needle)) || s.title.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    hits.add(f); java.util.Collections.addAll(pkgs, s.packages);
                }
            }
        }
        return new Features.Section("search", "Search: " + q, hits.isEmpty() ? "Nothing matches" : hits.size() + " matching tweaks", R.drawable.ic_search,
                pkgs.toArray(new String[0]), hits.toArray(new Features.Feature[0]));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String q = getIntent().getStringExtra("query");
        section = q != null ? searchSection(q) : Features.section(getIntent().getStringExtra("section"));
        if (section == null) { finish(); return; }
        setContentView(R.layout.activity_section);
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setTitle(section.title);
        tb.setNavigationOnClickListener(v -> finish());
        tb.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_restart) {
                boolean fw = java.util.Arrays.asList(section.packages).contains("android");
                if (fw && section.packages.length == 1) {
                    new MaterialAlertDialogBuilder(this).setTitle("Needs a reboot").setMessage("Framework tweaks live in the system server — use Soft reboot or Reboot from the main menu.").setPositiveButton(android.R.string.ok, null).show();
                    return true;
                }
                Toast.makeText(this, "Restarting " + section.title + " apps…", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    java.util.List<String> pk = new java.util.ArrayList<>(java.util.Arrays.asList(section.packages)); pk.remove("android");
                    Root.Result r = Root.restartApps(pk.toArray(new String[0]));
                    runOnUiThread(() -> Toast.makeText(this, r.ok() ? "Restarted — open the app again" : "Root command failed: " + r.out, Toast.LENGTH_LONG).show());
                }).start();
                return true;
            }
            if (id == R.id.action_reset) {
                new MaterialAlertDialogBuilder(this).setTitle("Reset to stock?").setMessage("Every switch on this page goes back to off / stock.")
                        .setPositiveButton("Reset", (d, w) -> {
                            for (Features.Feature f : section.features) { if (f.type == Features.Type.SWITCH) Prefs.set(this, f.key, false); else if (f.type == Features.Type.CHOICE) Prefs.set(this, f.key, 0); }
                            refresh();
                        }).setNegativeButton(android.R.string.cancel, null).show();
                return true;
            }
            return false;
        });
        String hintText = section.subtitle + (section.subtitle.isEmpty() ? "" : "\n") + "Apps: " + String.join(", ", section.packages);
        ((TextView) findViewById(R.id.hint)).setText(hintText);
        findViewById(R.id.hint).setVisibility(com.hammad.redmagictweaks.UiPrefs.clean(this) ? View.GONE : View.VISIBLE);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        previewScreens = com.hammad.redmagictweaks.LookPreview.screensFor(section.id);
        lockPreview = previewScreens != null;
        list.setAdapter(new FeatureAdapter());
        if (lockPreview) {
            prefWatch = (sp, key) -> {
                if (key != null && list.getAdapter() != null) list.getAdapter().notifyItemChanged(0);
            };
            android.content.SharedPreferences sp = Prefs.get(this);
            if (sp != null) sp.registerOnSharedPreferenceChangeListener(prefWatch);
        }
    }

    /** Values the header picture shows: the live settings, or for the presets page the One UI 8 preset laid over them. */
    private com.hammad.redmagictweaks.Looks.V previewValues() {
        com.hammad.redmagictweaks.Looks.V live = com.hammad.redmagictweaks.Looks.live(this);
        return "presets".equals(section.id) ? com.hammad.redmagictweaks.Looks.with(live, com.hammad.redmagictweaks.Presets.ONE_UI) : live;
    }

    private int cardWidthDp() { return Math.max(240, getResources().getConfiguration().screenWidthDp - 64); }

    /**
     * Choice dialog with the preview on top: tapping an option redraws the picture with that option (nothing saved);
     * Apply saves it.
     */
    private void tryDialog(final Features.Feature f, final int current, final Runnable changed) {
        final int[] sel = {current};
        final int w = PreviewDialogs.dialogWidthDp(this);
        View head = PreviewDialogs.header(this, f.title, "Tap an option to see it here before applying.",
                com.hammad.redmagictweaks.LookPreview.section(this, section.id, com.hammad.redmagictweaks.Looks.with(com.hammad.redmagictweaks.Looks.live(this), f.key, current), w),
                x -> PreviewDialogs.showAll(this, f.choices[sel[0]], com.hammad.redmagictweaks.Looks.with(com.hammad.redmagictweaks.Looks.live(this), f.key, sel[0])));
        final android.widget.ImageView img = head.findViewWithTag("preview");
        new MaterialAlertDialogBuilder(this)
                .setCustomTitle(head)
                .setSingleChoiceItems(f.choices, current, (d, which) -> {
                    sel[0] = which;
                    if (img != null) img.setImageDrawable(com.hammad.redmagictweaks.LookPreview.section(this, section.id, com.hammad.redmagictweaks.Looks.with(com.hammad.redmagictweaks.Looks.live(this), f.key, which), w));
                })
                .setPositiveButton("Apply", (d, which) -> {
                    Prefs.set(this, f.key, sel[0]);
                    changed.run();
                    if (sel[0] != current) offerRestart(f);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** "Preview" card at the top of the lock screen pages. */
    private View previewCard(ViewGroup parent) {
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout card = new android.widget.LinearLayout(this);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        card.setPadding((int) (16 * d), (int) (14 * d), (int) (16 * d), (int) (14 * d));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(20 * d);
        bg.setColor(getColor(R.color.surface));
        card.setBackground(bg);
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (12 * d);
        card.setLayoutParams(lp);
        TextView title = new TextView(this);
        title.setText("presets".equals(section.id) ? "Preview: One UI 8 look" : "Preview");
        title.setTextColor(getColor(R.color.text));
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        card.addView(title);
        TextView sub = new TextView(this);
        sub.setText("presets".equals(section.id)
                ? "What \"One UI 8 look\" gives you, drawn from your settings with the preset on top. Nothing changes until you apply it. Tap the picture for every screen."
                : "Your current choices, drawn the way System UI will show them. Tap a setting to try its options here before applying; System UI picks changes up after a restart. Tap the picture for every screen.");
        sub.setTextColor(getColor(R.color.text_dim));
        sub.setTextSize(12.5f);
        sub.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        sub.setPadding(0, (int) (4 * d), 0, (int) (12 * d));
        card.addView(sub);
        android.widget.ImageView img = new android.widget.ImageView(this);
        img.setTag("lock_preview");
        img.setContentDescription("Preview");
        img.setOnClickListener(x -> PreviewDialogs.showAll(this, "presets".equals(section.id) ? "One UI 8 look" : section.title, previewValues()));
        card.addView(img, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void loadThumb(Features.Feature f, int index, android.widget.ImageView target) {
        String ck = f.key + "/" + index;
        Drawable d = thumbs.get(ck);
        if (d != null || thumbs.containsKey(ck)) { target.setImageDrawable(d); return; }
        target.setImageDrawable(null); target.setTag(ck);
        final Previews.Provider pv = Previews.of(f.key);
        final android.content.Context app = getApplicationContext();
        pool.execute(() -> {
            Drawable r = null;
            try { r = pv == null ? null : pv.get(app, index); } catch (Throwable ignored) { }
            final Drawable res = r;
            runOnUiThread(() -> { thumbs.put(ck, res); if (ck.equals(target.getTag())) target.setImageDrawable(res); });
        });
    }

    private final class FeatureAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return section.features.length + (lockPreview ? 1 : 0); }

        @Override public int getItemViewType(int pos) { return lockPreview && pos == 0 ? 1 : 0; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 1) return new RecyclerView.ViewHolder(previewCard(parent)) {};
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feature, parent, false);
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            if (lockPreview) {
                if (pos == 0) {
                    android.widget.ImageView img = h.itemView.findViewWithTag("lock_preview");
                    if (img != null) img.setImageDrawable(com.hammad.redmagictweaks.LookPreview.section(SectionActivity.this, section.id, previewValues(), cardWidthDp()));
                    return;
                }
                pos--;
            }
            final Features.Feature f = section.features[pos];
            View v = h.itemView;
            ((TextView) v.findViewById(R.id.title)).setText(f.title);
            TextView desc = v.findViewById(R.id.desc);
            desc.setText(f.desc);
            final boolean clean = com.hammad.redmagictweaks.UiPrefs.clean(SectionActivity.this);
            desc.setVisibility(clean || f.desc == null || f.desc.isEmpty() ? View.GONE : View.VISIBLE);
            View card0 = v.findViewById(R.id.card);
            card0.setOnLongClickListener(clean && f.desc != null && !f.desc.isEmpty() ? x -> {
                new MaterialAlertDialogBuilder(SectionActivity.this).setTitle(f.title).setMessage(f.desc).setPositiveButton(android.R.string.ok, null).show();
                return true;
            } : null);
            View inner = ((ViewGroup) card0).getChildAt(0);
            int ph = (int) (getResources().getDisplayMetrics().density * (clean ? 14 : 18)), pv = (int) (getResources().getDisplayMetrics().density * (clean ? 9 : 14));
            inner.setPadding(ph, pv, (int) (getResources().getDisplayMetrics().density * 14), pv);
            TextView tag = v.findViewById(R.id.tag);
            String t = needs(f);
            tag.setVisibility(t == null || clean ? View.GONE : View.VISIBLE);
            if (t != null) tag.setText(t);
            final MaterialSwitch sw = v.findViewById(R.id.toggle);
            final TextView value = v.findViewById(R.id.value);
            final android.widget.ImageView thumb = v.findViewById(R.id.thumb);
            thumb.setVisibility(View.GONE);
            View card = v.findViewById(R.id.card);
            if (f.type == Features.Type.ACTION) {
                sw.setVisibility(View.GONE);
                value.setVisibility(View.VISIBLE);
                if (Features.CHARGE_IMPORT.equals(f.key)) {
                    int n = Frames.count(SectionActivity.this);
                    value.setText(n == 0 ? "Pick folder" : n + " frames");
                    card.setOnClickListener(x -> { pendingAction = f.key; pickFolder.launch(null); });
                } else if (com.hammad.redmagictweaks.FingerprintCustomFeatures.FP_IMPORT.equals(f.key)) {
                    int n = Frames.fpCount(SectionActivity.this);
                    value.setText(n == 0 ? "Pick folder" : n + " frames");
                    card.setOnClickListener(x -> { pendingAction = f.key; pickFolder.launch(null); });
                } else if (com.hammad.redmagictweaks.CosmeticsFeatures.COS_FONT_IMPORT.equals(f.key)) {
                    value.setText(Frames.hasFont(SectionActivity.this) ? "Imported" : "Pick file");
                    card.setOnClickListener(x -> pickFont.launch(new String[]{"font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"}));
                } else if (com.hammad.redmagictweaks.BootAnimFeatures.BOOTANIM_IMPORT.equals(f.key)) {
                    value.setText(com.hammad.redmagictweaks.BootAnim.customFile(SectionActivity.this).isFile() ? "Imported" : "Pick .zip");
                    card.setOnClickListener(x -> pickBootZip.launch(new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"}));
                } else if (com.hammad.redmagictweaks.BootAnimFeatures.BOOTANIM_STATUS.equals(f.key)) {
                    value.setText("Check");
                    card.setOnClickListener(x -> bootAnimStatus());
                } else if (f.key.startsWith("preset_")) {
                    final boolean apply = f.key.startsWith("preset_one_ui");
                    final boolean launcher = f.key.endsWith("_launcher");
                    final Object[][] set = launcher ? (apply ? com.hammad.redmagictweaks.Presets.ONE_UI_LAUNCHER : com.hammad.redmagictweaks.Presets.STOCK_LAUNCHER)
                                                    : (apply ? com.hammad.redmagictweaks.Presets.ONE_UI : com.hammad.redmagictweaks.Presets.STOCK);
                    value.setText(apply ? "Apply" : "Reset");
                    card.setOnClickListener(x -> new MaterialAlertDialogBuilder(SectionActivity.this)
                            .setCustomTitle(launcher ? PreviewDialogs.header(SectionActivity.this, f.title, null, null, null)
                                    : PreviewDialogs.header(SectionActivity.this, f.title, "After applying (tap for every screen):",
                                        com.hammad.redmagictweaks.LookPreview.row(SectionActivity.this, com.hammad.redmagictweaks.Looks.with(com.hammad.redmagictweaks.Looks.live(SectionActivity.this), set),
                                                new String[]{com.hammad.redmagictweaks.LookPreview.LOCK, com.hammad.redmagictweaks.LookPreview.SHADE, com.hammad.redmagictweaks.LookPreview.CC}, PreviewDialogs.dialogWidthDp(SectionActivity.this)),
                                        y -> PreviewDialogs.showAll(SectionActivity.this, f.title, com.hammad.redmagictweaks.Looks.with(com.hammad.redmagictweaks.Looks.live(SectionActivity.this), set))))
                            .setMessage(apply ? "Apply this preset now? It overwrites the related settings." : "Reset those settings to stock?")
                            .setPositiveButton(apply ? "Apply" : "Reset", (d, w) -> {
                                com.hammad.redmagictweaks.Presets.apply(SectionActivity.this, set);
                                final String pkg = launcher ? "com.zte.mifavor.launcher" : "com.android.systemui";
                                Snackbar.make(findViewById(R.id.list), launcher ? "Applied — restart the launcher" : "Applied — restart System UI", Snackbar.LENGTH_LONG)
                                        .setAction("Restart", sv -> rootRestart(pkg)).show();
                            }).setNegativeButton(android.R.string.cancel, null).show());
                } else {
                    value.setText(com.hammad.redmagictweaks.FlavorBits.actionLabel(f.key));
                    card.setOnClickListener(x -> com.hammad.redmagictweaks.FlavorBits.runAction(SectionActivity.this, f.key));
                }
            } else if (f.type == Features.Type.SWITCH) {
                sw.setVisibility(View.VISIBLE);
                value.setVisibility(View.GONE);
                sw.setOnCheckedChangeListener(null);
                sw.setChecked(Prefs.on(SectionActivity.this, f.key));
                sw.setOnCheckedChangeListener((b, checked) -> { Prefs.set(SectionActivity.this, f.key, checked); offerRestart(f); });
                card.setOnClickListener(x -> sw.toggle());
            } else {
                sw.setVisibility(View.GONE);
                value.setVisibility(View.VISIBLE);
                int idx = Prefs.choice(SectionActivity.this, f.key);
                if (idx < 0 || idx >= f.choices.length) idx = 0;
                value.setText(f.choices[idx]);
                final int current = idx;
                if (Previews.has(f.key)) {
                    thumb.setVisibility(View.VISIBLE);
                    loadThumb(f, current, thumb);
                    card.setOnClickListener(x -> pickVisual.launch(new Intent(SectionActivity.this, PickerActivity.class).putExtra(PickerActivity.EXTRA_KEY, f.key)));
                } else if (lockPreview && com.hammad.redmagictweaks.LookPreview.uses(f.key)) {
                    card.setOnClickListener(x -> tryDialog(f, current, () -> notifyItemChanged(h.getBindingAdapterPosition())));
                } else
                card.setOnClickListener(x -> new MaterialAlertDialogBuilder(SectionActivity.this)
                        .setTitle(f.title)
                        .setSingleChoiceItems(f.choices, current, (d, which) -> {
                            Prefs.set(SectionActivity.this, f.key, which);
                            notifyItemChanged(h.getBindingAdapterPosition());
                            d.dismiss();
                            if (which != current) offerRestart(f);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show());
            }
            v.findViewById(R.id.choice_row).setVisibility(value.getVisibility() == View.VISIBLE || thumb.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
        }
    }
}
