package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The charging animation sets (Features.CHARGE_SET_DEFS) on the app side: previews and whether a set can play on this
 * firmware. Kinds:
 * <ul>
 *   <li>table: one of System UI's own frame tables, found by its frame resource names (the ids change with every
 *       firmware, the names don't)</li>
 *   <li>file: frames the firmware keeps in /system/etc/custom_config/app/systemUI/charge (some editions only)</li>
 *   <li>asset / custom: our frames, served to System UI by LogProvider</li>
 *   <li>staged: our frames in stages, arg = folder base: &lt;base&gt;_normal (below 90 %), &lt;base&gt;_normal_mid then
 *       &lt;base&gt;_normal_full (from 90 %), and the same with _fast on a fast charger</li>
 * </ul>
 */
public final class ChargeSets {
    private ChargeSets() {}

    public static final String SYSTEMUI = "com.android.systemui";
    public static final String FILE_BASE = "/system/etc/custom_config/app/systemUI/charge/";
    /** Battery level from which a staged set plays its second part. */
    public static final int STAGE_FULL_FROM = 90;

    /** System UI frame table field -> drawable name prefix of its frames (charge_001 … charge_075). */
    public static String tablePrefix(String field) {
        switch (field) {
            case "Drawable": return "charge_";
            case "WireLessDrawable": return "wireless_charge_";
            case "RedMagic_Drawable": return "redmagic_charge_";
            case "RedMagic_Fast_Drawable": return "redmagic_fast_charge_";
            case "RedMagic_Wireless_Drawable": return "redmagic_wireless_charge_";
            case "Nubia_Game_Bottom_Drawable": return "game_charge_bottom_";
            default: return null;
        }
    }

    private static int[] tableIds(Context ctx, String field) {
        String prefix = tablePrefix(field);
        if (prefix == null) return null;
        try {
            Resources r = ctx.getPackageManager().getResourcesForApplication(SYSTEMUI);
            List<Integer> ids = new ArrayList<>();
            for (int k = 1; k <= 200; k++) {
                int id = r.getIdentifier(String.format(java.util.Locale.US, "%s%03d", prefix, k), "drawable", SYSTEMUI);
                if (id == 0) { if (k == 1) continue; break; }   // some tables start at 000
                ids.add(id);
            }
            if (ids.isEmpty()) {
                int id0 = r.getIdentifier(prefix + "000", "drawable", SYSTEMUI);
                if (id0 != 0) ids.add(id0);
            }
            int[] out = new int[ids.size()];
            for (int k = 0; k < out.length; k++) out[k] = ids.get(k);
            return out;
        } catch (Throwable t) { return null; }
    }

    private static File file(String arg, int k) {
        String pat = arg.split("\\|")[0];
        return new File(FILE_BASE + String.format(pat, k < 10 ? "0" + k : String.valueOf(k)));
    }

    /**
     * "Follow the theme": the set a theme id gets, as {kind, arg}. Ids come with the theme-changed broadcast
     * (com.mifavor.intent.action.THEME_CHANGED_YRZX, extra resId) and may carry a path, so they are matched by "contains".
     */
    public static String[] forTheme(String themeId, boolean fast) {
        if (themeId != null) {
            if (themeId.contains("default_theme_54")) return new String[]{"asset", fast ? "cn_pink_fast_charge" : "cn_pink_charge"};   // CAMELLYA (Chun)
            if (themeId.contains("default_theme_55")) return new String[]{"staged", "cn_staff"};                                     // Black Myth: Wukong
        }
        return new String[]{"stock", ""};
    }

    /** Asset folders of a staged set, in the order the preview plays them (normal stages; fast is the same art). */
    public static String[] stagedPreviewFolders(String base) {
        return new String[]{base + "_normal", base + "_normal_mid", base + "_normal_full"};
    }

