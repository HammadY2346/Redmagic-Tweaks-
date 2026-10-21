package com.hammad.redmagictweaks.xposed;

import android.content.Context;

import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.ThemeStoreFeatures;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.zte.beautify (Theme Store) — merge every collaboration pack into the local lists, free paid
 * content, skip the account login.
 *
 * How the store finds collaboration packs on this firmware: the local theme/lock-screen list is ONE
 * xml from com.zte.mifavor.theme.resource (theme_wallpaper_config_<theme-feature>, else
 * theme_wallpaper_config_<product>_<variant-id>, else theme_wallpaper_config_<launcher-feature>…)
 * picked by XmlParseResource.generatePriorityList from Utilities.getThemeResourceFeature() and
 * persist.vendor.custom.variant.id; each entry points at /system/etc/custom_config/themes/[VARIANT/]<id>.zmtp
 * (theme + wallpapers + AOD + ringtones inside) or is flagged getUrlFromServer and fetched by
 * LqRpcService.getPresetResourceUrl(deviceName=ro.product.name, id, type). Static wallpapers come from
 * /system/etc/custom_config/wallpapers/[VARIANT]/ with a class.txt order file. The online store itself
 * is filtered only by Build.MODEL (no variant parameter is sent), so "all packs" is a local-list merge.
 *
 * The Utilities spoof itself lives in ThemeHooks (single hook); this class only sets a per-thread
 * override (forced) that ThemeHooks honours first while another edition's config is parsed.
 */
final class ThemeStoreHooks {
    private ThemeStoreHooks() {}

    private static final String THEME_XML = "com.zte.beautify.model.local.parser.xml.DynamicWallpaperThemeXml";
    private static final String WP_LOADER = "com.zte.beautify.model.local.loader.ResourceStaticWallpaperLoader";
    private static final String BEAN = "com.zte.beautify.model.local.data.Bean";
    private static final String ACCOUNT = "com.zte.beautify.model.remote.zteaccount.ZteAccountManager";
    private static final String ACCOUNT_INFO = "com.zte.beautify.model.remote.zteaccount.ZteAccountInfo";
    private static final String RES_MGR = "com.zte.beautify.model.ResourceManager";
    private static final String RPC = "com.zte.beautify.model.remote.netservice.LqRpcService";
    private static final String THEME_RES_PKG = "com.zte.mifavor.theme.resource";
    private static final String THEME_ROOT = "/system/etc/custom_config/themes/";
    private static final String WP_ROOT = "/system/etc/custom_config/wallpapers/";

    /** {theme-feature, variant-id} forced on the current thread while an extra config is parsed. */
    private static final ThreadLocal<String[]> forced = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> merging = new ThreadLocal<>();
    /** srcId -> model name (e.g. NX809J) of a merged preset pack, for the server URL lookup. */
    private static final Map<String, String> presetModel = new HashMap<>();
    /** Parsed theme_wallpaper_config of each edition (theme feature -> DynamicWallpaperThemeXml). */
    private static final Map<String, Object> xmlCache = new HashMap<>();
    /** Extra wallpaper dirs appended by the dir hook (so the class.txt merge knows them). */
    private static final List<String> extraWpDirs = new ArrayList<>();
    /** Diagnostics are printed once per process (per loader name). */
    private static final Set<String> diagDone = new HashSet<>();
    private static volatile boolean fsReported;
    private static volatile boolean wpReported;

    /** Read by ThemeHooks: the edition the current thread must pretend to be, or null. */
    static String[] forced() { return forced.get(); }

    static void install(ClassLoader cl) {
        themeMerge(cl);
        wallpaperMerge(cl);
        login(cl);
        presetUrl(cl);
        Lx.i("ThemeStore: hooks ready");
    }

    // ---- which packs to merge ----

