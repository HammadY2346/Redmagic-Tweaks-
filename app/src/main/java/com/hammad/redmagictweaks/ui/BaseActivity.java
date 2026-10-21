package com.hammad.redmagictweaks.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hammad.redmagictweaks.BuildConfig;
import com.hammad.redmagictweaks.Catalog;
import com.hammad.redmagictweaks.LayoutStore;
import com.hammad.redmagictweaks.LookPreview;
import com.hammad.redmagictweaks.Looks;
import com.hammad.redmagictweaks.Pending;
import com.hammad.redmagictweaks.Presets;
import com.hammad.redmagictweaks.R;
import com.hammad.redmagictweaks.Root;
import com.hammad.redmagictweaks.UiPrefs;

import java.util.List;

/**
 * Shared by the screens: the app font, the restart bar, Simple / Advanced, and looks / saved layouts (previews, apply
 * with an automatic backup, export and import as files).
 */
public abstract class BaseActivity extends AppCompatActivity {
    protected RestartBar restartBar;

    /** Settings changed from a dialog (a look or layout applied, a reset): the screen redraws. */
    protected abstract void onSettingsChanged();

    @Override public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        Ui.fonts(findViewById(android.R.id.content));
        android.view.View col = findViewById(R.id.collapsing);
        if (col instanceof com.google.android.material.appbar.CollapsingToolbarLayout) {
            com.google.android.material.appbar.CollapsingToolbarLayout ctl = (com.google.android.material.appbar.CollapsingToolbarLayout) col;
            ctl.setExpandedTitleTypeface(Ui.font(this, 600));
            ctl.setCollapsedTitleTypeface(Ui.font(this, 600));
        }
        if (findViewById(R.id.restart_bar) != null) restartBar = new RestartBar(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if (restartBar != null) restartBar.update();
    }

    @Override protected void onDestroy() {
        if (restartBar != null) restartBar.destroy();
        super.onDestroy();
    }

    protected void toggleAdvanced(MenuItem item) {
        boolean v = !UiPrefs.advanced(this);
        UiPrefs.setAdvanced(this, v);
        item.setChecked(v);
        Toast.makeText(this, v ? "Advanced: fine-tuning and the Advanced category are shown" : "Simple: fine-tuning is hidden", Toast.LENGTH_SHORT).show();
        onSettingsChanged();
    }