    /** True when set {@code i} has frames to play on this phone (the picker only lists those, plus the current pick). */
    public static boolean available(Context ctx, int i) {
        if (i <= 0 || i >= Features.CHARGE_SET_DEFS.length) return true;
        String kind = Features.CHARGE_SET_DEFS[i][1], arg = Features.CHARGE_SET_DEFS[i][2];
        switch (kind) {
            case "table": {
                String prefix = tablePrefix(arg);
                if (prefix == null) return false;
                try {
                    Resources r = ctx.getPackageManager().getResourcesForApplication(SYSTEMUI);
                    return r.getIdentifier(prefix + "001", "drawable", SYSTEMUI) != 0 || r.getIdentifier(prefix + "000", "drawable", SYSTEMUI) != 0;
                } catch (Throwable t) { return false; }
            }
            case "file": return file(arg, 1).isFile();
            case "asset": return Frames.assetCount(ctx, arg) > 0;
            case "staged": return Frames.assetCount(ctx, arg + "_normal") > 0;
            case "flag": return false;   // no set uses it any more
            case "theme": return Frames.assetCount(ctx, "cn_pink_charge") > 0;
            default: return true;
        }
    }

    /** Still preview: a mid-animation frame. */
    public static Drawable still(Context ctx, int i) {
        if (i <= 0 || i >= Features.CHARGE_SET_DEFS.length) return null;
        String kind = Features.CHARGE_SET_DEFS[i][1], arg = Features.CHARGE_SET_DEFS[i][2];
        try {
            switch (kind) {
                // middle frame: several sets start from an empty screen
                case "asset": return Previews.asset(ctx, Frames.assetPath(ctx, arg, Math.max(0, Frames.assetCount(ctx, arg) / 2)));
                case "staged": { String f = arg + "_normal"; return Previews.asset(ctx, Frames.assetPath(ctx, f, Math.max(0, Frames.assetCount(ctx, f) / 2))); }
                case "custom": return Previews.file(Frames.frame(ctx, Math.min(12, Math.max(0, Frames.count(ctx) - 1))));
                case "theme": return Previews.asset(ctx, Frames.assetPath(ctx, "cn_pink_charge", Math.max(0, Frames.assetCount(ctx, "cn_pink_charge") / 2)));
                case "table": {
                    int[] ids = tableIds(ctx, arg);
                    if (ids == null || ids.length == 0) return null;
                    Resources r = ctx.getPackageManager().getResourcesForApplication(SYSTEMUI);
                    return r.getDrawable(ids[ids.length / 2], r.newTheme());
                }
                case "file": {
                    int n = 0;
                    while (n < 120 && file(arg, n + 1).isFile()) n++;
                    return n == 0 ? null : Previews.file(file(arg, Math.max(1, n / 2)));
                }
                default: return null;
            }
        } catch (Throwable t) { return null; }
    }

    /** Animated preview: every frame in order. */
    public static Drawable[] frames(Context ctx, int i) {
        if (i <= 0 || i >= Features.CHARGE_SET_DEFS.length) return null;
        String kind = Features.CHARGE_SET_DEFS[i][1], arg = Features.CHARGE_SET_DEFS[i][2];
        try {
            List<Drawable> out = new ArrayList<>();
            switch (kind) {
                case "asset": addAssets(ctx, arg, out); break;
                case "staged": for (String f : stagedPreviewFolders(arg)) addAssets(ctx, f, out); break;
                case "theme": addAssets(ctx, "cn_pink_charge", out); for (String f : stagedPreviewFolders("cn_staff")) addAssets(ctx, f, out); break;
                case "custom": { int n = Math.min(120, Frames.count(ctx)); for (int k = 0; k < n; k++) out.add(Previews.file(Frames.frame(ctx, k))); break; }
                case "table": {
                    int[] ids = tableIds(ctx, arg);
                    if (ids == null) break;
                    Resources r = ctx.getPackageManager().getResourcesForApplication(SYSTEMUI);
                    for (int id : ids) out.add(r.getDrawable(id, r.newTheme()));
                    break;
                }
                case "file": for (int k = 1; k <= 120; k++) { File f = file(arg, k); if (!f.isFile()) break; out.add(Previews.file(f)); } break;
                default: break;
            }
            out.removeIf(d -> d == null);
            return out.isEmpty() ? null : out.toArray(new Drawable[0]);
        } catch (Throwable t) { return null; }
    }

    private static void addAssets(Context ctx, String folder, List<Drawable> out) {
        int n = Frames.assetCount(ctx, folder);
        for (int k = 0; k < n; k++) out.add(Previews.asset(ctx, Frames.assetPath(ctx, folder, k)));
    }
}
