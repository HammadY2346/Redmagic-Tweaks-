package com.hammad.redmagictweaks.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.hammad.redmagictweaks.BuildConfig;
import com.hammad.redmagictweaks.Catalog;
import com.hammad.redmagictweaks.LayoutStore;
import com.hammad.redmagictweaks.LogStore;
import com.hammad.redmagictweaks.LookPreview;
import com.hammad.redmagictweaks.Looks;
import com.hammad.redmagictweaks.ModuleStatus;
import com.hammad.redmagictweaks.Prefs;
import com.hammad.redmagictweaks.Presets;
import com.hammad.redmagictweaks.R;
import com.hammad.redmagictweaks.Root;
import com.hammad.redmagictweaks.Safety;
import com.hammad.redmagictweaks.UiPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * Home: status card with Safe mode, search, the looks (One UI 8, stock, saved layouts), the category grid and Logs /
 * Backup & restore / About & credits.
 */
public class MainActivity extends BaseActivity {
    private RecyclerView list;
    private String rootState = null;
    private boolean safeBusy;

    private static final int BANNER = 0, STATUS = 1, SEARCH = 2, SUBHEAD = 3, LOOKS = 4, CATEGORY = 5, FOOTER = 6;

    private static final class Row {
        final int type; final String text; final Catalog.Category cat; final int icon; final Runnable click;
        Row(int type, String text, Catalog.Category cat, int icon, Runnable click) { this.type = type; this.text = text; this.cat = cat; this.icon = icon; this.click = click; }
    }
    private final List<Row> rows = new ArrayList<>();

    /** Phones this build was made on. Others get a "not tested" banner instead of silently broken tweaks. */
    static boolean supportedPhone() {
        String m = (Build.MODEL + " " + Build.DEVICE + " " + Build.PRODUCT).toUpperCase(java.util.Locale.ROOT);
        return m.contains("NX789J");
    }

    private void buildRows() {
        rows.clear();
        if (Safety.safeMode(this)) rows.add(new Row(BANNER, "Safe mode is on: every tweak is paused. Your settings are kept and come back when you turn it off.", null, R.drawable.ic_shield, null));
        if (!supportedPhone()) rows.add(new Row(BANNER, BuildConfig.APP_NAME + " is made and tested on the REDMAGIC 10S Pro (NX789J). On " + Build.MODEL
                + " some tweaks may do nothing; Logs show which ones found their target.", null, R.drawable.ic_cat_advanced, null));
        rows.add(new Row(STATUS, null, null, 0, null));
        rows.add(new Row(SEARCH, null, null, 0, null));
        rows.add(new Row(SUBHEAD, "Looks", null, 0, null));
        rows.add(new Row(LOOKS, null, null, 0, null));
        rows.add(new Row(SUBHEAD, "Settings", null, 0, null));
        for (Catalog.Category c : Catalog.visible(this)) rows.add(new Row(CATEGORY, null, c, 0, null));
        rows.add(new Row(SUBHEAD, "More", null, 0, null));
        rows.add(new Row(FOOTER, "Logs", null, R.drawable.ic_log, () -> startActivity(new Intent(this, LogActivity.class))));
        rows.add(new Row(FOOTER, "Backup & restore", null, R.drawable.ic_layouts, this::showLayouts));
        rows.add(new Row(FOOTER, "About & credits", null, R.drawable.ic_info, () -> startActivity(new Intent(this, AboutActivity.class))));
    }