    protected void confirm(String title, String msg, Runnable action) {
        new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(msg)
                .setPositiveButton("Go", (d, w) -> new Thread(action).start())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    protected void rootAction(Root.Result r) {
        runOnUiThread(() -> Toast.makeText(this, r.ok() ? "Done" : "Root command failed: " + r.out, Toast.LENGTH_LONG).show());
    }

    // ============================================================================================ looks

    static final String AUTO_BACKUP = "Auto-backup (before the last apply)";

    /** A look (preset) as a picture first; Apply saves the current setup as the auto-backup, then applies. */
    protected void showPreset(final String title, final Object[][] preset, final boolean launcher) {
        final Looks.V look = Looks.with(Looks.live(this), preset);
        String[] screens = launcher ? new String[]{LookPreview.RECENTS} : new String[]{LookPreview.LOCK, LookPreview.SHADE, LookPreview.CC};
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this)
                .setCustomTitle(PreviewDialogs.header(this, title, launcher ? null : "After applying · tap the picture for every screen",
                        launcher ? null : LookPreview.row(this, look, screens, PreviewDialogs.dialogWidthDp(this)),
                        launcher ? null : x -> PreviewDialogs.showAll(this, title, look)))
                .setMessage(preset == Presets.STOCK || preset == Presets.STOCK_LAUNCHER
                        ? "Every setting of this look goes back to stock. Your current setup is saved first, so you can go back from Saved layouts."
                        : "Changes the settings this look uses; everything else stays. Your current setup is saved first, so you can go back from Saved layouts.")
                .setPositiveButton("Apply", (d, w) -> {
                    try { LayoutStore.saveCurrent(this, AUTO_BACKUP); } catch (Throwable ignored) { }
                    Presets.apply(this, preset);
                    for (Object[] e : preset) Pending.add(this, Catalog.item((String) e[0]));
                    onSettingsChanged();
                    Toast.makeText(this, title + " applied", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null);
        b.show();
    }

    // ============================================================================================ saved layouts

    private LayoutStore.Layout pendingExport;
    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
        LayoutStore.Layout l = pendingExport; pendingExport = null;
        if (uri == null || l == null) return;
        try (java.io.OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            out.write(LayoutStore.export(l).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Toast.makeText(this, "Exported \"" + l.name + "\"", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) { Toast.makeText(this, "Export failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); }
    });
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri == null) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) { buf.write(b, 0, n); if (buf.size() > 4 * 1024 * 1024) throw new java.io.IOException("file too large"); }
            LayoutStore.Layout l = LayoutStore.parse(buf.toString("UTF-8"));
            if (l == null) { Toast.makeText(this, "That file isn't a layout from " + BuildConfig.APP_NAME, Toast.LENGTH_LONG).show(); return; }
            LayoutStore.put(this, l);
            Toast.makeText(this, "Imported \"" + l.name + "\" (" + l.count() + " settings)", Toast.LENGTH_SHORT).show();
            onSettingsChanged();
            showLayouts();
        } catch (Throwable t) { Toast.makeText(this, "Import failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); }
    });

    protected void showLayouts() {
        final List<LayoutStore.Layout> all = LayoutStore.all(this);
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this).setTitle("Saved layouts");
        if (all.isEmpty()) {
            b.setMessage("Save everything you've set up (Control Center, status bar, launcher, looks…) as a named layout, then switch between layouts or go back to one any time. Layouts can also be exported to a file to keep or share.");
        } else {
            CharSequence[] items = new CharSequence[all.size()];
            for (int i = 0; i < all.size(); i++) {
                LayoutStore.Layout l = all.get(i);
                items[i] = l.name + "\n" + l.count() + " settings · " + df.format(new java.util.Date(l.saved));
            }
            b.setItems(items, (d, which) -> layoutActions(all.get(which)));
        }
        b.setPositiveButton("Save current…", (d, w) -> saveCurrent())
                .setNeutralButton("Import…", (d, w) -> importLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"}))
                .setNegativeButton("Close", null)
                .show();
    }

    protected void saveCurrent() {
        nameDialog("Save current layout", "", name -> {
            try { LayoutStore.saveCurrent(this, name); Toast.makeText(this, "Saved \"" + name + "\"", Toast.LENGTH_SHORT).show(); onSettingsChanged(); }
            catch (Throwable t) { Toast.makeText(this, "Save failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); }
        });
    }

    protected void layoutActions(final LayoutStore.Layout l) {
        String[] acts = {"Apply this layout", "Update it with my current settings", "Rename", "Export to a file", "Delete"};
        final Looks.V look = Looks.of(l);
        android.view.View head = PreviewDialogs.header(this, l.name, l.count() + " settings · tap the picture for every screen",
                LookPreview.row(this, look, new String[]{LookPreview.LOCK, LookPreview.SHADE, LookPreview.CC}, PreviewDialogs.dialogWidthDp(this)),
                x -> PreviewDialogs.showAll(this, l.name, look));
        new MaterialAlertDialogBuilder(this).setCustomTitle(head).setItems(acts, (d, which) -> {
            try {
                switch (which) {
                    case 0:
                        new MaterialAlertDialogBuilder(this).setTitle("Apply \"" + l.name + "\"?")
                                .setMessage("All settings are replaced by this layout. Your current settings are saved first as \"" + AUTO_BACKUP + "\", so you can go back.")
                                .setPositiveButton("Apply", (d2, w2) -> applyLayout(l))
                                .setNegativeButton(android.R.string.cancel, null).show();
                        break;
                    case 1:
                        LayoutStore.saveCurrent(this, l.name);
                        Toast.makeText(this, "Updated \"" + l.name + "\"", Toast.LENGTH_SHORT).show();
                        break;
                    case 2:
                        nameDialog("Rename layout", l.name, name -> {
                            try { LayoutStore.rename(this, l.name, name); onSettingsChanged(); showLayouts(); } catch (Throwable t) { Toast.makeText(this, "Rename failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); }
                        });
                        break;
                    case 3:
                        pendingExport = l;
                        exportLauncher.launch(BuildConfig.DATA_ID + "-" + l.name.replaceAll("[^A-Za-z0-9._-]+", "_") + ".json");
                        break;
                    default:
                        new MaterialAlertDialogBuilder(this).setTitle("Delete \"" + l.name + "\"?")
                                .setPositiveButton("Delete", (d2, w2) -> { try { LayoutStore.delete(this, l.name); onSettingsChanged(); showLayouts(); } catch (Throwable ignored) { } })
                                .setNegativeButton(android.R.string.cancel, null).show();
                }
            } catch (Throwable t) { Toast.makeText(this, t.getMessage(), Toast.LENGTH_LONG).show(); }
        }).setNegativeButton("Back", (d, w) -> showLayouts()).show();
    }

    protected void applyLayout(LayoutStore.Layout l) {
        try {
            if (!l.name.equals(AUTO_BACKUP)) LayoutStore.saveCurrent(this, AUTO_BACKUP);
            LayoutStore.apply(this, l);
        } catch (Throwable t) { Toast.makeText(this, "Apply failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); return; }
        Pending.addEverything(this);
        onSettingsChanged();
        Toast.makeText(this, "\"" + l.name + "\" applied", Toast.LENGTH_SHORT).show();
    }

    interface NameCallback { void onName(String name); }

    protected void nameDialog(String title, String initial, NameCallback cb) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(initial);
        et.setHint("e.g. One UI daily, Gaming, Stock");
        et.setSelectAllOnFocus(true);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        int p = Ui.dp(this, 20);
        box.setPadding(p, p / 2, p, 0);
        box.addView(et);
        new MaterialAlertDialogBuilder(this).setTitle(title).setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) { Toast.makeText(this, "Give the layout a name", Toast.LENGTH_SHORT).show(); return; }
                    cb.onName(n);
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    @Override protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); }
}