    /** {label, theme-feature, variant-id} of every edition to merge, the phone's own stock edition first. */
    private static List<String[]> variants() {
        List<String[]> out = new ArrayList<>();
        boolean all = Cfg.choice(ThemeStoreFeatures.TS_PACK_SCOPE) == 1;
        // When the user spoofs another edition, the stock list of this phone is no longer loaded by
        // the app itself: merge it back in from the values the phone really reports.
        String[] chosen = ThemeHooks.variant();
        String rf = ThemeHooks.realFeature, rv = ThemeHooks.realVariant;
        if (chosen != null && rf != null) out.add(new String[]{"this phone's stock edition", rf, rv == null ? "" : rv});
        for (int i = 1; i < Features.THEME_VARIANTS.length; i++) {
            String[] v = Features.THEME_VARIANTS[i];
            if (v[1] == null || v[1].isEmpty()) continue;
            if (!all && !v[1].startsWith("nx789")) continue;
            out.add(v);
        }
        return out;
    }

    /** "nx809j_w_saga" -> "NX809J"; empty when the feature does not start with a model name. */
    private static String modelOf(String feature) {
        if (feature == null || feature.isEmpty()) return "";
        int i = feature.indexOf('_');
        String m = (i > 0 ? feature.substring(0, i) : feature).toUpperCase();
        return m.startsWith("NX") || m.startsWith("PQ") ? m : "";
    }

    // ---- 1. themes + preset lock-screen wallpapers: re-parse the config of every edition and merge ----

    private static void themeMerge(ClassLoader cl) {
        final Class<?> xml = H.cls(cl, THEME_XML);
        if (xml == null) return;
        for (final String name : new String[]{"loadTheme", "loadDynamicWallpaper"}) {
            int n = H.hookAll(xml, name, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (Boolean.TRUE.equals(merging.get())) return;
                    Object r = p.getResult();
                    if (!(r instanceof List)) return;
                    try {
                        merging.set(Boolean.TRUE);
                        reportFirmwareOnce();
                        reportBaseOnce((List<Object>) r, name);
                        if (!Cfg.on(ThemeStoreFeatures.TS_ALL_PACKS)) return;
                        mergeInto((List<Object>) r, xml, name);
                    } catch (Throwable t) {
                        Lx.e("ThemeStore: merge " + name, t);
                    } finally {
                        merging.set(Boolean.FALSE);
                        forced.remove();
                    }
                }
            });
            if (n == 0) Lx.w("ThemeStore: " + name + " not hooked");
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergeInto(List<Object> list, Class<?> xmlCls, String loader) {
        boolean diag;
        synchronized (diagDone) { diag = diagDone.add(loader); }
        int before = list.size();
        Map<String, String> paths = new HashMap<>();   // srcId -> resource path of what the app loaded itself
        for (Object b : list) { String id = srcId(b); if (id != null) paths.put(id, str(call(b, "getResourcePackagePath"))); }
        int added = 0, variantsTried = 0;
        for (String[] v : variants()) {
            variantsTried++;
            forced.set(new String[]{v[1], v[2]});
            int parsed = 0, onDiskN = 0, serverN = 0, dup = 0, skipped = 0;
            String cfg = configExists(v[1]) ? "found" : "MISSING (app falls back to the product / launcher-feature config)";
            try {
                Object x;
                synchronized (xmlCache) { x = xmlCache.get(v[1]); }
                if (x == null) {
                    x = XposedHelpers.newInstance(xmlCls);
                    XposedHelpers.callMethod(x, "getXmlObject");
                    synchronized (xmlCache) { xmlCache.put(v[1], x); }
                }
                Object obj = XposedHelpers.getObjectField(x, "mXmlObject");
                if (obj == null) {
                    if (diag) Lx.w("ThemeStore: edition " + v[0] + " (" + v[1] + "): config " + cfg + ", no xml object");
                    continue;
                }
                Object extra = XposedHelpers.callMethod(x, loader, obj);
                if (!(extra instanceof List)) continue;
                parsed = ((List<Object>) extra).size();
                for (Object b : (List<Object>) extra) {
                    String id = srcId(b);
                    if (id == null) continue;
                    String pkg = str(call(b, "getResourcePackagePath"));
                    if (paths.containsKey(id)) {
                        dup++;
                        String have = paths.get(id);
                        if (diag && pkg != null && have != null && !pkg.equals(have))
                            Lx.i("ThemeStore:   = " + id + " already listed from " + have + " (this edition would use " + pkg + ")");
                        continue;
                    }
                    boolean onDisk = pkg != null && new File(pkg).exists();
                    boolean server = Boolean.TRUE.equals(call(b, "isNeedDownloadFromServer"));
                    if (diag) Lx.i("ThemeStore:   " + (onDisk || server ? "+ " : "- ") + id + " " + pkg
                            + (onDisk ? " [on disk]" : server ? " [server download]" : " [NOT on this phone, not downloadable]"));
                    if (!onDisk && !server) { skipped++; continue; }   // nothing to apply or fetch for this edition
                    paths.put(id, pkg);
                    list.add(b);
                    added++;
                    if (onDisk) onDiskN++; else serverN++;
                    String model = modelOf(v[1]);
                    if (!onDisk && !model.isEmpty()) synchronized (presetModel) { presetModel.put(id, model); }
                }
            } catch (Throwable t) {
                Throwable c = t.getCause() != null ? t.getCause() : t;
                Lx.w("ThemeStore: config of " + v[0] + " (" + v[1] + ", cfg " + cfg + ") skipped: " + c);
            } finally {
                forced.remove();
            }
            if (diag) Lx.i("ThemeStore: edition " + v[0] + " (" + v[1] + "/" + v[2] + "): config "
                    + cfg + ", " + loader + " parsed " + parsed + ", already listed " + dup
                    + ", added " + (onDiskN + serverN) + " (on disk " + onDiskN + ", server " + serverN + "), skipped " + skipped);
        }
        Lx.i("ThemeStore: " + loader + " merged " + added + " extra entries from " + variantsTried + " editions (list " + before + " -> " + list.size() + ")");
    }

