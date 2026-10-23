package com.hammad.redmagictweaks.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import com.hammad.redmagictweaks.Features;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Game view (system_server): how a game fills the screen.
 *
 * Why games get bars: RedMagic's Game Space ratio (Game Assist writes Settings.Global nubia_gameratio_enable_pkgs /
 * nubia_gameratio_size_pkgs, e.g. "com.tencent.ig:1.78") makes GameRatioStub.applyAspectRatio hand that ratio to
 * AppCompatAspectRatioPolicy, which letterboxes the activity: a 16:9 window in the middle of the 20:9 panel, black at the
 * sides. Plain AOSP letterboxing, nothing is scaled.
 *
 *  - Full screen: for the chosen games the Game Space ratio is ignored and the manifest's max aspect ratio dropped, so the
 *    game window covers the whole panel at its own shape.
 *  - Stretched views (4:3 "iPad view", 3:2, 16:10, 16:9, 2:1): while the game is the resumed full-screen activity the
 *    default display gets a forced size of that shape (short side kept, long side shortened, density unchanged), exactly
 *    what "wm size" does, so the game renders that shape. LogicalDisplay normally fits a differently shaped logical display
 *    into the panel with bars (uniform scale); here its projection is stretched to the whole panel instead. The input
 *    viewport is taken from the same projection (DisplayDevice.populateViewportLocked), so touches land where they are
 *    drawn. The size is never written to settings (DisplayWindowSettings.setForcedSize is skipped for our calls), so a
 *    reboot always comes back at the normal resolution; a marker file covers the rest (cleared at boot).
 *    Back to normal when another app comes to the front (after a short pause, so a dialog over the game doesn't flicker),
 *    when the screen turns off, when the game is no longer the resumed activity (watchdog), or when the setting changes.
 */
final class GameViewHooks {
    private GameViewHooks() {}

    private static final String WM = "com.android.server.wm.";
    private static final File MARKER = new File("/data/system/rmtweaks-gameview");
    private static final ThreadLocal<Boolean> APPLYING = new ThreadLocal<>();

    private static ClassLoader cl;
    private static Handler handler;
    private static Object atms;
    private static volatile boolean stretch;       // the default display is ours: stretch its projection to the panel
    private static volatile String activePkg;      // the game whose stretched view is applied
    private static int activeW, activeH;           // the size we set
    private static volatile int physW, physH;      // the panel's own size (natural orientation), for the trigger points
    private static int prevW, prevH;                // the forced size before (0 = none)
    private static boolean receiverOn;
    private static String lastLog = "";

    static void install(ClassLoader loader) {
        cl = loader;
        HandlerThread ht = new HandlerThread("rmtweaks-gameview");
        ht.start();
        handler = new Handler(ht.getLooper());
        int n = 0;

        // 1. no max aspect ratio for games with a view other than stock (the manifest's limit would letterbox them)
        Class<?> policy = XposedHelpers.findClassIfExists(WM + "AppCompatAspectRatioPolicy", cl);
        if (H.hook(H.find(policy, "getMaxAspectRatio"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object r = p.getResult();
                    if (!(r instanceof Float) || (Float) r == 0f || !anyView()) return;
                    String pkg = pkgOf(XposedHelpers.getObjectField(p.thisObject, "mActivityRecord"));
                    if (mode(pkg) <= 0) return;
                    p.setResult(0f);
                    once("full:" + pkg, "Games: view: " + pkg + " has no max aspect ratio (was " + r + ")");
                } catch (Throwable t) { Lx.e("Games: view max aspect", t); }
            }
        })) n++;

        // 2. Game Space's own ratio (GameRatioStub) is ignored for those games: no letterbox, no repositioning
        Class<?> stub = XposedHelpers.findClassIfExists(WM + "GameRatioStub", cl);
        if (H.hook(H.find(stub, "applyAspectRatio", float.class, float.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (!anyView()) return;
                    String pkg = pkgOf(XposedHelpers.getObjectField(p.thisObject, "mActivityRecord"));
                    // "stretch only the game": the game gets a window of the view's shape (Game Space's own letterbox,
                    // centred); scaleGame then widens only that window's picture to the panel
                    float wr = windowRatio(pkg);
                    if (wr > 0f) {
                        p.setResult(wr);
                        XposedHelpers.setBooleanField(p.thisObject, "mIsGameRatioEnable", true);
                        once("wratio:" + pkg + wr, "Games: view: " + pkg + " gets a " + wr + " window (only the game is stretched)");
                        return;
                    }
                    if (!XposedHelpers.getBooleanField(p.thisObject, "mIsGameRatioEnable")) return;
                    if (mode(pkg) <= 0) return;
                    p.setResult(p.args[1]);
                    XposedHelpers.setBooleanField(p.thisObject, "mIsGameRatioEnable", false);
                    once("ratio:" + pkg, "Games: view: Game Space ratio for " + pkg + " ignored (" + p.getResult() + ")");
                } catch (Throwable t) { Lx.e("Games: view Game Space ratio", t); }
            }
        })) n++;

        // 3. stretch the default display's projection to the whole panel while a stretched view is on
        Class<?> ld = XposedHelpers.findClassIfExists("com.android.server.display.LogicalDisplay", cl);
        if (H.hookAll(ld, "configureDisplayLocked", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!stretch || p.args.length < 2) return;
                try { stretchProjection(p.thisObject, p.args[0], p.args[1]); } catch (Throwable t) { Lx.e("Games: view projection", t); }
            }
        }) > 0) n++;

        // 4. our size is never saved (display_size_forced / display settings): a restart always comes back normal
        Class<?> dws = XposedHelpers.findClassIfExists(WM + "DisplayWindowSettings", cl);
        if (H.hookAll(dws, "setForcedSize", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (Boolean.TRUE.equals(APPLYING.get())) p.setResult(null);
            }
        }) > 0) n++;

        // 5. the resumed activity: apply for a game, go back for anything else (debounced)
        Class<?> atmsCls = XposedHelpers.findClassIfExists(WM + "ActivityTaskManagerService", cl);
        if (H.hookAll(atmsCls, "setLastResumedActivityUncheckLocked", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    atms = p.thisObject;
                    Object ar = p.args.length > 0 ? p.args[0] : null;
                    if (ar == null) return;
                    final String pkg = pkgOf(ar);
                    if (activePkg == null && !anyView()) return;
                    final boolean fs = fullscreen(ar);
                    handler.post(() -> onTop(pkg, fs ? displayRatio(pkg) : 0f));      // settings are read off the WM lock
                } catch (Throwable t) { Lx.e("Games: view resume", t); }
            }
        }) > 0) n++;

        // 6. at launch: apply before the game's first frame (it starts at the right shape instead of resizing)
        Class<?> sup = XposedHelpers.findClassIfExists(WM + "ActivityTaskSupervisor", cl);
        if (H.hookAll(sup, "acquireAppLaunchPerfLock", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (p.args.length != 1 || p.args[0] == null || !anyView()) return;
                    final String pkg = pkgOf(p.args[0]);
                    handler.post(() -> { float r = displayRatio(pkg); if (r > 0f) apply(pkg, r, "launch"); });
                } catch (Throwable t) { Lx.e("Games: view launch", t); }
            }
        }) > 0) n++;

        // 8. shoulder triggers (touch game keys): Game Space hands their points in the current, stretched size; the native
        //    side takes them as panel pixels and the panel-to-screen mapping then shrinks them a second time (the touch landed
        //    at about 0.72x of the trigger's place with 16:10). While stretched the points are scaled to panel pixels.
        Class<?> tgk = XposedHelpers.findClassIfExists("com.android.server.redmagic.game.touchgamekey.InputManagerServiceExtTgk", cl);
        if (H.hook(H.find(tgk, "transtlateToVerticalPoint", int[].class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (!stretch || activeW <= 0 || activeH <= 0 || physW <= 0 || physH <= 0) return;
                    if (!(p.getResult() instanceof int[])) return;
                    int[] pt = (int[]) p.getResult();
                    if (pt.length < 2) return;
                    int x0 = pt[0], y0 = pt[1];
                    pt[0] = Math.round(x0 * (float) physW / activeW);
                    pt[1] = Math.round(y0 * (float) physH / activeH);
                    once("tgk:" + x0 + "," + y0 + ":" + activeW + "x" + activeH, "Games: view: trigger point " + x0 + "," + y0 + " -> " + pt[0] + "," + pt[1]
                            + " (screen " + activeW + "x" + activeH + ", panel " + physW + "x" + physH + ")");
                } catch (Throwable t) { Lx.e("Games: view trigger point", t); }
            }
        })) n++;
        else Lx.w("Games: view: trigger point hook not found, shoulder triggers may miss in stretched views");

        // 9. "stretch only the game": the game's letterboxed window is scaled to the panel on its own surface
        Class<?> arCls = XposedHelpers.findClassIfExists(WM + "ActivityRecord", cl);
        boolean scaleHook = H.hook(H.find(arCls, "prepareSurfaces"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { scaleGame(p.thisObject); }
        });
        if (!scaleHook) Lx.w("Games: view: ActivityRecord.prepareSurfaces not found, \"stretch only the game\" can't scale");

        // 7. a size left from a crash or a restart mid-game is cleared once the system is up
        handler.post(GameViewHooks::bootCleanup);
        Lx.i("Games: view hooks " + n + "/8" + (n < 8 ? "" : " (shoulder-trigger fix on)") + (scaleHook ? ", game-only stretch ready" : ""));
    }

    // ------------------------------------------------------------------ settings

    private static volatile long readAt;
    private static volatile int allView;
    private static volatile String perGame;
    private static volatile boolean onlyGame, gsStretch;
    private static volatile int strengthIdx, strengthPct = -1;
    private static final java.util.Map<String, Float> RATIOS = new java.util.concurrent.ConcurrentHashMap<>();

    /** The settings, read at most every 1.5 s (getMaxAspectRatio and prepareSurfaces run often, under the WM lock). */
    private static void read() {
        long now = SystemClock.uptimeMillis();
        if (now - readAt < 1500 && readAt != 0) return;
        allView = Cfg.choice(Features.GAME_VIEW);
        perGame = Cfg.str(Features.GAME_VIEW_PKGS);
        onlyGame = Cfg.on(Features.GAME_VIEW_ONLY);
        gsStretch = Cfg.on(Features.GAME_VIEW_GS);
        strengthIdx = Cfg.choice(Features.GAME_VIEW_STRENGTH);
        strengthPct = Cfg.hasNum(Features.GAME_VIEW_STRENGTH) ? Math.max(0, Math.min(100, Cfg.num(Features.GAME_VIEW_STRENGTH, 100))) : -1;
        RATIOS.clear();
        readAt = now;
    }

    /** Stretched shape for the display-wide way (0 with "stretch only the game"). */
    private static float displayRatio(String pkg) {
        read();
        return onlyGame ? 0f : ratio(pkg);
    }

    /** Stretched shape for the game-window way (0 unless "stretch only the game" is on); cached per package. */
    private static float windowRatio(String pkg) {
        if (pkg == null) return 0f;
        read();
        if (!onlyGame) return 0f;
        Float r = RATIOS.get(pkg);
        if (r == null) { r = ratio(pkg); RATIOS.put(pkg, r); }
        return r;
    }

    /** Any game has a view other than stock (cheap check before the per-package lookups). */
    private static boolean anyView() {
        read();
        return allView > 0 || (perGame != null && !perGame.isEmpty());
    }

    /** Features.GAME_VIEWS index for a package: its own choice, else the Game view of Game Space games, else stock. */
    static int mode(String pkg) {
        if (pkg == null) return 0;
        read();
        String s = perGame;
        if (s != null) {
            for (String e : s.split(";")) {
                int eq = e.indexOf('=');
                if (eq > 0 && e.substring(0, eq).trim().equals(pkg)) {
                    try { int v = Integer.parseInt(e.substring(eq + 1).trim()); return v >= 0 && v < Features.GAME_VIEWS.length ? v : 0; }
                    catch (NumberFormatException ignored) { return 0; }
                }
            }
        }
        int all = allView;
        if (all <= 0 || all >= Features.GAME_VIEWS.length) return 0;
        return FrameworkHooks.isGameSpaceGame(cl, pkg) ? all : 0;
    }

    /** Width / height of the stretched shape for a package, 0 = not stretched. */
    private static float ratio(String pkg) {
        int m = mode(pkg);
        return m > 0 && m < Features.GAME_VIEW_RATIO.length ? Features.GAME_VIEW_RATIO[m] : 0f;
    }

    private static String pkgOf(Object ar) {
        try { return ar == null ? null : (String) XposedHelpers.getObjectField(ar, "packageName"); } catch (Throwable t) { return null; }
    }

    /** Full-screen windowing mode (not split screen / freeform / pinned). */
    private static boolean fullscreen(Object ar) {
        try { return (Integer) XposedHelpers.callMethod(ar, "getWindowingMode") == 1; } catch (Throwable t) { return true; }
    }

    // ------------------------------------------------------------------ apply / restore (handler thread)

    private static final Runnable LEAVE = () -> {
        String top = topPkg();
        if (activePkg != null && activePkg.equals(top) && displayRatio(top) > 0f) return;    // back in the game already
        restore("left the game for " + top);
    };

    private static final Runnable WATCHDOG = new Runnable() {
        @Override public void run() {
            if (activePkg == null) return;
            String top = topPkg();
            if (!activePkg.equals(top)) restore("watchdog: " + top + " is in front");
            else if (displayRatio(top) <= 0f) restore("watchdog: the view was turned off");
            else if (!sizeIsOurs()) { Lx.w("Games: view: the display size was changed by someone else, letting go"); release(); }
            else handler.postDelayed(this, 3000);
        }
    };

    private static void onTop(String pkg, float r) {
        handler.removeCallbacks(LEAVE);
        if (r > 0f) { apply(pkg, r, "resumed"); return; }
        if (activePkg != null) handler.postDelayed(LEAVE, 600);
    }

    private static Object wms() throws Exception {
        return Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "window");
    }

    private static Object displayContent(Object wms) {
        return XposedHelpers.callMethod(XposedHelpers.getObjectField(wms, "mRoot"), "getDisplayContent", 0);
    }

    private static boolean sizeIsOurs() {
        try {
            Object dc = displayContent(wms());
            return XposedHelpers.getIntField(dc, "mBaseDisplayWidth") == activeW && XposedHelpers.getIntField(dc, "mBaseDisplayHeight") == activeH;
        } catch (Throwable t) { return true; }
    }

    private static void apply(String pkg, float r, String why) {
        try {
            registerReceiver();
            Object wms = wms();
            Object dc = displayContent(wms);
            if (dc == null) return;
            int iw = XposedHelpers.getIntField(dc, "mInitialDisplayWidth"), ih = XposedHelpers.getIntField(dc, "mInitialDisplayHeight");
            physW = iw; physH = ih;
            if (activePkg == null) {
                boolean forced = XposedHelpers.getBooleanField(dc, "mIsSizeForced");
                prevW = forced ? XposedHelpers.getIntField(dc, "mBaseDisplayWidth") : 0;
                prevH = forced ? XposedHelpers.getIntField(dc, "mBaseDisplayHeight") : 0;
            }
            int bw = prevW > 0 ? prevW : iw, bh = prevH > 0 ? prevH : ih;
            int shortSide = Math.min(bw, bh), longSide = Math.max(bw, bh);
            int want = Math.round(shortSide * r) & ~1;
            if (want >= longSide - 2) {
                once("wide:" + pkg + r, "Games: view: " + pkg + " shape " + r + " is not narrower than the panel, nothing to stretch");
                return;
            }
            int w = bw <= bh ? shortSide : want, h = bw <= bh ? want : shortSide;
            if (activePkg != null && w == activeW && h == activeH && sizeIsOurs()) {
                activePkg = pkg;
                return;
            }
            stretch = true;
            activePkg = pkg;
            activeW = w; activeH = h;
            writeMarker(w, h);
            APPLYING.set(true);
            try { XposedHelpers.callMethod(wms, "setForcedDisplaySize", 0, w, h); } finally { APPLYING.remove(); }
            Lx.i("Games: view: " + pkg + " " + w + "x" + h + " stretched to the panel (" + why + ", was " + (prevW > 0 ? prevW + "x" + prevH : "not forced") + ")");
            handler.removeCallbacks(WATCHDOG);
            handler.postDelayed(WATCHDOG, 3000);
        } catch (Throwable t) {
            Lx.e("Games: view apply", t);
            restore("apply failed");
        }
    }

    private static void restore(String why) { restore(why, false); }

    /** {@code persist}: also save the restored size (boot cleanup: in case our size ever got saved). */
    private static void restore(String why, boolean persist) {
        handler.removeCallbacks(WATCHDOG);
        handler.removeCallbacks(LEAVE);
        if (activePkg == null && !MARKER.exists()) { stretch = false; return; }
        String was = activePkg;
        try {
            Object wms = wms();
            if (!persist) APPLYING.set(true);
            try {
                if (prevW > 0 && prevH > 0) XposedHelpers.callMethod(wms, "setForcedDisplaySize", 0, prevW, prevH);
                else XposedHelpers.callMethod(wms, "clearForcedDisplaySize", 0);
            } finally { APPLYING.remove(); }
            Lx.i("Games: view: back to " + (prevW > 0 ? prevW + "x" + prevH : "the normal size") + " (" + why + (was != null ? ", " + was : "") + ")");
        } catch (Throwable t) {
            Lx.e("Games: view restore", t);
        }
        release();
    }

    /** Forget our state (after restoring, or when someone else changed the size). */
    private static void release() {
        handler.removeCallbacks(WATCHDOG);
        activePkg = null;
        activeW = activeH = 0;
        stretch = false;
        //noinspection ResultOfMethodCallIgnored
        MARKER.delete();
    }

    private static String topPkg() {
        try { return atms == null ? null : pkgOf(XposedHelpers.getObjectField(atms, "mLastResumedActivity")); } catch (Throwable t) { return null; }
    }

    // ------------------------------------------------------------------ "stretch only the game"

    private static volatile String lastScaleLog = "";
    private static final java.util.Map<Object, Boolean> SCALED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * After ActivityRecord.prepareSurfaces: a game letterboxed at its view's shape (hook 2) has its own surface scaled so
     * its picture covers its parent (the task, i.e. the panel) and moved so the picture starts at the parent's corner.
     * SurfaceFlinger computes each window's input transform from its layers, so touches follow the picture. The letterbox
     * bars are children of the same surface and end up outside the screen. Everything else on the display is untouched.
     */
    /** Stretch strength (GAME_VIEW_STRENGTH): the scale actually used for a full scale {@code full} along the stretched side. */
    private static float soften(float full, float panelShape) {
        if (full <= 1.001f) return full;
        if (strengthPct >= 0) return 1f + (full - 1f) * strengthPct / 100f;
        switch (strengthIdx) {
            case 1: return Math.max(1f, Math.min(full, panelShape / 1.6f));
            case 2: return Math.max(1f, Math.min(full, panelShape / (16f / 9f)));
            case 3: return 1f + (full - 1f) * 0.5f;
            case 4: return 1f;
            default: return full;
        }
    }

    /** Game Space letterboxes this activity at a ratio picked in Game Space (GameRatioStub.mIsGameRatioEnable). */
    private static boolean gameSpaceRatio(Object ar) {
        try {
            Object stub = XposedHelpers.getObjectField(ar, "mGameRatioStub");
            return stub != null && XposedHelpers.getBooleanField(stub, "mIsGameRatioEnable");
        } catch (Throwable t) { return false; }
    }

    private static void scaleGame(Object ar) {
        try {
            read();
            if (!onlyGame && !gsStretch && SCALED.isEmpty()) return;
            boolean want = false;
            float sx = 1f, sy = 1f;
            int px = 0, py = 0;
            Point last = (Point) XposedHelpers.getObjectField(ar, "mLastSurfacePosition");
            String pkg = pkgOf(ar);
            boolean ours = onlyGame && windowRatio(pkg) > 0f;
            if ((ours || (gsStretch && gameSpaceRatio(ar))) && fullscreen(ar)) {
                Rect b = (Rect) XposedHelpers.callMethod(ar, "getBounds");
                Object parent = XposedHelpers.callMethod(ar, "getParent");
                Rect pb = parent == null ? null : (Rect) XposedHelpers.callMethod(parent, "getBounds");
                if (b != null && pb != null && b.width() > 0 && b.height() > 0 && pb.contains(b)
                        && (b.width() < pb.width() - 2 || b.height() < pb.height() - 2)) {
                    float panelShape = Math.max(pb.width(), pb.height()) / (float) Math.min(pb.width(), pb.height());
                    sx = soften(pb.width() / (float) b.width(), panelShape);
                    sy = soften(pb.height() / (float) b.height(), panelShape);
                    // the widened picture is centred in its parent; anything it doesn't cover shows the letterbox bars
                    int l = b.left - pb.left, t = b.top - pb.top;
                    float offX = (pb.width() - b.width() * sx) / 2f, offY = (pb.height() - b.height() * sy) / 2f;
                    px = Math.round(offX - sx * (l - last.x));
                    py = Math.round(offY - sy * (t - last.y));
                    want = true;
                }
            }
            android.view.SurfaceControl sc = (android.view.SurfaceControl) XposedHelpers.getObjectField(ar, "mSurfaceControl");
            if (sc == null || !sc.isValid()) return;
            android.view.SurfaceControl.Transaction tx = (android.view.SurfaceControl.Transaction) XposedHelpers.callMethod(ar, "getSyncTransaction");
            if (want) {
                tx.setScale(sc, sx, sy);
                tx.setPosition(sc, px, py);
                String msg = "Games: view: only " + pkg + " stretched: " + String.format(java.util.Locale.ROOT, "%.3f x %.3f", sx, sy) + " at " + px + "," + py;
                if (SCALED.put(ar, Boolean.TRUE) == null || !msg.equals(lastScaleLog)) { lastScaleLog = msg; Lx.i(msg); }
            } else if (SCALED.remove(ar) != null) {
                tx.setScale(sc, 1f, 1f);
                tx.setPosition(sc, last.x, last.y);
                Lx.i("Games: view: " + pkg + " back to its own size");
            }
        } catch (Throwable t) {
            if (!"scale".equals(lastLog)) { lastLog = "scale"; Lx.e("Games: view game-only stretch", t); }
        }
    }

    // ------------------------------------------------------------------ projection

    /**
     * After LogicalDisplay.configureDisplayLocked: when the default display's logical shape differs from the panel's, set
     * the projection's display rect to the whole panel (instead of the barred, uniformly scaled rect).
     */
    private static void stretchProjection(Object logical, Object transaction, Object device) {
        if (XposedHelpers.getIntField(logical, "mDisplayId") != 0 || device != XposedHelpers.getObjectField(logical, "mPrimaryDisplayDevice")) return;
        Rect layer = (Rect) XposedHelpers.getObjectField(device, "mCurrentLayerStackRect");
        Rect shown = (Rect) XposedHelpers.getObjectField(device, "mCurrentDisplayRect");
        if (layer == null || shown == null || layer.isEmpty()) return;
        int orient = XposedHelpers.getIntField(device, "mCurrentOrientation");
        Object info = XposedHelpers.callMethod(device, "getDisplayDeviceInfoLocked");
        int dw = XposedHelpers.getIntField(info, "width"), dh = XposedHelpers.getIntField(info, "height");
        boolean rotated = orient == 1 || orient == 3;
        int pw = rotated ? dh : dw, ph = rotated ? dw : dh;
        // same shape as the panel (within 1 %): nothing to stretch
        float logicalShape = layer.width() / (float) layer.height(), panelShape = pw / (float) ph;
        if (Math.abs(logicalShape / panelShape - 1f) < 0.01f) return;
        Rect masking = null;
        try { masking = (Rect) XposedHelpers.callMethod(logical, "getMaskingInsets", info); } catch (Throwable ignored) { }
        if (masking != null && !masking.isEmpty()) { once("mask", "Games: view: display has masking insets " + masking + ", not stretched"); return; }
        Rect full = new Rect(0, 0, pw, ph);
        if (full.equals(shown)) return;
        XposedHelpers.callMethod(device, "setProjectionLocked", transaction, orient, new Rect(layer), full);
        try { ((Point) XposedHelpers.getObjectField(logical, "mDisplayPosition")).set(0, 0); } catch (Throwable ignored) { }
        once("proj:" + layer.width() + "x" + layer.height() + ":" + orient, "Games: view: " + layer.width() + "x" + layer.height() + " stretched to " + pw + "x" + ph + " (rotation " + orient + ")");
    }

    // ------------------------------------------------------------------ safety

    private static void registerReceiver() {
        if (receiverOn) return;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = XposedHelpers.callStaticMethod(at, "currentActivityThread");
            Context sys = thread == null ? null : (Context) XposedHelpers.callMethod(thread, "getSystemContext");
            if (sys == null) return;
            sys.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) handler.post(() -> { if (activePkg != null) restore("screen off"); });
                }
            }, new IntentFilter(Intent.ACTION_SCREEN_OFF), null, handler);
            receiverOn = true;
        } catch (Throwable t) { Lx.e("Games: view screen-off receiver", t); }
    }

    private static void writeMarker(int w, int h) {
        try (FileOutputStream o = new FileOutputStream(MARKER)) {
            o.write((w + " " + h + " " + prevW + " " + prevH + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) { Lx.w("Games: view: marker not written: " + t); }
    }

    /** Boot (or system_server restart): a marker means a stretched size may still be in place: put the old one back. */
    private static void bootCleanup() {
        final long until = SystemClock.uptimeMillis() + 300000;
        Runnable check = new Runnable() {
            @Override public void run() {
                try {
                    if (!MARKER.exists()) return;
                    Object wms = wms();
                    Object dc = wms == null ? null : displayContent(wms);
                    if (dc == null) {
                        if (SystemClock.uptimeMillis() < until) handler.postDelayed(this, 2000);
                        return;
                    }
                    String[] v = new String(Files.readAllBytes(MARKER.toPath()), StandardCharsets.UTF_8).trim().split("\\s+");
                    int w = Integer.parseInt(v[0]), h = Integer.parseInt(v[1]);
                    prevW = Integer.parseInt(v[2]); prevH = Integer.parseInt(v[3]);
                    int bw = XposedHelpers.getIntField(dc, "mBaseDisplayWidth"), bh = XposedHelpers.getIntField(dc, "mBaseDisplayHeight");
                    if (bw == w && bh == h) { activePkg = "(left over)"; restore("left over from before a restart", true); }
                    else { Lx.i("Games: view: marker found, size is already " + bw + "x" + bh); release(); }
                } catch (Throwable t) {
                    Lx.e("Games: view boot cleanup", t);
                    if (SystemClock.uptimeMillis() < until) handler.postDelayed(this, 2000);
                }
            }
        };
        handler.postDelayed(check, 5000);
    }

    private static void once(String key, String msg) {
        if (key.equals(lastLog)) return;
        lastLog = key;
        Lx.i(msg);
    }
}
