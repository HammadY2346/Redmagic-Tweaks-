package com.hammad.redmagictweaks.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.Prefs;
import com.hammad.redmagictweaks.Root;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picker for the stock launcher's icon pack (Features.LAUNCHER_ICON_PACK, a text setting): every installed app that
 * declares itself an ADW / Nova / Apex / Go / Action-launcher icon pack, plus "Stock icons". After a pick the launcher is
 * restarted; on its next start it clears its own icon cache (xposed.LauncherIcons#clearCacheIfChanged).
 */
public final class IconPackPicker {
    private IconPackPicker() {}

    /** Intent actions / categories icon packs declare (the same list other launchers query). */
    private static final String[] ACTIONS = {"org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
            "com.teslacoilsw.launcher.THEME", "com.anddoes.launcher.THEME", "com.fede.launcher.THEME_ICONPACK", "com.dlto.atom.launcher.THEME",
            "com.actionlauncher.playstore.ICONPACK", "ginlemon.smartlauncher.THEMES", "com.lge.launcher2.THEME"};
    private static final String[] CATEGORIES = {"com.anddoes.launcher.THEME", "com.teslacoilsw.launcher.THEME", "com.fede.launcher.THEME_ICONPACK"};

    /** Installed icon packs: package name -> label (sorted by label). */
    public static Map<String, String> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, String> found = new LinkedHashMap<>();
        List<ResolveInfo> all = new ArrayList<>();
        for (String a : ACTIONS) {
            try { all.addAll(pm.queryIntentActivities(new Intent(a), PackageManager.GET_META_DATA)); } catch (Throwable ignored) { }
        }
        for (String cat : CATEGORIES) {
            try { all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), PackageManager.GET_META_DATA)); } catch (Throwable ignored) { }
        }
        for (ResolveInfo r : all) {
            if (r.activityInfo == null) continue;
            String pkg = r.activityInfo.packageName;
            if (found.containsKey(pkg)) continue;
            CharSequence l = null;
            try { l = r.activityInfo.applicationInfo.loadLabel(pm); } catch (Throwable ignored) { }
            found.put(pkg, l == null ? pkg : l.toString());
        }
        List<Map.Entry<String, String>> e = new ArrayList<>(found.entrySet());
        Collections.sort(e, (x, y) -> x.getValue().compareToIgnoreCase(y.getValue()));
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> x : e) out.put(x.getKey(), x.getValue());
        return out;
    }

    /** Value text of the row: the pack's name, or "Stock icons". */
    public static String label(Context c) {
        String pkg = Prefs.str(c, Features.LAUNCHER_ICON_PACK);
        if (pkg == null || pkg.isEmpty()) return "Stock icons";
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
        } catch (Throwable t) { return pkg + " (not installed)"; }
    }

    /** Shows the list; {@code done} runs on the UI thread after a change was saved. */
    public static void show(final Activity a, final Runnable done) {
        final Map<String, String> packs = installed(a);
        final List<String> pkgs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        pkgs.add(null); names.add("Stock icons");
        for (Map.Entry<String, String> e : packs.entrySet()) { pkgs.add(e.getKey()); names.add(e.getValue()); }
        String cur = Prefs.str(a, Features.LAUNCHER_ICON_PACK);
        int checked = 0;
        for (int i = 1; i < pkgs.size(); i++) if (pkgs.get(i).equals(cur)) checked = i;
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a).setTitle("Icon pack");
        if (packs.isEmpty()) b.setMessage("No icon pack is installed. Install one from the Play Store (any pack that supports Nova, ADW or Apex), then come back here.");
        final int was = checked;
        b.setSingleChoiceItems(names.toArray(new CharSequence[0]), checked, (d, which) -> {
            d.dismiss();
            if (which == was) return;
            String pick = pkgs.get(which);
            Prefs.setStr(a, Features.LAUNCHER_ICON_PACK, pick);
            if (done != null) done.run();
            Toast.makeText(a, "Restarting the launcher with " + (pick == null ? "stock icons" : names.get(which)) + "…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                Root.Result r = Root.restartApps("com.zte.mifavor.launcher");
                if (r == null || !r.ok()) a.runOnUiThread(() -> Toast.makeText(a, "Couldn't restart the launcher (root?). Restart it yourself to see the new icons.", Toast.LENGTH_LONG).show());
            }, "icon-pack-restart").start();
        });
        b.setNegativeButton(android.R.string.cancel, null).show();
    }
}
