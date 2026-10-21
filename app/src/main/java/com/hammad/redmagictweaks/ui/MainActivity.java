package com.hammad.redmagictweaks.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.LayoutStore;
import com.hammad.redmagictweaks.LogStore;
import com.hammad.redmagictweaks.ModuleStatus;
import com.hammad.redmagictweaks.Prefs;
import com.hammad.redmagictweaks.R;
import com.hammad.redmagictweaks.Root;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView list;
    private String rootState = "…";

    /** Home rows: hero, group headers, section cards, logs card. */
    private static final class Row {
        static final int HERO = 0, GROUP = 1, SECTION = 2, LOGS = 3, LAYOUTS = 4;
        final int type; final String label; final Features.Section section;
        Row(int type, String label, Features.Section section) { this.type = type; this.label = label; this.section = section; }
    }
    private final List<Row> rows = new ArrayList<>();

    private static String groupOf(Features.Section s) {
        String id = s.id.startsWith(Features.TOPIC_PREFIX) ? s.id.substring(Features.TOPIC_PREFIX.length()) : s.id;
        switch (id) {
            case "battery": case "lock": return "Topics";
            case "presets": return "Looks";
            case "game": return "Gaming";
            case "launcher": case "theme": case "themestore": case "fpcustom": case "cosmetics": case "lockscreen": case "statusbar": case "sbclock": case "sbicons": case "qs": case "surface": case "systemui": case "bootanim": return "Look & feel";
            default: return "System";
        }
    }

    private void buildRows() {
        rows.clear();
        rows.add(new Row(Row.HERO, null, null));
        String last = null;
        for (Features.Section s : Features.visible(this)) {
            String g = groupOf(s);
            if (!g.equals(last)) { rows.add(new Row(Row.GROUP, g, null)); last = g; }
            rows.add(new Row(Row.SECTION, null, s));
            if ("presets".equals(s.id)) rows.add(new Row(Row.LAYOUTS, null, null));
        }
        rows.add(new Row(Row.GROUP, "Debug", null));
        rows.add(new Row(Row.LOGS, null, null));
    }

    // ------------------------------------------------------------------ saved layouts (LayoutStore)
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
            if (l == null) { Toast.makeText(this, ("That file isn't a layout from " + com.hammad.redmagictweaks.BuildConfig.APP_NAME), Toast.LENGTH_LONG).show(); return; }
            LayoutStore.put(this, l);
            Toast.makeText(this, "Imported \"" + l.name + "\" (" + l.count() + " settings)", Toast.LENGTH_SHORT).show();
            refreshHome();
            showLayouts();
        } catch (Throwable t) { Toast.makeText(this, "Import failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); }
    });

    private void refreshHome() { if (list != null && list.getAdapter() != null) list.getAdapter().notifyDataSetChanged(); }

    private static final String AUTO_BACKUP = "Auto-backup (before the last apply)";

    private void showLayouts() {
        final List<LayoutStore.Layout> all = LayoutStore.all(this);
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this).setTitle("Saved layouts");
        if (all.isEmpty()) {
            b.setMessage(("Save everything you've set up in " + com.hammad.redmagictweaks.BuildConfig.APP_NAME + " (Control Center, status bar, launcher, presets…) as a named layout, then switch between layouts or go back to one any time. Layouts can also be exported to a file to keep or share."));
        } else {
            CharSequence[] items = new CharSequence[all.size()];
            for (int i = 0; i < all.size(); i++) {
                LayoutStore.Layout l = all.get(i);
                items[i] = l.name + "\n" + l.count() + " settings · " + df.format(new java.util.Date(l.saved));
            }
            b.setItems(items, (d, which) -> layoutActions(all.get(which)));
        }
        b.setPositiveButton("Save current…", (d, w) -> nameDialog("Save current layout", "", name -> {
                    try { LayoutStore.saveCurrent(this, name); Toast.makeText(this, "Saved \"" + name + "\"", Toast.LENGTH_SHORT).show(); refreshHome(); showLayouts(); }
                    catch (Throwable t) { Toast.makeText(this, "Save failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); }
                }))
                .setNeutralButton("Import…", (d, w) -> importLauncher.launch(new String[]{"application/json", "text/plain", "application/octet-stream"}))
                .setNegativeButton("Close", null)
                .show();
    }

    private void layoutActions(final LayoutStore.Layout l) {
        String[] acts = {"Apply this layout", "Update it with my current settings", "Rename", "Export to a file", "Delete"};
        final com.hammad.redmagictweaks.Looks.V look = com.hammad.redmagictweaks.Looks.of(l);
        android.view.View head = PreviewDialogs.header(this, l.name, l.count() + " settings · tap the picture for every screen",
                com.hammad.redmagictweaks.LookPreview.row(this, look, new String[]{com.hammad.redmagictweaks.LookPreview.LOCK, com.hammad.redmagictweaks.LookPreview.SHADE, com.hammad.redmagictweaks.LookPreview.CC}, PreviewDialogs.dialogWidthDp(this)),
                x -> PreviewDialogs.showAll(this, l.name, look));
        new MaterialAlertDialogBuilder(this).setCustomTitle(head).setItems(acts, (d, which) -> {
            try {
                switch (which) {
                    case 0:
                        new MaterialAlertDialogBuilder(this).setTitle("Apply \"" + l.name + "\"?")
                                .setMessage(("All " + com.hammad.redmagictweaks.BuildConfig.APP_NAME + " settings are replaced by this layout. Your current settings are saved first as \"") + AUTO_BACKUP + "\", so you can go back.")
                                .setPositiveButton("Apply", (d2, w2) -> applyLayout(l))
                                .setNegativeButton(android.R.string.cancel, null).show();
                        break;
                    case 1:
                        LayoutStore.saveCurrent(this, l.name);
                        Toast.makeText(this, "Updated \"" + l.name + "\"", Toast.LENGTH_SHORT).show();
                        break;
                    case 2:
                        nameDialog("Rename layout", l.name, name -> {
                            try { LayoutStore.rename(this, l.name, name); showLayouts(); } catch (Throwable t) { Toast.makeText(this, "Rename failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); }
                        });
                        break;
                    case 3:
                        pendingExport = l;
                        exportLauncher.launch("rmtweaks-" + l.name.replaceAll("[^A-Za-z0-9._-]+", "_") + ".json");
                        break;
                    default:
                        new MaterialAlertDialogBuilder(this).setTitle("Delete \"" + l.name + "\"?")
                                .setPositiveButton("Delete", (d2, w2) -> { try { LayoutStore.delete(this, l.name); refreshHome(); showLayouts(); } catch (Throwable ignored) { } })
                                .setNegativeButton(android.R.string.cancel, null).show();
                }
            } catch (Throwable t) { Toast.makeText(this, t.getMessage(), Toast.LENGTH_LONG).show(); }
        }).setNegativeButton("Back", (d, w) -> showLayouts()).show();
    }

    private void applyLayout(LayoutStore.Layout l) {
        try {
            if (!l.name.equals(AUTO_BACKUP)) LayoutStore.saveCurrent(this, AUTO_BACKUP);
            LayoutStore.apply(this, l);
        } catch (Throwable t) { Toast.makeText(this, "Apply failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); return; }
        refreshHome();
        new MaterialAlertDialogBuilder(this).setTitle("\"" + l.name + "\" applied")
                .setMessage("Restart the hooked apps (System UI, launcher and the rest) so everything shows up now? Framework tweaks such as the refresh rate need a reboot.")
                .setPositiveButton("Restart apps", (d, w) -> new Thread(() -> rootAction(Root.restartApps(allPackages()))).start())
                .setNegativeButton("Later", null).show();
    }

    private interface NameCallback { void onName(String name); }

    private void nameDialog(String title, String initial, NameCallback cb) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(initial);
        et.setHint("e.g. One UI daily, Gaming, Stock");
        et.setSelectAllOnFocus(true);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        int p = Math.round(20 * getResources().getDisplayMetrics().density);
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

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Prefs.migrate(this);
        buildRows();
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new HomeAdapter());
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.getMenu().findItem(R.id.action_clean).setChecked(com.hammad.redmagictweaks.UiPrefs.clean(this));
        tb.getMenu().findItem(R.id.action_by_topic).setChecked(com.hammad.redmagictweaks.UiPrefs.byTopic(this));
        tb.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_layouts) { showLayouts(); return true; }
            if (id == R.id.action_clean) {
                boolean v = !item.isChecked();
                item.setChecked(v);
                com.hammad.redmagictweaks.UiPrefs.setClean(this, v);
                if (list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
                Toast.makeText(this, v ? "Clean layout on — long-press a tweak to read what it does" : "Full layout", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (id == R.id.action_by_topic) {
                boolean v = !item.isChecked();
                item.setChecked(v);
                com.hammad.redmagictweaks.UiPrefs.setByTopic(this, v);
                buildRows();
                if (list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
                Toast.makeText(this, v ? "Grouped by topic: Battery & charging and Lock screen hold every related option" : "Grouped by app", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (id == R.id.action_restart_all) {
                confirm("Restart all hooked apps?", "Every app in the module scope is force-stopped and comes back by itself (the framework needs a reboot).",
                        () -> rootAction(Root.restartApps(allPackages())));
            } else if (id == R.id.action_restart_sysui) {
                confirm("Restart System UI?", "The status bar and lock screen reload; takes about two seconds.",
                        () -> rootAction(Root.restartApps("com.android.systemui")));
            } else if (id == R.id.action_soft_reboot) {
                confirm("Soft reboot?", "Restarts zygote (all apps) without a full reboot. Enough for framework-level tweaks.", () -> rootAction(Root.softReboot()));
            } else if (id == R.id.action_reboot) {
                confirm("Reboot now?", null, () -> rootAction(Root.reboot()));
            } else return false;
            return true;
        });
        new Thread(() -> {
            Root.Result r = Root.run("id");
            rootState = r.ok() && r.out.contains("uid=0") ? "OK" : "no";
            runOnUiThread(() -> { if (list.getAdapter() != null) list.getAdapter().notifyItemChanged(0); });
        }).start();
    }

    @Override protected void onResume() {
        super.onResume();
        if (list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
        checkGuards();
    }

    private boolean guardDialogShown;
    private static long lastGuardCheck;

    /** The crash guard / boot-loop guard paused tweaks somewhere: say where and offer to turn them back on. */
    private void checkGuards() {
        if (guardDialogShown || android.os.SystemClock.elapsedRealtime() - lastGuardCheck < 60_000 && lastGuardCheck != 0) return;
        lastGuardCheck = android.os.SystemClock.elapsedRealtime();
        new Thread(() -> {
            final java.util.List<String> paused = Root.pausedByGuards();
            if (paused.isEmpty()) return;
            runOnUiThread(() -> {
                if (isFinishing() || guardDialogShown) return;
                guardDialogShown = true;
                StringBuilder names = new StringBuilder();
                for (String p : paused) names.append("\n• ").append("android".equals(p) ? "Android framework (two boots in a row did not finish)" : p);
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Tweaks paused by the fail-safe")
                        .setMessage("These apps crashed again and again with the tweaks loaded, so they run stock now:" + names
                                + "\n\nTurn a tweak off first if you know which one did it, then turn them back on.")
                        .setPositiveButton("Turn back on", (d, w) -> new Thread(() -> {
                            Root.Result r = Root.resumeGuards();
                            java.util.List<String> apps = new java.util.ArrayList<>(paused);
                            apps.remove("android");
                            if (!apps.isEmpty()) Root.restartApps(apps.toArray(new String[0]));
                            rootAction(r);
                        }).start())
                        .setNegativeButton("Later", null)
                        .show();
            });
        }).start();
    }

    private static String[] allPackages() {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (Features.Section s : Features.SECTIONS) for (String p : s.packages) if (!"android".equals(p)) set.add(p);
        return set.toArray(new String[0]);
    }

    private void confirm(String title, String msg, Runnable action) {
        new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(msg)
                .setPositiveButton("Go", (d, w) -> new Thread(action).start())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void rootAction(Root.Result r) {
        runOnUiThread(() -> Toast.makeText(this, r.ok() ? "Done" : "Root command failed: " + r.out, Toast.LENGTH_LONG).show());
    }

    private int[] counts() {
        int on = 0, total = 0;
        for (Features.Section s : Features.SECTIONS) {
            for (Features.Feature f : s.features) if (f.type != Features.Type.ACTION) total++;
            on += Prefs.enabledCount(this, s);
        }
        return new int[]{on, total};
    }

    private void openSearch(String q) {
        if (q == null || q.trim().isEmpty()) return;
        startActivity(new Intent(this, SectionActivity.class).putExtra("query", q.trim()));
    }

    private final class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int pos) { return rows.get(pos).type; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            int layout = type == Row.HERO ? R.layout.item_hero : type == Row.GROUP ? R.layout.item_group : R.layout.item_section;
            return new RecyclerView.ViewHolder(inf.inflate(layout, parent, false)) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            View v = h.itemView;
            Row row = rows.get(pos);
            if (row.type == Row.HERO) { bindHero(v); return; }
            if (row.type == Row.GROUP) { ((TextView) v.findViewById(R.id.group)).setText(row.label); return; }
            if (row.type == Row.LAYOUTS) {
                ((ImageView) v.findViewById(R.id.icon)).setImageResource(R.drawable.ic_layouts);
                ((TextView) v.findViewById(R.id.title)).setText("Saved layouts");
                TextView sub = v.findViewById(R.id.subtitle);
                sub.setText("Save all your settings as a named layout and switch back any time; export or import as a file");
                sub.setVisibility(com.hammad.redmagictweaks.UiPrefs.clean(MainActivity.this) ? View.GONE : View.VISIBLE);
                int n = LayoutStore.all(MainActivity.this).size();
                TextView count = v.findViewById(R.id.count);
                count.setText(n == 0 ? "none yet" : n + " saved");
                count.setTextColor(getColor(n == 0 ? R.color.text_dim : R.color.accent));
                v.findViewById(R.id.card).setOnClickListener(x -> showLayouts());
                return;
            }
            if (row.type == Row.LOGS) {
                ((ImageView) v.findViewById(R.id.icon)).setImageResource(R.drawable.ic_log);
                ((TextView) v.findViewById(R.id.title)).setText(R.string.logs);
                ((TextView) v.findViewById(R.id.subtitle)).setText("What every hook did, per app — the first place to look when something doesn't apply");
                ((TextView) v.findViewById(R.id.count)).setText(LogStore.read(MainActivity.this).size() + " entries");
                v.findViewById(R.id.card).setOnClickListener(x -> startActivity(new Intent(MainActivity.this, LogActivity.class)));
                return;
            }
            final Features.Section s = row.section;
            ((ImageView) v.findViewById(R.id.icon)).setImageResource(s.icon);
            ((TextView) v.findViewById(R.id.title)).setText(s.title);
            TextView sub = v.findViewById(R.id.subtitle);
            sub.setText(s.subtitle);
            sub.setVisibility(com.hammad.redmagictweaks.UiPrefs.clean(MainActivity.this) ? View.GONE : View.VISIBLE);
            int on = Prefs.enabledCount(MainActivity.this, s), total = 0;
            for (Features.Feature f : s.features) if (f.type != Features.Type.ACTION) total++;
            TextView count = v.findViewById(R.id.count);
            count.setText(on == 0 ? total + " tweaks" : on + " of " + total + " on");
            count.setTextColor(getColor(on == 0 ? R.color.text_dim : R.color.accent));
            v.findViewById(R.id.card).setOnClickListener(x ->
                    startActivity(new Intent(MainActivity.this, SectionActivity.class).putExtra("section", s.id)));
        }

        private void bindHero(View v) {
            boolean active = ModuleStatus.isActive();
            boolean readable = Prefs.isWorldReadable(MainActivity.this);
            TextView pill = v.findViewById(R.id.status_pill);
            pill.setText(active ? "● ACTIVE" : "● NOT ACTIVE");
            pill.setTextColor(getColor(active ? R.color.ok : R.color.err));
            ((TextView) v.findViewById(R.id.hero_sub)).setText("RedMagic 10S Pro · NX789J · firmware 11.0.11 · Android 16");
            int[] c = counts();
            ((TextView) v.findViewById(R.id.stat_on)).setText(String.valueOf(c[0]));
            ((TextView) v.findViewById(R.id.stat_total)).setText(String.valueOf(c[1]));
            ((TextView) v.findViewById(R.id.stat_root)).setText(rootState);
            boolean clean = com.hammad.redmagictweaks.UiPrefs.clean(MainActivity.this);
            v.findViewById(R.id.stats_row).setVisibility(clean ? View.GONE : View.VISIBLE);
            v.findViewById(R.id.hero_sub).setVisibility(clean ? View.GONE : View.VISIBLE);
            TextView hint = v.findViewById(R.id.hero_hint);
            if (!active) { hint.setText(("Enable " + com.hammad.redmagictweaks.BuildConfig.APP_NAME + " in LSPosed, tick every app in its scope, then reboot.")); hint.setVisibility(View.VISIBLE); }
            else if (!readable) { hint.setText("Settings file is not shared with the hooked apps yet — reboot once."); hint.setVisibility(View.VISIBLE); }
            else hint.setVisibility(View.GONE);
            v.findViewById(R.id.btn_sysui).setOnClickListener(x -> confirm("Restart System UI?", "Status bar and lock screen reload in about two seconds.", () -> rootAction(Root.restartApps("com.android.systemui"))));
            v.findViewById(R.id.btn_reboot).setOnClickListener(x -> confirm("Reboot now?", null, () -> rootAction(Root.reboot())));
            EditText search = v.findViewById(R.id.search);
            search.setOnEditorActionListener((tv, actionId, ev) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { openSearch(tv.getText().toString()); tv.setText(""); return true; }
                return false;
            });
        }
    }
}
