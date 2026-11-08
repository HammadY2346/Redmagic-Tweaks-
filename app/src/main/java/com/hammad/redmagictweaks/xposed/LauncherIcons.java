package com.hammad.redmagictweaks.xposed;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.hammad.redmagictweaks.Features;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * App icons: icon pack + icon shape globally; label size / hidden labels / icon size for the stock launcher.
 *
 * Verified in the decompiled ZTE launcher (China 11.0.11 and EU 11.0.4, same code):
 *  - Every app icon of home, drawer and folders comes from the launcher's IconProvider, which asks
 *    {@link LauncherActivityInfo#getIcon(int)} first. Hooking that framework method from zygote themes every
 *    launcher that uses it (stock and third-party), matching how Settings → Icon shape rewrites the system mask.
 *  - Shape: Launcher3 ThemeManager reads android:string/config_icon_mask; we answer that from zygote so every
 *    process sees the selected path (same effect as the system icon-shape overlay).
 *  - Icons are cached as bitmaps in databases/app_icons.db: when the pack or the shape changed since the last start,
 *    any process that has that cache deletes it before opening it ({@link #clearCacheIfChanged}).
 *  - Label size / hidden labels / BubbleTextView icon size stay stock-launcher-only (ZTE resources / prefs).
 */
public final class LauncherIcons {
    private LauncherIcons() {}

    static final String PKG = "com.zte.mifavor.launcher";

    /** Path data (viewport 100×100) for Features.LAUNCHER_ICON_SHAPES; index 0 = stock (null). */
    public static final String[] SHAPE_PATHS = {
            null,
            "M50,0 A50,50,0,1,1,50,100 A50,50,0,1,1,50,0 Z",                                                        // circle
            "M50,0 C10,0 0,10 0,50 C0,90 10,100 50,100 C90,100 100,90 100,50 C100,10 90,0 50,0 Z",                  // squircle
            "M50,0 L80,0 A20,20,0,0,1,100,20 L100,80 A20,20,0,0,1,80,100 L20,100 A20,20,0,0,1,0,80 L0,20 A20,20,0,0,1,20,0 Z", // rounded square
            "M0,0 L100,0 L100,100 L0,100 Z",                                                                       // square
            "M50,0 A50,50,0,0,1,100,50 L100,85 A15,15,0,0,1,85,100 L50,100 A50,50,0,0,1,50,0 Z",                    // teardrop
            "M50,0 C16,0 0,16 0,50 C0,84 16,100 50,100 C84,100 100,84 100,50 C100,16 84,0 50,0 Z",                  // One UI (softer squircle)
            "M50,0 L100,50 L50,100 L0,50 Z",                                                                       // diamond
    };
    static final float[] LABEL_SCALE = {1f, 0.8f, 0.9f, 1.1f, 1.2f, 1.3f};

    private static boolean installed;
    private static boolean zygoteInstalled;

    /**
     * Icon pack + adaptive-icon mask in every app process. Android exposes {@code config_icon_mask} through
     * {@link Resources#getSystem()}; installing these hooks from {@code initZygote} makes them visible to
     * third-party launchers the same way Settings → Icon shape does.
     */
    static void installZygote() {
        if (zygoteInstalled) return;
        zygoteInstalled = true;
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getString", int.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        int id = (Integer) p.args[0];
                        if (id == 0 || id != maskId()) return;
                        String path = shapePath();
                        if (path == null) return;
                        p.setResult(path);
                        logOnce("shape", "Icons: config_icon_mask -> shape " + Cfg.choice(Features.LAUNCHER_ICON_SHAPE));
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { Lx.e("Icons: zygote Resources#getString hook", t); }
        try {
            XposedHelpers.findAndHookMethod(LauncherActivityInfo.class, "getIcon", int.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!inLauncher()) return;
                        Pack pack = Pack.current();
                        if (pack == null) return;
                        LauncherActivityInfo info = (LauncherActivityInfo) p.thisObject;
                        Drawable d = pack.icon(info.getComponentName(), (Integer) p.args[0], (Drawable) p.getResult());
                        if (d != null) p.setResult(d);
                    } catch (Throwable t) { logOnce("pack", "Icons: icon pack lookup failed: " + t); }
                }
            });
        } catch (Throwable t) { Lx.e("Icons: zygote LauncherActivityInfo#getIcon hook", t); }
        // One UI Home loads icons through PackageItemInfo.loadIcon / PackageManager, not only LauncherActivityInfo.
        try {
            XposedHelpers.findAndHookMethod(PackageItemInfo.class, "loadIcon", PackageManager.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { applyPack(p, (PackageItemInfo) p.thisObject); }
            });
        } catch (Throwable t) { Lx.e("Icons: zygote PackageItemInfo hook", t); }
        try {
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", null, "getActivityIcon", ComponentName.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        if (!inLauncher()) return;
                        Pack pack = Pack.current();
                        if (pack == null) return;
                        Drawable d = pack.icon((ComponentName) p.args[0], 0, (Drawable) p.getResult());
                        if (d != null) p.setResult(d);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { Lx.e("Icons: zygote getActivityIcon hook", t); }
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getText", int.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        int id = (Integer) p.args[0];
                        if (id == 0 || id != maskId()) return;
                        String path = shapePath();
                        if (path != null) p.setResult(path);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) { Lx.e("Icons: zygote Resources#getText hook", t); }
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { clearCacheIfChanged((Context) p.args[0]); } catch (Throwable t) { Lx.e("Icons: cache check", t); }
                }
            });
        } catch (Throwable t) { Lx.e("Icons: zygote Application#attach hook", t); }
    }

    /** True in a home-app process. The name is cached only after a real app package is known, so zygote does not stick it on every child. */
    private static volatile String procName;
    private static boolean inLauncher() {
        String p = procName;
        if (p == null) {
            try { p = (String) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", null), "currentPackageName"); }
            catch (Throwable t) { p = null; }
            if (p == null || p.isEmpty() || "android".equals(p)) return false;
            procName = p;
        }
        return "com.zte.mifavor.launcher".equals(p) || "com.sec.android.app.launcher".equals(p) || p.contains("launcher") || p.contains("lawnchair") || p.contains("nova");
    }

    /** Swap a stock icon for the selected pack. No-op while no pack is selected. */
    private static void applyPack(de.robv.android.xposed.XC_MethodHook.MethodHookParam p, PackageItemInfo info) {
        try {
            if (!inLauncher()) return;
            Pack pack = Pack.current();
            if (pack == null || info == null || info.packageName == null) return;
            String cls = info.name != null ? info.name : info.packageName;
            if (cls.startsWith(".")) cls = info.packageName + cls;
            Drawable d = pack.icon(new ComponentName(info.packageName, cls), 0, (Drawable) p.getResult());
            if (d != null) p.setResult(d);
        } catch (Throwable ignored) { }
    }

    /** Stock-launcher-only extras (label size, hide labels, BubbleTextView icon size). Pack/shape come from {@link #installZygote()}. */
    static void install(ClassLoader cl) {
        if (installed) return;
        installed = true;
        int n = 0;

        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getInteger", int.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        int i = labelChoice();
                        if (i <= 0 || i >= LABEL_SCALE.length || !(p.getResult() instanceof Integer)) return;
                        String name = textSizeName((Resources) p.thisObject, (Integer) p.args[0]);
                        if (name == null) return;
                        int v = (Integer) p.getResult();
                        int s = Math.max(1, Math.round(v * LABEL_SCALE[i]));
                        p.setResult(s);
                        logOnce("label:" + name, "Icons: " + name + " " + v + " -> " + s);
                    } catch (Throwable ignored) { }
                }
            });
            n++;
        } catch (Throwable t) { Lx.e("Icons: Resources#getInteger hook", t); }

        // Hidden labels: the stock launcher's "icon_no_title" pref
        try {
            Class<?> spi = XposedHelpers.findClassIfExists("android.app.SharedPreferencesImpl", null);
            if (spi != null) {
                XposedHelpers.findAndHookMethod(spi, "getBoolean", String.class, boolean.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!"icon_no_title".equals(p.args[0])) return;
                        try {
                            if (Cfg.on(Features.LAUNCHER_HIDE_LABELS)) { p.setResult(true); logOnce("nolabel", "Icons: app labels hidden"); }
                        } catch (Throwable ignored) { }
                    }
                });
                n++;
            }
        } catch (Throwable t) { Lx.e("Icons: SharedPreferencesImpl#getBoolean hook", t); }

        // Icon size: BubbleTextView (stock Launcher3)
        try { if (iconSize(cl)) n++; } catch (Throwable t) { Lx.e("Icons: icon size hook", t); }

        Lx.i("Icons: stock hooks ready (" + n + "/3) pack=" + Cfg.str(Features.LAUNCHER_ICON_PACK) + " shape=" + Cfg.choice(Features.LAUNCHER_ICON_SHAPE)
                + " iconSize=" + Cfg.choice(Features.LAUNCHER_ICON_SIZE) + " labelSize=" + Cfg.choice(Features.LAUNCHER_LABEL_SIZE) + " hideLabels=" + Cfg.on(Features.LAUNCHER_HIDE_LABELS));
    }

    // ------------------------------------------------------------------ helpers

    private static final java.util.Map<Object, Boolean> sized = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static volatile java.lang.reflect.Field sizeField;
    private static volatile boolean sizeFieldSearched;

    /** Scales BubbleTextView's icon size once per view, right after its constructor (all constructors chain into one). */
    private static boolean iconSize(ClassLoader cl) {
        final Class<?> btv = XposedHelpers.findClassIfExists("com.android.launcher3.BubbleTextView", cl);
        if (btv == null) { Lx.w("Icons: BubbleTextView not found, icon size stays stock"); return false; }
        de.robv.android.xposed.XposedBridge.hookAllConstructors(btv, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object v = p.thisObject;
                if (v.getClass() != btv && !btv.isInstance(v)) return;
                int i = Cfg.choice(Features.LAUNCHER_ICON_SIZE);
                if (i <= 0 || i >= Features.ICON_SIZE_SCALE.length) return;
                if (sized.put(v, Boolean.TRUE) != null) return;          // the outer constructor runs this again
                try {
                    int now = (Integer) XposedHelpers.callMethod(v, "getIconSize");
                    if (now <= 0) { sized.remove(v); return; }           // not set yet: the next constructor in the chain retries
                    java.lang.reflect.Field f = sizeField(btv, v, now);
                    if (f == null) return;
                    int to = Math.max(8, Math.round(now * Features.ICON_SIZE_SCALE[i]));
                    f.setInt(v, to);
                    logOnce("iconsize", "Icons: icon size " + now + " -> " + to + " px (field " + f.getName() + ")");
                } catch (Throwable t) { logOnce("iconsize-err", "Icons: icon size failed: " + t); }
            }
        });
        return true;
    }

    /** The int field of BubbleTextView holding the icon size: "mIconSize" when the build keeps names, else the one int
     *  field whose value equals getIconSize() (searched once; null if none or ambiguous). */
    private static java.lang.reflect.Field sizeField(Class<?> btv, Object v, int value) throws IllegalAccessException {
        if (sizeFieldSearched) return sizeField;
        java.lang.reflect.Field named = null, only = null;
        int matches = 0;
        for (java.lang.reflect.Field f : btv.getDeclaredFields()) {
            if (f.getType() != int.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            if ("mIconSize".equals(f.getName())) named = f;
            if (f.getInt(v) == value) { matches++; only = f; }
        }
        sizeField = named != null ? named : matches == 1 ? only : null;
        sizeFieldSearched = true;
        if (sizeField == null) Lx.w("Icons: icon size field not found (" + matches + " int fields equal " + value + "), icon size stays stock");
        return sizeField;
    }

    private static final java.util.Set<String> logged = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private static void logOnce(String tag, String msg) { if (logged.add(tag)) Lx.i(msg); }

    private static int maskId = -1;
    private static int maskId() {
        if (maskId == -1) {
            try { maskId = Resources.getSystem().getIdentifier("config_icon_mask", "string", "android"); } catch (Throwable t) { maskId = 0; }
        }
        return maskId;
    }

    private static long shapeAt;
    private static String shapeCached;
    private static String shapePath() {
        long now = System.currentTimeMillis();
        if (now - shapeAt > 2000) {
            int i = Cfg.choice(Features.LAUNCHER_ICON_SHAPE);
            shapeCached = i > 0 && i < SHAPE_PATHS.length ? SHAPE_PATHS[i] : null;
            shapeAt = now;
        }
        return shapeCached;
    }

    private static long labelAt;
    private static int labelCached;
    /** Label-size choice, re-read at most every 2 s (Resources#getInteger is called often). */
    private static int labelChoice() {
        long now = System.currentTimeMillis();
        if (now - labelAt > 2000) { labelCached = Cfg.choice(Features.LAUNCHER_LABEL_SIZE); labelAt = now; }
        return labelCached;
    }

    /** Name of the launcher's label-size integer with this id, or null. The ids are looked up through the launcher's own
     *  Resources (the system Resources can't resolve them), once one call came through those. */
    private static final Map<Integer, String> textIds = new HashMap<>();
    private static boolean textIdsReady;
    private static String textSizeName(Resources res, int id) {
        synchronized (textIds) {
            if (!textIdsReady) {
                if (res == Resources.getSystem()) return null;
                for (String n : new String[]{"config_icon_text_size", "config_icon_text_size_single_line", "config_icon_text_size_two_line",
                        "config_icon_text_size_ease", "config_icon_text_size_ease_big_font"}) {
                    try {
                        int i = res.getIdentifier(n, "integer", PKG);
                        if (i != 0) textIds.put(i, n);
                    } catch (Throwable ignored) { }
                }
                textIdsReady = !textIds.isEmpty();
            }
            return textIds.get(id);
        }
    }

    /** Delete cached icon bitmaps when the pack or shape is not what they were drawn with (any launcher with app_icons.db). */
    static void clearCacheIfChanged(Context c) {
        if (c == null) return;
        Context de;
        try { de = c.createDeviceProtectedStorageContext(); } catch (Throwable t) { de = null; }
        boolean stock = PKG.equals(c.getPackageName());
        boolean hasCache = false;
        for (Context k : new Context[]{c, de}) {
            if (k == null) continue;
            for (String f : new String[]{"app_icons.db", "app_icons.db-journal", "app_icons.db-wal", "app_icons.db-shm",
                    "icons.db", "icons.db-journal", "icons.db-wal", "icons.db-shm"}) {
                try { if (k.getDatabasePath(f).exists()) { hasCache = true; break; } } catch (Throwable ignored) { }
            }
            if (hasCache) break;
        }
        if (!stock && !hasCache) return;   // not a launcher icon cache process
        String pack = Cfg.str(Features.LAUNCHER_ICON_PACK);
        String sig = (pack == null ? "" : pack) + "|" + Cfg.choice(Features.LAUNCHER_ICON_SHAPE) + "|" + versionOf(c, pack);
        SharedPreferences sp = (de != null ? de : c).getSharedPreferences("rmtweaks_icons", Context.MODE_PRIVATE);
        String old = sp.getString("sig", null);
        if (sig.equals(old)) return;
        if (old == null && pack == null && Cfg.choice(Features.LAUNCHER_ICON_SHAPE) == 0) { sp.edit().putString("sig", sig).apply(); return; }   // first run, nothing themed
        int deleted = 0;
        for (Context k : new Context[]{c, de}) {
            if (k == null) continue;
            for (String f : new String[]{"app_icons.db", "app_icons.db-journal", "app_icons.db-wal", "app_icons.db-shm",
                    "icons.db", "icons.db-journal", "icons.db-wal", "icons.db-shm"}) {
                try { File file = k.getDatabasePath(f); if (file.exists() && file.delete()) deleted++; } catch (Throwable ignored) { }
            }
        }
        sp.edit().putString("sig", sig).commit();
        Lx.i("Icons: " + c.getPackageName() + " pack/shape changed (" + old + " -> " + sig + "), icon cache cleared (" + deleted + " files)");
    }

    private static String versionOf(Context c, String pkg) {
        if (pkg == null) return "";
        try { return String.valueOf(c.getPackageManager().getPackageInfo(pkg, 0).getLongVersionCode()); } catch (Throwable t) { return "?"; }
    }

    // ------------------------------------------------------------------ icon pack (ADW / Nova appfilter format)

    static class Pack {
        final String pkg;
        final Resources res;
        final Map<String, String> byComponent = new HashMap<>();   // "pkg/cls" -> drawable name
        final Map<String, String> byPackage = new HashMap<>();     // "pkg" -> drawable name (first item seen for the package)
        String back, mask, upon;
        float scale = 1f;
        final Map<String, Integer> ids = new HashMap<>();

        private static Pack cached;
        private static String cachedFor;
        private static long checkedAt;

        /** The selected icon pack, loaded once (the setting is re-read every 3 s), or null. */
        static synchronized Pack current() {
            long now = System.currentTimeMillis();
            if (now - checkedAt < 3000) return cached;
            checkedAt = now;
            String want = Cfg.str(Features.LAUNCHER_ICON_PACK);
            if (want == null || want.isEmpty()) { cached = null; cachedFor = null; return null; }
            if (want.equals(cachedFor)) return cached;
            Context app = currentApp();
            if (app == null) return null;                   // too early: try again on a later call
            cachedFor = want;
            cached = want.startsWith(com.hammad.redmagictweaks.IconZip.PREFIX) ? ZipPack.load(app, want) : load(app, want);
            return cached;
        }

        private static Pack load(Context app, String pkg) {
            try {
                Context pc = app.createPackageContext(pkg, Context.CONTEXT_IGNORE_SECURITY);
                Pack p = new Pack(pkg, pc.getResources());
                XmlPullParser x = null;
                InputStream in = null;
                int xmlId = p.res.getIdentifier("appfilter", "xml", pkg);
                if (xmlId != 0) x = p.res.getXml(xmlId);
                else {
                    in = pc.getAssets().open("appfilter.xml");
                    XmlPullParserFactory f = XmlPullParserFactory.newInstance();
                    x = f.newPullParser();
                    x.setInput(in, "UTF-8");
                }
                try { p.parse(x); } finally { if (in != null) try { in.close(); } catch (Throwable ignored) { } }
                Lx.i("Icons: icon pack " + pkg + " loaded: " + p.byComponent.size() + " icons" + (p.back != null ? ", iconback" : "")
                        + (p.mask != null ? ", iconmask" : "") + (p.upon != null ? ", iconupon" : "") + ", scale " + p.scale);
                return p;
            } catch (Throwable t) {
                Lx.e("Icons: icon pack " + pkg + " could not be loaded", t);
                return null;
            }
        }

        private static Context currentApp() {
            try { return (Context) XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", null), "currentApplication"); }
            catch (Throwable t) { return null; }
        }

        Pack(String pkg, Resources res) { this.pkg = pkg; this.res = res; }

        void parse(XmlPullParser x) throws Exception {
            for (int e = x.getEventType(); e != XmlPullParser.END_DOCUMENT; e = x.next()) {
                if (e != XmlPullParser.START_TAG) continue;
                String tag = x.getName();
                if ("item".equals(tag)) {
                    String comp = x.getAttributeValue(null, "component"), draw = x.getAttributeValue(null, "drawable");
                    if (comp == null || draw == null) continue;
                    // ComponentInfo{com.app/com.app.Main}
                    int a = comp.indexOf('{'), b = comp.lastIndexOf('}');
                    if (a < 0 || b <= a) continue;
                    String flat = comp.substring(a + 1, b).trim();
                    int slash = flat.indexOf('/');
                    if (slash <= 0) continue;
                    String p = flat.substring(0, slash), c = flat.substring(slash + 1);
                    if (c.startsWith(".")) c = p + c;
                    byComponent.put(p + "/" + c, draw);
                    if (!byPackage.containsKey(p)) byPackage.put(p, draw);
                } else if ("iconback".equals(tag)) { if (back == null) back = x.getAttributeValue(null, "img1"); }
                else if ("iconmask".equals(tag)) { if (mask == null) mask = x.getAttributeValue(null, "img1"); }
                else if ("iconupon".equals(tag)) { if (upon == null) upon = x.getAttributeValue(null, "img1"); }
                else if ("scale".equals(tag)) {
                    try { scale = Float.parseFloat(x.getAttributeValue(null, "factor")); } catch (Throwable ignored) { }
                }
            }
        }

        Drawable drawable(String name, int density) {
            if (name == null) return null;
            Integer id = ids.get(name);
            if (id == null) { id = res.getIdentifier(name, "drawable", pkg); if (id == 0) id = res.getIdentifier(name, "mipmap", pkg); ids.put(name, id); }
            if (id == 0) return null;
            return density > 0 ? res.getDrawableForDensity(id, density, null) : res.getDrawable(id, null);
        }

        /** The pack's icon for this activity (or for its package), or the stock icon drawn on the pack's back/mask; null = stock. */
        Drawable icon(ComponentName cn, int density, Drawable stock) {
            if (cn == null) return null;
            String name = byComponent.get(cn.getPackageName() + "/" + cn.getClassName());
            if (name == null) name = byPackage.get(cn.getPackageName());
            Drawable d = drawable(name, density);
            if (d != null) return d;
            if (stock == null || (back == null && mask == null && upon == null)) return null;
            return compose(stock, density);
        }

        /** ADW-style: the stock icon scaled, cut by the mask, over the back, under the upon layer. */
        Drawable compose(Drawable stock, int density) {
            Drawable b = drawable(back, density), m = drawable(mask, density), u = drawable(upon, density);
            Drawable ref = b != null ? b : m != null ? m : u;
            int size = ref != null && ref.getIntrinsicWidth() > 0 ? ref.getIntrinsicWidth() : 192;
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            int s = Math.round(size * (scale > 0 && scale <= 1.5f ? scale : 1f)), off = (size - s) / 2;
            Rect old = new Rect(stock.getBounds());
            stock.setBounds(off, off, off + s, off + s);
            stock.draw(c);
            stock.setBounds(old);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            if (m != null) {
                Bitmap mb = toBitmap(m, size);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                c.drawBitmap(mb, 0, 0, p);
            }
            if (b != null) {
                Bitmap bb = toBitmap(b, size);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
                c.drawBitmap(bb, 0, 0, p);
            }
            if (u != null) {
                p.setXfermode(null);
                c.drawBitmap(toBitmap(u, size), 0, 0, p);
            }
            return new BitmapDrawable(res, out);
        }

        private static Bitmap toBitmap(Drawable d, int size) {
            if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                Bitmap b = ((BitmapDrawable) d).getBitmap();
                return b.getWidth() == size && b.getHeight() == size ? b : Bitmap.createScaledBitmap(b, size, size, true);
            }
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, size, size);
            d.draw(c);
            return b;
        }
    }

    // ------------------------------------------------------------------ icon pack from a zip (com.hammad.redmagictweaks.IconZip)

    /** Icons read from the zip the app imported, copied once into the launcher's cache (it can't read the app's files). */
    static final class ZipPack extends Pack {
        private final java.util.zip.ZipFile zip;
        private final Map<String, String> entries = new HashMap<>();        // image stem (as named) -> zip entry
        private final Map<String, String> entriesLower = new HashMap<>();   // lower-case stem -> zip entry
        private final android.util.LruCache<String, Bitmap> bitmaps = new android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
        };

        private ZipPack(String value, Resources res, java.util.zip.ZipFile zip) { super(value, res); this.zip = zip; }

        static Pack load(Context app, String value) {
            try {
                File dir = app.getCacheDir();
                String ver = value.substring(com.hammad.redmagictweaks.IconZip.PREFIX.length()).replaceAll("[^0-9]", "");
                File local = new File(dir, "rmt_iconzip_" + ver + ".zip");
                if (!local.isFile() || local.length() == 0) {
                    File[] old = dir.listFiles((d, n) -> n.startsWith("rmt_iconzip_"));
                    if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    android.net.Uri uri = android.net.Uri.parse("content://" + com.hammad.redmagictweaks.LockVideoProvider.AUTHORITY + "/iconzip");
                    File tmp = new File(dir, "rmt_iconzip.tmp");
                    try (InputStream in = app.getContentResolver().openInputStream(uri); java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                        if (in == null) throw new java.io.FileNotFoundException("icon zip not served");
                        byte[] b = new byte[64 * 1024];
                        for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
                    }
                    if (!tmp.renameTo(local)) throw new java.io.IOException("couldn't keep the icon zip");
                }
                ZipPack p = new ZipPack(value, app.getResources(), new java.util.zip.ZipFile(local));
                p.index();
                Lx.i("Icons: icon zip loaded: " + p.byComponent.size() + " activity icons, " + p.byPackage.size() + " app icons"
                        + (p.back != null ? ", iconback" : "") + (p.mask != null ? ", iconmask" : "") + (p.upon != null ? ", iconupon" : ""));
                return p;
            } catch (Throwable t) {
                Lx.e("Icons: icon zip could not be loaded", t);
                return null;
            }
        }

        /** Highest-density copy of each image wins; then appfilter.xml (if any) or the file names give the mapping. */
        private void index() throws Exception {
            Map<String, Integer> rank = new HashMap<>();
            String appfilter = null;
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                java.util.zip.ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String n = e.getName(), base = n.substring(n.lastIndexOf('/') + 1);
                String low = base.toLowerCase(java.util.Locale.ROOT);
                if (low.equals("appfilter.xml")) { if (appfilter == null) appfilter = n; continue; }
                if (!(low.endsWith(".png") || low.endsWith(".webp") || low.endsWith(".jpg"))) continue;
                String stem = base.substring(0, base.lastIndexOf('.'));
                int r = densityRank(n);
                Integer had = rank.get(stem);
                if (had != null && had >= r) continue;
                rank.put(stem, r);
                entries.put(stem, n);
                entriesLower.put(stem.toLowerCase(java.util.Locale.ROOT), n);
            }
            if (appfilter != null) {
                try (InputStream in = zip.getInputStream(zip.getEntry(appfilter))) {
                    XmlPullParser x = XmlPullParserFactory.newInstance().newPullParser();
                    x.setInput(in, null);
                    parse(x);
                }
            }
            if (byComponent.isEmpty() && byPackage.isEmpty()) {
                // Xiaomi-style names: com.app.png (whole app) / com.app#com.app.Main.png (one activity)
                for (String stem : entries.keySet()) {
                    int hash = stem.indexOf('#');
                    if (hash > 0) {
                        String pk = stem.substring(0, hash), cls = stem.substring(hash + 1);
                        if (cls.startsWith(".")) cls = pk + cls;
                        byComponent.put(pk + "/" + cls, stem);
                        if (!byPackage.containsKey(pk)) byPackage.put(pk, stem);
                    } else if (stem.indexOf('.') > 0) {
                        byPackage.put(stem, stem);
                    }
                }
            }
            if (back == null && entriesLower.containsKey("iconback")) back = "iconback";
            if (mask == null && entriesLower.containsKey("iconmask")) mask = "iconmask";
            if (upon == null && entriesLower.containsKey("iconupon")) upon = "iconupon";
        }

        private static int densityRank(String path) {
            String p = path.toLowerCase(java.util.Locale.ROOT);
            if (p.contains("xxxhdpi")) return 6;
            if (p.contains("xxhdpi")) return 5;
            if (p.contains("xhdpi")) return 4;
            if (p.contains("hdpi")) return 3;
            if (p.contains("mdpi")) return 2;
            if (p.contains("nodpi")) return 7;
            return 1;
        }

        /** Like Pack.icon, plus RedMagic theme names (dots as underscores, "app-activity"), and the picked icon shape
         *  cut out of the image: theme icons are full squares, and the shape setting only reaches adaptive icons. */
        @Override Drawable icon(ComponentName cn, int density, Drawable stock) {
            if (cn == null) return null;
            String pk = cn.getPackageName(), cls = cn.getClassName();
            String name = byComponent.get(pk + "/" + cls);
            if (name == null) name = byPackage.get(pk);
            if (name == null) {
                String up = pk.replace('.', '_'), uc = cls.replace('.', '_');
                if (has(up + "-" + uc)) name = up + "-" + uc;
                else if (has(up)) name = up;
            }
            Drawable d = drawable(name, density);
            if (d != null) return shaped(name, d);
            if (stock == null || (back == null && mask == null && upon == null)) return null;
            return compose(stock, density);
        }

        private boolean has(String stem) { return entries.containsKey(stem) || entriesLower.containsKey(stem.toLowerCase(java.util.Locale.ROOT)); }

        private final android.util.LruCache<String, Bitmap> shapedCache = new android.util.LruCache<String, Bitmap>(16 * 1024 * 1024) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
        };

        private Drawable shaped(String name, Drawable d) {
            int i = Cfg.choice(Features.LAUNCHER_ICON_SHAPE);
            if (i <= 0 || i >= SHAPE_PATHS.length || SHAPE_PATHS[i] == null || !(d instanceof BitmapDrawable)) return d;
            String key = name + "#" + i;
            Bitmap out = shapedCache.get(key);
            if (out == null) {
                Bitmap src = ((BitmapDrawable) d).getBitmap();
                if (src == null) return d;
                android.graphics.Path path;
                try { path = androidx.core.graphics.PathParser.createPathFromPathData(SHAPE_PATHS[i]); } catch (Throwable t) { return d; }
                int w = src.getWidth(), h = src.getHeight();
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.setScale(w / 100f, h / 100f);
                path.transform(m);
                out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                c.drawPath(path, p);
                p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                c.drawBitmap(src, 0, 0, p);
                shapedCache.put(key, out);
            }
            return new BitmapDrawable(res, out);
        }

        @Override Drawable drawable(String name, int density) {
            if (name == null) return null;
            String entry = entries.get(name);
            if (entry == null) entry = entriesLower.get(name.toLowerCase(java.util.Locale.ROOT));
            if (entry == null) return null;
            Bitmap b = bitmaps.get(entry);
            if (b == null) {
                try (InputStream in = zip.getInputStream(zip.getEntry(entry))) {
                    b = android.graphics.BitmapFactory.decodeStream(in);
                } catch (Throwable t) { return null; }
                if (b == null) return null;
                bitmaps.put(entry, b);
            }
            return new BitmapDrawable(res, b);
        }
    }
}
