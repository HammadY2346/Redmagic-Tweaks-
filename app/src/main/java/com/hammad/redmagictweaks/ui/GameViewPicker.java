package com.hammad.redmagictweaks.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.Prefs;
import com.hammad.redmagictweaks.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-game choice for Features.GAME_VIEW (a text setting, Features.GAME_VIEW_PKGS = "pkg=index;pkg=index"), read in
 * system_server by xposed/GameViewHooks.
 *
 * The list: the games added in Game Space first (Game Launcher's own list, content://cn.nubia.gamelauncher.db.AppAddProvider
 * /appadd "component", the list system_server's GameAssistController checks), then apps Android marks as games, then, with
 * "All apps", every launchable app. Needs QUERY_ALL_PACKAGES (Android 11+ package visibility: without it only a few
 * system apps were visible). A search field filters by name or package; the list scrolls inside the dialog.
 */
public final class GameViewPicker {
    private GameViewPicker() {}

    static final Uri GAME_SPACE = Uri.parse("content://cn.nubia.gamelauncher.db.AppAddProvider/appadd?notify=false");

    /** Per-game choices: package -> Features.GAME_VIEWS index. */
    public static Map<String, Integer> load(Context c) {
        Map<String, Integer> m = new LinkedHashMap<>();
        String s = Prefs.str(c, Features.GAME_VIEW_PKGS);
        if (s == null) return m;
        for (String e : s.split(";")) {
            int eq = e.indexOf('=');
            if (eq <= 0) continue;
            try {
                int v = Integer.parseInt(e.substring(eq + 1).trim());
                if (v >= 0 && v < Features.GAME_VIEWS.length) m.put(e.substring(0, eq).trim(), v);
            } catch (NumberFormatException ignored) { }
        }
        return m;
    }

    private static void save(Context c, Map<String, Integer> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        Prefs.setStr(c, Features.GAME_VIEW_PKGS, sb.length() == 0 ? null : sb.toString());
    }

    // ------------------------------------------------------------------ launch boost games (Features.GAME_LAUNCH_BOOST_PKGS)

    /** Games picked for the launch boost ("pkg;pkg"); empty = every Game Space game. */
    public static Set<String> boostGames(Context c) {
        Set<String> out = new java.util.LinkedHashSet<>();
        String s = Prefs.str(c, Features.GAME_LAUNCH_BOOST_PKGS);
        if (s != null) for (String e : s.split(";")) if (!e.trim().isEmpty()) out.add(e.trim());
        return out;
    }

    private static void saveBoost(Context c, Set<String> pkgs) {
        Prefs.setStr(c, Features.GAME_LAUNCH_BOOST_PKGS, pkgs.isEmpty() ? null : String.join(";", pkgs));
    }

    public static String boostLabel(Context c) {
        int n = boostGames(c).size();
        return n == 0 ? "Every Game Space game" : n == 1 ? "1 game" : n + " games";
    }

    /** Tick the games that get the launch boost. */
    public static void showBoost(final Activity a, final Runnable done) {
        show(a, done, true);
    }

    /** Value text of the row: how many games have their own view. */
    public static String label(Context c) {
        int n = load(c).size();
        return n == 0 ? "None yet" : n == 1 ? "1 game" : n + " games";
    }

    private static String shortView(int i) {
        String s = Features.GAME_VIEWS[i];
        int cut = s.indexOf(" (");
        if (cut > 0) s = s.substring(0, cut);
        return s.replace(", stretched to full screen", " stretched").replace(": 4:3", " (4:3)");
    }

    /** Packages in Game Space's game list ("component" is "pkg/cls" or a package name). */
    static Set<String> gameSpace(Context c) {
        Set<String> out = new HashSet<>();
        try (Cursor q = c.getContentResolver().query(GAME_SPACE, null, null, null, null)) {
            if (q == null) { Log.w("RMTweaks", "Game view: Game Space's list is not readable (no provider)"); return out; }
            int col = q.getColumnIndex("component");
            int pcol = q.getColumnIndex("packageName");
            while (q.moveToNext()) {
                String v = col >= 0 ? q.getString(col) : pcol >= 0 ? q.getString(pcol) : null;
                if (v == null || v.isEmpty()) continue;
                int slash = v.indexOf('/');
                out.add(slash > 0 ? v.substring(0, slash) : v);
            }
        } catch (Throwable t) { Log.w("RMTweaks", "Game view: Game Space's list: " + t); }
        return out;
    }

    static final class App {
        final String pkg, label;
        final int rank;            // 0 = in Game Space, 1 = marked as a game, 2 = has a choice, 3 = other app
        App(String pkg, String label, int rank) { this.pkg = pkg; this.label = label; this.rank = rank; }
    }

    /** Every launchable app, ranked (Game Space games first). */
    static List<App> apps(Context c, boolean boost) {
        PackageManager pm = c.getPackageManager();
        Set<String> space = gameSpace(c);
        Map<String, Integer> chosen = load(c);
        if (boost) { chosen = new HashMap<>(); for (String p : boostGames(c)) chosen.put(p, 1); }
        List<ResolveInfo> ris = new ArrayList<>();
        try { ris = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0); } catch (Throwable ignored) { }
        Map<String, App> found = new LinkedHashMap<>();
        for (ResolveInfo r : ris) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || found.containsKey(pkg)) continue;
            found.put(pkg, app(pm, pkg, r.activityInfo.applicationInfo, space, chosen));
        }
        // Game Space games / chosen ones without a launcher entry (or hidden from it)
        Set<String> extra = new HashSet<>(space);
        extra.addAll(chosen.keySet());
        for (String pkg : extra) {
            if (found.containsKey(pkg)) continue;
            try { found.put(pkg, app(pm, pkg, pm.getApplicationInfo(pkg, 0), space, chosen)); } catch (Throwable ignored) { }
        }
        List<App> out = new ArrayList<>(found.values());
        Collections.sort(out, (x, y) -> x.rank != y.rank ? x.rank - y.rank : x.label.compareToIgnoreCase(y.label));
        Log.i("RMTweaks", "Game view: " + out.size() + " apps, " + space.size() + " in Game Space");
        return out;
    }

    private static App app(PackageManager pm, String pkg, ApplicationInfo ai, Set<String> space, Map<String, Integer> chosen) {
        String label = pkg;
        try { label = ai.loadLabel(pm).toString(); } catch (Throwable ignored) { }
        boolean game = ai != null && (ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0);
        int rank = space.contains(pkg) ? 0 : game ? 1 : chosen.containsKey(pkg) ? 2 : 3;
        return new App(pkg, label, rank);
    }

    // ------------------------------------------------------------------ the dialog

    /** The list of games; {@code done} runs after a change was saved. */
    public static void show(final Activity a, final Runnable done) { show(a, done, false); }

    private static void show(final Activity a, final Runnable done, final boolean boost) {
        final int pad = Ui.dp(a, 20);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(a, 4), pad, 0);

        final EditText search = new EditText(a);
        search.setHint("Search");
        search.setSingleLine(true);
        box.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final CheckBox all = new CheckBox(a);
        all.setText("All apps (not only games)");
        box.addView(all, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView empty = new TextView(a);
        empty.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 12));
        empty.setTextColor(a.getColor(R.color.text_dim));
        empty.setText("Loading…");
        box.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ProgressBar spin = new ProgressBar(a);
        box.addView(spin, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // a fixed height: the list scrolls inside the dialog and the buttons stay visible
        final ListView list = new ListView(a);
        int h = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.5f);
        box.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        list.setVisibility(View.GONE);

        final Adapter ad = new Adapter(a, boost);
        list.setAdapter(ad);

        final AlertDialog d = new MaterialAlertDialogBuilder(a)
                .setTitle(boost ? "Boost only these games" : "Game view per game")
                .setView(box)
                .setPositiveButton("Done", null)
                .create();

        final Runnable refilter = () -> {
            ad.filter(search.getText().toString(), all.isChecked());
            boolean none = ad.getCount() == 0;
            list.setVisibility(none ? View.GONE : View.VISIBLE);
            empty.setVisibility(none ? View.VISIBLE : View.GONE);
            if (none) empty.setText(all.isChecked() ? "No app matches." : "No game found. Add the game in Game Space, or tick \"All apps\".");
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { if (ad.loaded) refilter.run(); }
        });
        all.setOnCheckedChangeListener((b, on) -> { if (ad.loaded) refilter.run(); });
        list.setOnItemClickListener((parent, view, pos, id) -> {
            final App app = ad.get(pos);
            if (boost) {
                Set<String> now = boostGames(a);
                if (!now.remove(app.pkg)) now.add(app.pkg);
                saveBoost(a, now);
                ad.notifyDataSetChanged();
                if (done != null) done.run();
                return;
            }
            pick(a, app, () -> { ad.notifyDataSetChanged(); if (done != null) done.run(); });
        });
        d.show();

        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            final List<App> apps = apps(a, boost);
            main.post(() -> {
                if (!d.isShowing()) return;
                spin.setVisibility(View.GONE);
                ad.set(apps);
                // nothing marked as a game: show everything right away
                boolean anyGame = false;
                for (App x : apps) if (x.rank <= 2) { anyGame = true; break; }
                if (!anyGame) all.setChecked(true);
                refilter.run();
            });
        }, "rmtweaks-gameview-apps").start();
    }

    private static void pick(final Activity a, final App app, final Runnable changed) {
        final Map<String, Integer> chosen = load(a);
        final List<String> names = new ArrayList<>();
        names.add("Same as Game view");
        for (int i = 0; i < Features.GAME_VIEWS.length; i++) names.add(i == 0 ? "Stock (no change)" : Features.GAME_VIEWS[i]);
        Integer cur = chosen.get(app.pkg);
        final int[] sel = {cur == null ? 0 : cur + 1};
        final int allView = Math.max(0, Prefs.choice(a, Features.GAME_VIEW));

        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 16);
        box.setPadding(pad, Ui.dp(a, 8), pad, 0);
        final android.widget.ImageView preview = new android.widget.ImageView(a);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        box.addView(preview, new android.widget.LinearLayout.LayoutParams(-1, -2));
        final android.widget.TextView hint = new android.widget.TextView(a);
        hint.setText("Estimate for PUBG-style games · applies the next time the game opens, no reboot needed");
        hint.setTextSize(12);
        hint.setAlpha(0.7f);
        hint.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 4));
        box.addView(hint);
        final android.widget.ListView list = new android.widget.ListView(a);
        list.setChoiceMode(android.widget.ListView.CHOICE_MODE_SINGLE);
        list.setAdapter(new android.widget.ArrayAdapter<>(a, android.R.layout.simple_list_item_single_choice, names));
        list.setItemChecked(sel[0], true);
        int listH = Math.min(names.size() * Ui.dp(a, 48), a.getResources().getDisplayMetrics().heightPixels / 3);
        box.addView(list, new android.widget.LinearLayout.LayoutParams(-1, listH));
        final Runnable show = () -> preview.setImageDrawable(com.hammad.redmagictweaks.GameViewArt.preview(a, sel[0] == 0 ? allView : sel[0] - 1));
        show.run();
        list.setOnItemClickListener((parent, v, pos, id) -> { sel[0] = pos; show.run(); });

        new MaterialAlertDialogBuilder(a).setTitle(app.label)
                .setView(box)
                .setPositiveButton("Use", (dlg, w) -> {
                    Map<String, Integer> now = load(a);
                    if (sel[0] == 0) now.remove(app.pkg); else now.put(app.pkg, sel[0] - 1);
                    save(a, now);
                    changed.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Rows: icon, name, "In Game Space" / package, current choice. */
    private static final class Adapter extends BaseAdapter {
        private final Activity a;
        private final boolean boost;
        private final PackageManager pm;
        private final Map<String, Drawable> icons = new HashMap<>();
        private List<App> every = new ArrayList<>();
        private final List<App> shown = new ArrayList<>();
        boolean loaded;

        Adapter(Activity a, boolean boost) { this.a = a; this.boost = boost; this.pm = a.getPackageManager(); }

        void set(List<App> apps) { every = apps; loaded = true; }

        void filter(String q, boolean all) {
            shown.clear();
            String s = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            for (App x : every) {
                if (!all && x.rank > 2) continue;
                if (!s.isEmpty() && !x.label.toLowerCase(Locale.ROOT).contains(s) && !x.pkg.toLowerCase(Locale.ROOT).contains(s)) continue;
                shown.add(x);
            }
            notifyDataSetChanged();
        }

        App get(int i) { return shown.get(i); }
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View v, ViewGroup parent) {
            Row r;
            if (v == null) { r = new Row(a); v = r.root; v.setTag(r); } else r = (Row) v.getTag();
            App x = shown.get(i);
            Drawable ic = icons.get(x.pkg);
            if (ic == null) {
                try { ic = pm.getApplicationIcon(x.pkg); } catch (Throwable t) { ic = pm.getDefaultActivityIcon(); }
                icons.put(x.pkg, ic);
            }
            r.icon.setImageDrawable(ic);
            r.name.setText(x.label);
            String where = x.rank == 0 ? "In Game Space" : x.rank == 1 ? "Game" : x.pkg;
            if (boost) {
                Set<String> picked = boostGames(a);
                boolean on = picked.contains(x.pkg);
                String state = on ? "Boosted" : picked.isEmpty() && x.rank == 0 ? "Boosted (every Game Space game)" : "Not boosted";
                r.sub.setText(where + " · " + state);
                r.sub.setTextColor(a.getColor(on ? R.color.accent : R.color.text_dim));
                r.check.setVisibility(View.VISIBLE);
                r.check.setChecked(on);
                return v;
            }
            Integer cur = load(a).get(x.pkg);
            r.sub.setText(where + " · " + (cur == null ? "Same as Game view" : shortView(cur)));
            r.sub.setTextColor(a.getColor(cur == null ? R.color.text_dim : R.color.accent));
            r.check.setVisibility(View.GONE);
            return v;
        }
    }

    private static final class Row {
        final LinearLayout root;
        final ImageView icon;
        final TextView name, sub;
        final CheckBox check;

        Row(Context c) {
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
            icon = new ImageView(c);
            int s = Ui.dp(c, 40);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(s, s);
            ip.setMarginEnd(Ui.dp(c, 14));
            root.addView(icon, ip);
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(LinearLayout.VERTICAL);
            name = new TextView(c);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            name.setTextColor(c.getColor(R.color.text));
            name.setSingleLine(true);
            sub = new TextView(c);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            sub.setSingleLine(true);
            texts.addView(name);
            texts.addView(sub);
            root.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            check = new CheckBox(c);
            check.setClickable(false);
            check.setFocusable(false);
            check.setVisibility(View.GONE);
            root.addView(check);
        }
    }
}
