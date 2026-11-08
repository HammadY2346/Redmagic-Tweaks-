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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picker for the launcher icon pack (Features.LAUNCHER_ICON_PACK): every installed ADW / Nova / Apex / Go / Action
 * icon pack, plus "Stock icons". After a pick every home app is restarted; on start each clears its own icon cache
 * (xposed.LauncherIcons#clearCacheIfChanged) so stock and third-party launchers both reload.
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
        if (com.hammad.redmagictweaks.IconZip.isZipValue(pkg)) return com.hammad.redmagictweaks.IconZip.label(c);
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString();
        } catch (Throwable t) { return pkg + " (not installed)"; }
    }

    /** Shows the list; {@code done} runs on the UI thread after a change was saved. {@code pickZip} (null = no zip option,
     *  e.g. the public build) opens the file picker for an icon zip (com.hammad.redmagictweaks.IconZip). */
    public static void show(final Activity a, final Runnable done, final Runnable pickZip) {
        final Map<String, String> packs = installed(a);
        final List<String> pkgs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        pkgs.add(null); names.add("Stock icons");
        com.hammad.redmagictweaks.IconZip.Meta zm = pickZip == null ? null : com.hammad.redmagictweaks.IconZip.meta(a);
        String fromPreset = zm == null ? null : com.hammad.redmagictweaks.IconZip.presetOf(a);
        String cur0 = Prefs.str(a, Features.LAUNCHER_ICON_PACK);
        boolean zipOn = com.hammad.redmagictweaks.IconZip.isZipValue(cur0);
        // built-in presets (private build): one row each; the row of the preset in use is the checked one
        if (pickZip != null) for (Map.Entry<String, String> e : com.hammad.redmagictweaks.IconZip.presets(a).entrySet()) {
            pkgs.add(ZIP_PRESET + e.getKey());
            names.add("Built-in: " + e.getValue());
        }
        if (zm != null && fromPreset == null) { pkgs.add(com.hammad.redmagictweaks.IconZip.PREFIX + zm.version); names.add(com.hammad.redmagictweaks.IconZip.label(a)); }
        for (Map.Entry<String, String> e : packs.entrySet()) { pkgs.add(e.getKey()); names.add(e.getValue()); }
        final int zipRow = pickZip == null ? -1 : pkgs.size();
        if (pickZip != null) { pkgs.add(ZIP_PICK); names.add(zm == null ? "From a zip file…" : "Another zip file…"); }
        if (zm != null) { pkgs.add(ZIP_REMOVE); names.add("Remove the icon zip"); }
        String cur = zipOn && fromPreset != null ? ZIP_PRESET + fromPreset : cur0;
        int checked = 0;
        for (int i = 1; i < pkgs.size(); i++) if (pkgs.get(i).equals(cur)) checked = i;
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a).setTitle("Icon pack");
        if (packs.isEmpty() && zm == null && (pickZip == null || com.hammad.redmagictweaks.IconZip.presets(a).isEmpty())) b.setMessage(pickZip == null
                ? "No icon pack is installed. Install one from the Play Store (any pack that supports Nova, ADW or Apex), then come back here."
                : "No icon pack app is installed. Pick an icon zip (an icon pack's appfilter.xml with its images, images named after apps such as com.whatsapp.png, or a Xiaomi .mtz theme), or install a pack from the Play Store.");
        final int was = checked;
        b.setSingleChoiceItems(names.toArray(new CharSequence[0]), checked, (d, which) -> {
            d.dismiss();
            String pick = pkgs.get(which);
            if (ZIP_PICK.equals(pick)) { pickZip.run(); return; }
            if (pick != null && pick.startsWith(ZIP_PRESET)) {
                if (which == was) return;
                final String id = pick.substring(ZIP_PRESET.length()), label = names.get(which);
                Toast.makeText(a, "Loading the icons…", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    String err = com.hammad.redmagictweaks.IconZip.applyPreset(a, id);
                    a.runOnUiThread(() -> {
                        if (err != null) { Toast.makeText(a, err, Toast.LENGTH_LONG).show(); return; }
                        if (done != null) done.run();
                        restartLauncher(a, label.replace("Built-in: ", ""));
                    });
                }, "icon-preset").start();
                return;
            }
            if (ZIP_REMOVE.equals(pick)) {
                boolean wasOn = com.hammad.redmagictweaks.IconZip.isZipValue(Prefs.str(a, Features.LAUNCHER_ICON_PACK));
                com.hammad.redmagictweaks.IconZip.remove(a);
                if (done != null) done.run();
                if (wasOn) restartLauncher(a, "stock icons");
                return;
            }
            if (which == was) return;
            Prefs.setStr(a, Features.LAUNCHER_ICON_PACK, pick);
            if (done != null) done.run();
            restartLauncher(a, pick == null ? "stock icons" : names.get(which));
        });
        b.setNegativeButton(android.R.string.cancel, null).show();
    }

    private static final String ZIP_PICK = ":zip-pick", ZIP_REMOVE = ":zip-remove", ZIP_PRESET = ":preset:";   // never a package name

    /** Restarts every home app so stock and third-party launchers reload icons (each clears its own cache on start). */
    public static void restartLauncher(final Activity a, String what) {
        Toast.makeText(a, "Restarting the launcher with " + what + "…", Toast.LENGTH_SHORT).show();
        com.hammad.redmagictweaks.IconMask.refreshHomes(a);
    }
}