    /** Once per loader: what the app loaded on its own (with the spoof applied), id + pack path + whether the pack exists. */
    private static void reportBaseOnce(List<Object> list, String loader) {
        synchronized (diagDone) { if (!diagDone.add("base:" + loader)) return; }
        int missing = 0;
        StringBuilder sb = new StringBuilder();
        for (Object b : list) {
            String id = srcId(b);
            String pkg = str(call(b, "getResourcePackagePath"));
            boolean onDisk = pkg != null && new File(pkg).exists();
            boolean server = Boolean.TRUE.equals(call(b, "isNeedDownloadFromServer"));
            if (!onDisk && !server) missing++;
            sb.append(id).append(onDisk ? " " : server ? "(server) " : "(MISSING) ");
        }
        String[] v = ThemeHooks.variant();
        Lx.i("ThemeStore: " + loader + ": app loaded " + list.size() + " entries itself"
                + (v == null ? "" : " as edition " + v[0]) + ", " + missing + " without a pack file: " + sb.toString().trim());
    }

    /** Does com.zte.mifavor.theme.resource ship theme_wallpaper_config_<feature>? (first choice of the priority list) */
    private static boolean configExists(String feature) {
        try {
            Context c = Lx.context();
            if (c == null || feature == null || feature.isEmpty()) return false;
            Context r = c.createPackageContext(THEME_RES_PKG, 3);
            return r.getResources().getIdentifier("theme_wallpaper_config_" + feature.toLowerCase(), "xml", THEME_RES_PKG) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** One line about what the firmware actually ships, so the log says whether a pack can exist at all. */
    private static void reportFirmwareOnce() {
        if (fsReported) return;
        fsReported = true;
        try {
            Lx.i("ThemeStore: firmware themes " + dirSummary(THEME_ROOT, ".zmtp") + "; wallpapers " + dirSummary(WP_ROOT, null));
        } catch (Throwable t) {
            Lx.w("ThemeStore: firmware listing: " + t);
        }
    }

    private static String dirSummary(String root, String ext) {
        File[] fs = new File(root).listFiles();
        if (fs == null) return root + " not readable";
        int files = 0;
        StringBuilder subs = new StringBuilder();
        for (File f : fs) {
            if (f.isDirectory()) {
                File[] in = f.listFiles();
                int n = 0;
                if (in != null) for (File g : in) if (ext == null || g.getName().endsWith(ext)) n++;
                subs.append(f.getName()).append('(').append(n).append(") ");
            } else if (ext == null || f.getName().endsWith(ext)) {
                files++;
            }
        }
        return root + ": " + files + (ext == null ? " files" : " packs") + " at root, folders [" + subs.toString().trim() + "]";
    }

    private static Object call(Object o, String m) {
        try { return XposedHelpers.callMethod(o, m); } catch (Throwable t) { return null; }
    }

    private static String srcId(Object bean) { return str(call(bean, "getSrcId")); }

    private static String str(Object o) { return o instanceof String ? (String) o : null; }

    // ---- 2. static desktop wallpapers: scan every edition's folder and merge its class.txt ----

    private static void wallpaperMerge(ClassLoader cl) {
        final Class<?> loader = H.cls(cl, WP_LOADER);
        if (loader == null) return;
        Method dirs = H.resolveAny(loader, List.class, H.types(), "getTraverseStaticWPDirPath");
        H.hook(dirs, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                List<String> cand = candidateWpDirs();
                if (cand.isEmpty()) return;
                Object r = p.getResult();
                if (!(r instanceof List)) return;
                try {
                    @SuppressWarnings("unchecked") List<String> l = new ArrayList<>((List<String>) r);
                    String extra;
                    synchronized (extraWpDirs) {
                        extraWpDirs.clear();
                        for (String d : cand) {
                            if (l.contains(d) || l.contains(d.substring(0, d.length() - 1))) continue;
                            l.add(d);
                            extraWpDirs.add(d);
                        }
                        extra = extraWpDirs.toString();
                    }
                    if (!wpReported) { wpReported = true; Lx.i("ThemeStore: wallpaper folders " + r + " + " + extra); }
                    p.setResult(l);
                } catch (Throwable t) { Lx.e("ThemeStore: wallpaper dirs", t); }
            }
        });
        Method load = H.resolveAny(loader, boolean.class, H.types(), "loadPresetStaticWallpaper");
        H.hook(load, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { mergeClassOrders(p.thisObject); } catch (Throwable t) { Lx.e("ThemeStore: class.txt merge", t); }
            }
        });
    }

    /**
     * /system/etc/custom_config/wallpapers/<VARIANT>/ of the spoofed edition (the loader reads the real
     * variant id straight from SystemProperties, so the spoof alone never reaches it), plus every known
     * edition and any IP_* folder found when "all packs" is on. Only folders that exist.
     */
    private static List<String> candidateWpDirs() {
        List<String> out = new ArrayList<>();
        String[] chosen = ThemeHooks.variant();
        if (chosen != null && chosen[2] != null && !chosen[2].isEmpty()) addDir(out, WP_ROOT + chosen[2] + "/");
        if (!Cfg.on(ThemeStoreFeatures.TS_ALL_PACKS)) return out;
        for (String[] v : variants()) {
            if (v[2] == null || v[2].isEmpty()) continue;
            addDir(out, WP_ROOT + v[2] + "/");
        }
        if (Cfg.choice(ThemeStoreFeatures.TS_PACK_SCOPE) == 1) {
            File[] subs = new File(WP_ROOT).listFiles();
            if (subs != null) for (File f : subs) {
                if (f.isDirectory() && f.getName().startsWith("IP_")) addDir(out, WP_ROOT + f.getName() + "/");
            }
        }
        return out;
    }

    private static void addDir(List<String> out, String d) {
        if (!out.contains(d) && new File(d).isDirectory()) out.add(d);
    }

    @SuppressWarnings("unchecked")
    private static void mergeClassOrders(Object loader) {
        List<String> dirs;
        synchronized (extraWpDirs) { dirs = new ArrayList<>(extraWpDirs); }
        if (dirs.isEmpty()) return;
        Map<String, HashMap<String, List<String>>> classOrder = (Map<String, HashMap<String, List<String>>>) XposedHelpers.getObjectField(loader, "mClassOrder");
        List<String> classNames = (List<String>) XposedHelpers.getObjectField(loader, "mClassNameList");
        List<String> idList = (List<String>) XposedHelpers.getObjectField(loader, "mWallpaperIdList");
        int added = 0;
        for (String dir : dirs) {
            File f = new File(dir, "class.txt");
            if (!f.exists()) { Lx.w("ThemeStore: " + dir + " has no class.txt, its wallpapers stay unsorted"); continue; }
            String cls = null, section = null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String s = line.trim();
                    if (s.isEmpty() || s.contains("#")) continue;
                    if (s.contains("<")) {
                        if (s.contains("<main>")) section = "main";
                        else if (s.contains("<secondary>")) section = "secondary";
                        else if (s.contains("<original>")) section = "original";
                        else {
                            if (s.startsWith("﻿")) s = s.substring(1);
                            cls = s.length() >= 2 ? s.substring(1, s.length() - 1) : s;
                            if (!classOrder.containsKey(cls)) classOrder.put(cls, new HashMap<>());
                            if (!classNames.contains(cls)) classNames.add(cls);
                        }
                        continue;
                    }
                    if (cls == null || section == null) continue;
                    if (!s.contains("default_wallpaper_") && !s.contains("com.zte.livewallpaper")) continue;
                    if (idList.contains(s)) continue;
                    HashMap<String, List<String>> m = classOrder.get(cls);
                    if (m == null) { m = new HashMap<>(); classOrder.put(cls, m); }
                    List<String> l = m.get(section);
                    if (l == null) { l = new ArrayList<>(); m.put(section, l); }
                    l.add(s);
                    idList.add(s);
                    added++;
                }
            } catch (Throwable t) {
                Lx.w("ThemeStore: class.txt of " + dir + ": " + t);
            }
        }
        Lx.i("ThemeStore: wallpapers merged " + added + " ids from " + dirs.size() + " edition folders");
    }

    // ---- 4. account login gate before downloads ----

    private static void login(ClassLoader cl) {
        Class<?> am = H.cls(cl, ACCOUNT);
        final Class<?> info = H.cls(cl, ACCOUNT_INFO);
        if (am == null || info == null) return;
        H.hook(H.find(am, "getCurrentUserInfo"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!Cfg.on(ThemeStoreFeatures.TS_NO_LOGIN) || p.getResult() != null) return;
                if (!H.calledFromPrefix("com.zte.beautify.view.common.preview")) return;
                try {
                    Object fake = XposedHelpers.newInstance(info);
                    XposedHelpers.setObjectField(fake, "userName", "rmtweaks");
                    p.setResult(fake);
                } catch (Throwable t) { Lx.e("ThemeStore: fake account", t); }
            }
        });
    }

    // ---- 5. server lookup of preset pack files: ask with the pack's own model name ----

    private static void presetUrl(ClassLoader cl) {
        Class<?> rm = H.cls(cl, RES_MGR);
        if (rm != null) {
            H.hook(H.find(rm, "getPresetResourceUrl", String.class, String.class, int.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    String id = str(p.args[1]);
                    if (!Cfg.on(ThemeStoreFeatures.TS_PRESET_ANY)) {
                        Lx.i("ThemeStore: preset url for " + id + " asked as " + p.args[0] + " (switch off, not rewritten)");
                        return;
                    }
                    String model;
                    synchronized (presetModel) { model = presetModel.get(id); }
                    if (model == null) {
                        // Not a merged pack: it came from the spoofed edition's own list, so use that model.
                        String[] v = ThemeHooks.variant();
                        if (v != null) model = modelOf(v[1]);
                    }
                    if (model != null && !model.isEmpty() && !model.equals(p.args[0])) {
                        Lx.i("ThemeStore: preset url for " + id + " asked as " + model + " (was " + p.args[0] + ")");
                        p.args[0] = model;
                    } else {
                        Lx.i("ThemeStore: preset url for " + id + " asked as " + p.args[0] + " (no other model known)");
                    }
                }
            });
        }
        // What the server answered (null = it does not offer this pack for that model).
        Class<?> rpc = H.cls(cl, RPC);
        if (rpc != null) {
            H.hook(H.find(rpc, "getPresetResourceUrl", String.class, String.class, int.class), new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Object r = p.getResult();
                    Lx.i("ThemeStore: server url for " + p.args[1] + " as " + p.args[0] + " type " + p.args[2] + " -> "
                            + (r == null ? "null (not offered / offline)" : "ok"));
                }
            });
        }
    }
}
