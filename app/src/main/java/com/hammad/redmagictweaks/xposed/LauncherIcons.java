package com.hammad.redmagictweaks.xposed;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
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
 * Stock launcher (com.zte.mifavor.launcher) app icons: icon pack, icon shape, label size, hidden labels.
 *
 * Verified in the decompiled launcher (China 11.0.11 and EU 11.0.4, same code):
 *  - Every app icon of home, drawer and folders comes from the launcher's IconProvider (s1.T#l(LauncherActivityInfo,
 *    PackageItemInfo, ApplicationInfo, int), R8 name differs per build), which asks
 *    {@link LauncherActivityInfo#getIcon(int)} first. Hooking that framework method (only in the launcher's process)
 *    themes every launcher icon on both builds without depending on obfuscated names. The launcher's clock/calendar
 *    live icons are made before that call and stay stock. ZTE's own ResourceContext.l(ComponentName,String) is only
 *    the "_fake" icon lookup for ZTE's placeholder apps, not the general icon path.
 *  - Icons are cached as bitmaps in databases/app_icons.db: when the pack or the shape changed since the last start,
 *    the launcher's own process deletes that file before the launcher opens it ({@link #clearCacheIfChanged}).
 *  - Shape: Launcher3's ThemeManager (n1.C) reads the system string android:string/config_icon_mask; we answer that
 *    lookup with our path in the launcher's process.
 *  - Label size: InvariantDeviceProfile reads the integers config_icon_text_size(_single_line/_two_line/_ease…) of the
 *    launcher's own resources; we scale those.
 *  - Hidden labels: the launcher keeps "icon_no_title" in its prefs (default config_icon_no_title); we answer true.
 */
final class LauncherIcons {
    private LauncherIcons() {}

    static final String PKG = "com.zte.mifavor.launcher";

    /** Path data (viewport 100×100) for Features.LAUNCHER_ICON_SHAPES; index 0 = stock (null). */
    static final String[] SHAPE_PATHS = {
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

    static void install(ClassLoader cl) {
        if (installed) return;
        installed = true;
        int n = 0;

        // 1. icon pack: LauncherActivityInfo#getIcon(int) in this process
        try {
            XposedHelpers.findAndHookMethod(LauncherActivityInfo.class, "getIcon", int.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Pack pack = Pack.current();
                        if (pack == null) return;
                        LauncherActivityInfo info = (LauncherActivityInfo) p.thisObject;
                        Drawable d = pack.icon(info.getComponentName(), (Integer) p.args[0], (Drawable) p.getResult());
                        if (d != null) p.setResult(d);
                    } catch (Throwable t) { logOnce("pack", "Icons: icon pack lookup failed: " + t); }
                }
            });
            n++;
        } catch (Throwable t) { Lx.e("Icons: LauncherActivityInfo#getIcon hook", t); }

        // 2. shape + label size: Resources lookups of this process
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
            n++;
        } catch (Throwable t) { Lx.e("Icons: Resources#getString hook", t); }
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

        // 3. hidden labels: the launcher's "icon_no_title" pref
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

        // 4. clear the icon cache once after the pack or the shape changed (in the launcher's own process: no root needed)
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { clearCacheIfChanged((Context) p.args[0]); } catch (Throwable t) { Lx.e("Icons: cache check", t); }
                }
            });
            n++;
        } catch (Throwable t) { Lx.e("Icons: Application#attach hook", t); }

        Lx.i("Icons: hooks ready (" + n + "/5) pack=" + Cfg.str(Features.LAUNCHER_ICON_PACK) + " shape=" + Cfg.choice(Features.LAUNCHER_ICON_SHAPE)
                + " labelSize=" + Cfg.choice(Features.LAUNCHER_LABEL_SIZE) + " hideLabels=" + Cfg.on(Features.LAUNCHER_HIDE_LABELS));
    }

    // ------------------------------------------------------------------ helpers

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

    /** Delete the cached icon bitmaps when the icon pack or the shape is not what they were drawn with. */
    static void clearCacheIfChanged(Context c) {
        if (c == null || !PKG.equals(c.getPackageName())) return;
        String pack = Cfg.str(Features.LAUNCHER_ICON_PACK);
        String sig = (pack == null ? "" : pack) + "|" + Cfg.choice(Features.LAUNCHER_ICON_SHAPE) + "|" + versionOf(c, pack);
        Context de = c.createDeviceProtectedStorageContext();
        SharedPreferences sp = de.getSharedPreferences("rmtweaks_icons", Context.MODE_PRIVATE);
        String old = sp.getString("sig", null);
        if (sig.equals(old)) return;
        if (old == null && pack == null && Cfg.choice(Features.LAUNCHER_ICON_SHAPE) == 0) { sp.edit().putString("sig", sig).apply(); return; }   // first run, nothing themed
        int deleted = 0;
        for (Context k : new Context[]{c, de}) {
            for (String f : new String[]{"app_icons.db", "app_icons.db-journal", "app_icons.db-wal", "app_icons.db-shm"}) {
                try { File file = k.getDatabasePath(f); if (file.exists() && file.delete()) deleted++; } catch (Throwable ignored) { }
            }
        }
        sp.edit().putString("sig", sig).commit();
        Lx.i("Icons: icon pack/shape changed (" + old + " -> " + sig + "), icon cache cleared (" + deleted + " files)");
    }

    private static String versionOf(Context c, String pkg) {
        if (pkg == null) return "";
        try { return String.valueOf(c.getPackageManager().getPackageInfo(pkg, 0).getLongVersionCode()); } catch (Throwable t) { return "?"; }
    }

    // ------------------------------------------------------------------ icon pack (ADW / Nova appfilter format)

    static final class Pack {
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
            cached = load(app, want);
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

        private Drawable drawable(String name, int density) {
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
        private Drawable compose(Drawable stock, int density) {
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
}