    @Override protected void onSettingsChanged() {
        buildRows();
        if (list != null && list.getAdapter() != null) list.getAdapter().notifyDataSetChanged();
        if (restartBar != null) restartBar.update();
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.migrate(this);
        if (!UiPrefs.firstRunDone(this)) startActivity(new Intent(this, FirstRunActivity.class));
        setContentView(R.layout.activity_main);
        buildRows();
        list = findViewById(R.id.list);
        GridLayoutManager glm = new GridLayoutManager(this, 2);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int pos) { return pos < rows.size() && rows.get(pos).type == CATEGORY ? 1 : 2; }
        });
        list.setLayoutManager(glm);
        list.setAdapter(new HomeAdapter());
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.getMenu().findItem(R.id.action_advanced).setChecked(UiPrefs.advanced(this));
        tb.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_advanced) toggleAdvanced(item);
            else if (id == R.id.action_restart_sysui) confirm("Restart System UI?", "The status bar, lock screen and panels reload; takes about two seconds.", () -> {
                Root.Result r = Root.restartSystemUi();
                if (r.ok()) com.hammad.redmagictweaks.Pending.clearSystemUi(this);
                runOnUiThread(() -> Toast.makeText(this, r.out, Toast.LENGTH_LONG).show());
            });
            else if (id == R.id.action_restart_all) confirm("Restart all tweaked apps?", "Every app in the module scope restarts by itself (framework tweaks need a reboot).", () -> {
                Root.Result r = Root.restartApps(Catalog.allPackages());
                if (r.ok()) { com.hammad.redmagictweaks.Pending.clearSystemUi(this); com.hammad.redmagictweaks.Pending.clearApps(this); }
                rootAction(r);
            });
            else if (id == R.id.action_soft_reboot) confirm("Soft reboot?", "Restarts Android without restarting the phone (about 15 seconds). Enough for framework tweaks.", () -> {
                com.hammad.redmagictweaks.Pending.clearAll(this); rootAction(Root.softReboot());
            });
            else if (id == R.id.action_reboot) confirm("Reboot now?", null, () -> { com.hammad.redmagictweaks.Pending.clearAll(this); rootAction(Root.reboot()); });
            else if (id == R.id.action_reset_all) resetAll();
            else if (id == R.id.action_setup) startActivity(new Intent(this, FirstRunActivity.class));
            else return false;
            return true;
        });
        new Thread(() -> {
            Root.Result r = Root.run("id");
            rootState = r.ok() && r.out.contains("uid=0") ? "granted" : "not granted";
            runOnUiThread(() -> onSettingsChanged());
        }).start();
    }

    @Override protected void onResume() {
        super.onResume();
        onSettingsChanged();
        checkGuards();
    }

    private void resetAll() {
        new MaterialAlertDialogBuilder(this).setTitle("Reset all settings?")
                .setMessage("Every setting goes back to stock. Your current setup is saved first as a layout (\"Before reset\" and the date), so you can restore it from Backup & restore.")
                .setPositiveButton("Reset", (d, w) -> {
                    try {
                        String name = Safety.resetAll(this);
                        onSettingsChanged();
                        Toast.makeText(this, "Reset · saved \"" + name + "\" first", Toast.LENGTH_LONG).show();
                    } catch (Throwable t) { Toast.makeText(this, "Reset failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void setSafe(boolean on) {
        if (safeBusy) return;
        safeBusy = true;
        Toast.makeText(this, on ? "Safe mode on: restarting the tweaked apps…" : "Safe mode off: restarting the tweaked apps…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Root.Result r = Safety.setSafeMode(this, on);
            runOnUiThread(() -> {
                safeBusy = false;
                if (!r.ok()) Toast.makeText(this, "Root command failed: " + r.out + "\nThe hooked apps pick it up after a reboot.", Toast.LENGTH_LONG).show();
                if (on) new MaterialAlertDialogBuilder(this).setTitle("Safe mode is on")
                        .setMessage("Apps that restarted run stock now. Framework tweaks (refresh rate, small windows, volume boost…) stop after the next reboot.")
                        .setPositiveButton(android.R.string.ok, null).show();
                onSettingsChanged();
            });
        }).start();
    }

    // ============================================================================================ fail-safe guards

    private boolean guardDialogShown;
    private static long lastGuardCheck;

    /** The crash guard / boot-loop guard paused tweaks somewhere: say where and offer to turn them back on. */
    private void checkGuards() {
        if (guardDialogShown || android.os.SystemClock.elapsedRealtime() - lastGuardCheck < 60_000 && lastGuardCheck != 0) return;
        lastGuardCheck = android.os.SystemClock.elapsedRealtime();
        new Thread(() -> {
            final List<String> paused = Root.pausedByGuards();
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
                            List<String> apps = new ArrayList<>(paused);
                            apps.remove("android");
                            if (!apps.isEmpty()) Root.restartApps(apps.toArray(new String[0]));
                            rootAction(r);
                        }).start())
                        .setNegativeButton("Later", null)
                        .show();
            });
        }).start();
    }

    // ============================================================================================ list

    private int settingsOn() {
        int n = 0;
        for (Catalog.Category c : Catalog.all()) n += Catalog.changedCount(this, c);
        return n;
    }

    private void openSearch(String q) {
        if (q == null || q.trim().isEmpty()) return;
        startActivity(new Intent(this, CategoryActivity.class).putExtra(CategoryActivity.EXTRA_QUERY, q.trim()));
    }

    private final class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return rows.size(); }
        @Override public int getItemViewType(int pos) { return rows.get(pos).type; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            int layout;
            switch (type) {
                case BANNER: layout = R.layout.item_banner; break;
                case STATUS: layout = R.layout.item_status; break;
                case SEARCH: layout = R.layout.item_search; break;
                case SUBHEAD: layout = R.layout.item_subhead; break;
                case LOOKS: layout = R.layout.item_looks; break;
                case CATEGORY: layout = R.layout.item_category; break;
                default: layout = R.layout.item_footer; break;
            }
            View v = inf.inflate(layout, parent, false);
            if (type == LOOKS) ((RecyclerView) v).setLayoutManager(new LinearLayoutManager(MainActivity.this, LinearLayoutManager.HORIZONTAL, false));
            Ui.fonts(v);
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            View v = h.itemView;
            Row row = rows.get(pos);
            switch (row.type) {
                case BANNER:
                    ((TextView) v.findViewById(R.id.banner_text)).setText(row.text);
                    ((ImageView) v.findViewById(R.id.banner_icon)).setImageResource(row.icon);
                    break;
                case STATUS: bindStatus(v); break;
                case SEARCH: {
                    EditText search = v.findViewById(R.id.search);
                    search.setOnEditorActionListener((tv, actionId, ev) -> {
                        if (actionId == EditorInfo.IME_ACTION_SEARCH) { openSearch(tv.getText().toString()); tv.setText(""); return true; }
                        return false;
                    });
                    break;
                }
                case SUBHEAD: ((TextView) v.findViewById(R.id.subhead)).setText(row.text); break;
                case LOOKS: ((RecyclerView) v).setAdapter(new LooksAdapter()); break;
                case CATEGORY: {
                    final Catalog.Category c = row.cat;
                    ((ImageView) v.findViewById(R.id.cat_icon)).setImageResource(c.icon);
                    ((ImageView) v.findViewById(R.id.cat_icon)).setColorFilter(getColor(R.color.accent));
                    ((TextView) v.findViewById(R.id.cat_title)).setText(c.title);
                    int on = Catalog.changedCount(MainActivity.this, c);
                    TextView count = v.findViewById(R.id.cat_count);
                    count.setText(on == 0 ? c.subtitle : on + " on");
                    count.setTextColor(getColor(on == 0 ? R.color.text_dim : R.color.accent));
                    v.setOnClickListener(x -> startActivity(new Intent(MainActivity.this, CategoryActivity.class).putExtra(CategoryActivity.EXTRA_CATEGORY, c.id)));
                    break;
                }
                default: {
                    ((ImageView) v.findViewById(R.id.row_icon)).setImageResource(row.icon);
                    ((TextView) v.findViewById(R.id.row_title)).setText(row.text);
                    TextView sub = v.findViewById(R.id.row_sub);
                    if (row.icon == R.drawable.ic_log) sub.setText(LogStore.read(MainActivity.this).size() + " entries · what every hook did, per app");
                    else if (row.icon == R.drawable.ic_layouts) { int n = LayoutStore.all(MainActivity.this).size(); sub.setText(n == 0 ? "Save your setup as a layout, export or import it" : n + " saved layouts"); }
                    else sub.setText("Version " + BuildConfig.VERSION_NAME + " · everyone whose work this uses");
                    boolean first = pos == 0 || rows.get(pos - 1).type != FOOTER, last = pos == rows.size() - 1 || rows.get(pos + 1).type != FOOTER;
                    v.setBackground(Ui.groupRow(MainActivity.this, first, last));
                    ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                    lp.leftMargin = lp.rightMargin = Ui.dp(MainActivity.this, 4);
                    v.setLayoutParams(lp);
                    v.setOnClickListener(x -> row.click.run());
                    break;
                }
            }
        }

        private void bindStatus(View v) {
            boolean active = ModuleStatus.isActive();
            boolean safe = Safety.safeMode(MainActivity.this);
            boolean readable = Prefs.isWorldReadable(MainActivity.this);
            TextView state = v.findViewById(R.id.state);
            View dot = v.findViewById(R.id.state_dot);
            int color = getColor(safe ? R.color.warn : active ? R.color.ok : R.color.err);
            state.setText(safe ? "Safe mode" : active ? "Active" : "Not active");
            dot.getBackground().mutate().setTint(color);
            StringBuilder d = new StringBuilder();
            d.append(Build.MODEL).append(" · ").append(Build.DISPLAY).append(" · Android ").append(Build.VERSION.RELEASE);
            d.append("\nRoot ").append(rootState == null ? "…" : rootState);
            if (!active) d.append("\nEnable ").append(BuildConfig.APP_NAME).append(" in LSPosed, tick every app in its scope, then reboot.");
            else if (!readable) d.append("\nThe settings file is not shared with the hooked apps yet: reboot once.");
            ((TextView) v.findViewById(R.id.state_detail)).setText(d);
            int on = settingsOn();
            ((TextView) v.findViewById(R.id.on_count)).setText(on + " on");
            MaterialSwitch sw = v.findViewById(R.id.safe_switch);
            sw.setOnCheckedChangeListener(null);
            sw.setChecked(safe);
            sw.setOnCheckedChangeListener((b, checked) -> setSafe(checked));
            v.findViewById(R.id.safe_row).setOnClickListener(x -> sw.toggle());
        }
    }

    // ============================================================================================ looks row

    private static final class LookItem {
        final String name; final Looks.V values; final Runnable open;
        LookItem(String name, Looks.V values, Runnable open) { this.name = name; this.values = values; this.open = open; }
    }

    private List<LookItem> looks() {
        List<LookItem> l = new ArrayList<>();
        Looks.V live = Looks.live(this);
        l.add(new LookItem("One UI 8 look", Looks.with(live, Presets.ONE_UI), () -> showPreset("One UI 8 look", Presets.ONE_UI, false)));
        l.add(new LookItem("Stock look", Looks.with(live, Presets.STOCK), () -> showPreset("Stock look", Presets.STOCK, false)));
        for (LayoutStore.Layout s : LayoutStore.all(this)) l.add(new LookItem(s.name, Looks.of(s), () -> layoutActions(s)));
        l.add(new LookItem("Save current", null, this::saveCurrent));
        return l;
    }

    private final class LooksAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<LookItem> items = looks();
        @Override public int getItemCount() { return items.size(); }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int t) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_look, parent, false);
            Ui.fonts(v);
            return new RecyclerView.ViewHolder(v) {};
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            LookItem it = items.get(pos);
            View v = h.itemView;
            ((TextView) v.findViewById(R.id.look_name)).setText(it.name);
            ImageView img = v.findViewById(R.id.look_img);
            v.findViewById(R.id.look_add).setVisibility(it.values == null ? View.VISIBLE : View.GONE);
            img.setImageDrawable(it.values == null ? null : LookPreview.phone(MainActivity.this, it.values, LookPreview.CC, 76));
            v.setOnClickListener(x -> it.open.run());
        }
    }
}
