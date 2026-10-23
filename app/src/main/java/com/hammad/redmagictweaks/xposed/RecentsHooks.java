package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Recent apps styles in the stock launcher (com.zte.mifavor.launcher), see {@link RecentsArt}.
 * <ul>
 * <li>List / grid: the launcher's own "Recent apps style" setting (settings_preference_key_recent_style, 1 list,
 *     2 grid) is answered by the style; Stock leaves it alone.</li>
 * <li>Card size: BaseContainerInterface.calculateTaskSize, the one place the overview, the swipe-up animation and the
 *     launch animation all take the card rectangle from, is scaled around its centre (list layout only).</li>
 * <li>Corners: TaskCornerRadius.get (stock 16 dp).</li>
 * <li>Header: after TaskView.setThumbnailOrientation lays out the icon, name and the mini-window / split / menu
 *     buttons above the screenshot, the icon is centred and the rest folded away (tapping the icon opens the same
 *     menu as the ⋮ button), or icon and name are centred together after each layout.</li>
 * <li>Moving styles: TaskView.applyTranslationX (every stock change of a card's position ends there) adds the
 *     style's shift for the card's distance from the middle and stores its scale, TaskView.applyScale multiplies it
 *     in, RecentsView.updateCurveProperties (every scroll frame) re-runs both for all cards. The app that is still live
 *     (its window drawn by the swipe animation, not by the card) is left alone until it becomes a screenshot.</li>
 * <li>Close all: OverviewActionsView's round button becomes a "Close all" pill or is removed.</li>
 * </ul>
 * Nothing is installed while every recents option is stock. Header, button and corner changes need a launcher restart;
 * the moving styles and card size follow the setting within a second and a half.
 */
final class RecentsHooks {
    private RecentsHooks() {}

    private static final String TV = "com.android.quickstep.views.TaskView";
    private static final String RV = "com.android.quickstep.views.RecentsView";
    private static final String OAV = "com.android.quickstep.views.OverviewActionsView";
    private static final String TCR = "com.android.quickstep.util.TaskCornerRadius";
    private static final String BCI = "com.android.quickstep.BaseContainerInterface";
    private static final String PREF_KEY = "settings_preference_key_recent_style";
    private static final String F_SCALE = "rmtRecentsScale", F_PAIR = "rmtRecentsPair", F_PILL = "rmtRecentsPill";

    private static RecentsArt.Look look;
    private static long lookAt;
    private static Class<?> rvClass;
    private static Method applyScale, applyTx, showAsGrid, runningTaskView, orientationHandler;
    private static Field liveTile, pageSpacing, modalness;
    private static final ThreadLocal<int[]> GRID_CALLS = new ThreadLocal<int[]>() { @Override protected int[] initialValue() { return new int[1]; } };
    private static final float[] TMP = new float[4];

    static RecentsArt.Look look() {
        long now = SystemClock.uptimeMillis();
        if (look == null || now - lookAt > 1500) {
            look = RecentsArt.resolve(Cfg.choice(RecentsArt.KEY_STYLE), Cfg.choice(RecentsArt.KEY_HEADER), Cfg.choice(RecentsArt.KEY_CLEAR),
                    Cfg.choice(RecentsArt.KEY_CORNERS), Cfg.choice(RecentsArt.KEY_SIZE),
                    Cfg.hasNum(RecentsArt.KEY_CORNERS) ? Cfg.num(RecentsArt.KEY_CORNERS, -1) : -1, Cfg.hasNum(RecentsArt.KEY_SIZE) ? Cfg.num(RecentsArt.KEY_SIZE, -1) : -1);
            lookAt = now;
        }
        return look;
    }

    static void install(ClassLoader cl) {
        RecentsArt.Look l = look();
        if (!l.any()) { Lx.i("Recents: all stock, nothing hooked"); return; }
        Lx.i("Recents: style " + RecentsArt.STYLES[l.style] + ", header " + l.header + ", close all " + l.clear + ", corners " + l.cornerDp + " dp, size " + l.size);
        hookLauncherSetting(cl);
        hookSize(cl);
        hookCorners(cl);
        hookHeader(cl);
        hookMotion(cl);
        hookClearAll(cl);
    }

    // ---------------------------------------------------------------------------------------- list / grid setting
    private static void hookLauncherSetting(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("android.app.SharedPreferencesImpl", cl, "getInt", String.class, int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!PREF_KEY.equals(p.args[0])) return;
                    int v = RecentsArt.launcherSetting(look().style);
                    if (v > 0) p.setResult(v);
                }
            });
        } catch (Throwable t) { Lx.w("Recents: launcher list/grid setting not hooked: " + t); }
    }

    // ---------------------------------------------------------------------------------------- card size
    private static void hookSize(ClassLoader cl) {
        Class<?> bci = XposedHelpers.findClassIfExists(BCI, cl);
        if (bci == null) { Lx.w("Recents: " + BCI + " missing, card size stays stock"); return; }
        int n = 0;
        for (Method m : bci.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (m.getName().equals("calculateGridTaskSize") && pt.length == 4 && pt[2] == Rect.class) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) { GRID_CALLS.get()[0]++; }
                });
            } else if (m.getName().equals("calculateTaskSize") && pt.length == 4 && pt[2] == Rect.class) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) { p.setObjectExtra("g", GRID_CALLS.get()[0]); }
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Object before = p.getObjectExtra("g");
                        if (before instanceof Integer && GRID_CALLS.get()[0] != (Integer) before) return;   // grid layout
                        float s = look().size;
                        if (s >= 0.999f || s <= 0.3f) return;
                        Rect r = (Rect) p.args[2];
                        if (r == null || r.isEmpty()) return;
                        float cx = r.exactCenterX(), cy = r.exactCenterY(), hw = r.width() * s / 2f, hh = r.height() * s / 2f;
                        r.set(Math.round(cx - hw), Math.round(cy - hh), Math.round(cx + hw), Math.round(cy + hh));
                    }
                });
                n++;
            }
        }
        if (n == 0) Lx.w("Recents: calculateTaskSize not found, card size stays stock");
    }

    // ---------------------------------------------------------------------------------------- corners
    private static void hookCorners(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(TCR, cl, "get", Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    float dp = look().cornerDp;
                    if (dp <= 0f || p.args[0] == null) return;
                    p.setResult(dp * ((Context) p.args[0]).getResources().getDisplayMetrics().density);
                }
            });
        } catch (Throwable t) { Lx.w("Recents: card corners not hooked: " + t); }
    }

    // ---------------------------------------------------------------------------------------- header
    private static void hookHeader(ClassLoader cl) {
        final Class<?> tv = XposedHelpers.findClassIfExists(TV, cl);
        if (tv == null) { Lx.w("Recents: TaskView missing"); return; }
        Method orient = null;
        for (Method m : tv.getDeclaredMethods()) if (m.getName().equals("setThumbnailOrientation") && m.getParameterTypes().length == 1) orient = m;
        if (orient == null) { Lx.w("Recents: TaskView.setThumbnailOrientation missing, header stays stock"); return; }
        XposedBridge.hookMethod(orient, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { header((FrameLayout) p.thisObject); } catch (Throwable t) { Lx.w("Recents: header: " + t); }
            }
        });
        XposedHelpers.findAndHookMethod(tv, "onLayout", boolean.class, int.class, int.class, int.class, int.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(p.thisObject, F_PAIR))) return;
                try { centrePair((FrameLayout) p.thisObject); } catch (Throwable t) { Lx.w("Recents: header layout: " + t); }
            }
        });
    }

    private static boolean plainCard(View tv) {
        String n = tv.getClass().getName();
        if (n.endsWith("GroupedTaskView") || n.endsWith("DesktopTaskView")) return false;
        try { if ((Boolean) XposedHelpers.callMethod(tv, "isGridTask")) return false; } catch (Throwable ignored) { }
        return portrait(tv);
    }

    private static boolean portrait(View tv) {
        try {
            if (orientationHandler == null) orientationHandler = findTv(tv.getClass()).getMethod("getPagedOrientationHandler");
            Object h = orientationHandler.invoke(tv);
            return h != null && h.getClass().getName().contains("Portrait");
        } catch (Throwable t) { return true; }
    }

    private static Class<?> findTv(Class<?> c) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) if (k.getName().equals(TV)) return k;
        return c;
    }

    private static void header(FrameLayout tv) {
        int mode = look().header;
        XposedHelpers.setAdditionalInstanceField(tv, F_PAIR, Boolean.FALSE);
        if (mode == RecentsArt.H_STOCK || !plainCard(tv)) return;
        List<?> containers = (List<?>) XposedHelpers.callMethod(tv, "getTaskContainers");
        if (containers == null || containers.isEmpty()) return;
        Object c0 = containers.get(0);
        View icon = (View) XposedHelpers.callMethod(XposedHelpers.callMethod(c0, "getIconView"), "asView");
        TextView title = (TextView) XposedHelpers.callMethod(c0, "getTitleView");
        // the ⋮ / mini-window / split buttons: no size, so nothing to see or tap (the icon opens the same menu)
        for (String f : new String[]{"mMenuButton", "mMiniWindowButton", "mSplitScreenButton"}) {
            try {
                View b = (View) XposedHelpers.getObjectField(tv, f);
                if (b == null) continue;
                ViewGroup.LayoutParams lp = b.getLayoutParams();
                lp.width = 0; lp.height = 0;
                b.setLayoutParams(lp);
            } catch (Throwable ignored) { }
        }
        if (mode == RecentsArt.H_ICON) {
            if (icon != null && icon.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.getLayoutParams();
                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                lp.leftMargin = 0; lp.rightMargin = 0; lp.setMarginStart(0); lp.setMarginEnd(0);
                icon.setLayoutParams(lp);
                icon.setTranslationX(0f);
            }
            if (title != null) {
                ViewGroup.LayoutParams lp = title.getLayoutParams();
                lp.width = 0;
                title.setLayoutParams(lp);
            }
        } else if (mode == RecentsArt.H_ICON_NAME && title != null && tv.getLayoutDirection() != View.LAYOUT_DIRECTION_RTL) {
            ViewGroup.LayoutParams lp = title.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            title.setLayoutParams(lp);
            title.setMaxWidth(Math.round(tv.getResources().getDisplayMetrics().widthPixels * 0.42f));
            XposedHelpers.setAdditionalInstanceField(tv, F_PAIR, Boolean.TRUE);
        }
    }

    /** Icon and name centred together over the card (after every layout, the name width changes with the app). */
    private static void centrePair(FrameLayout tv) {
        List<?> containers = (List<?>) XposedHelpers.callMethod(tv, "getTaskContainers");
        if (containers == null || containers.isEmpty()) return;
        Object c0 = containers.get(0);
        View icon = (View) XposedHelpers.callMethod(XposedHelpers.callMethod(c0, "getIconView"), "asView");
        TextView title = (TextView) XposedHelpers.callMethod(c0, "getTitleView");
        if (icon == null || title == null || title.getVisibility() == View.GONE || icon.getWidth() == 0) return;
        int gap = Math.max(0, title.getLeft() - icon.getRight());
        float total = icon.getWidth() + gap + title.getWidth();
        float start = (tv.getWidth() - total) / 2f;
        icon.setTranslationX(start - icon.getLeft());
        title.setTranslationX(start + icon.getWidth() + gap - title.getLeft());
    }

    // ---------------------------------------------------------------------------------------- moving styles
    private static void hookMotion(ClassLoader cl) {
        final Class<?> tv = XposedHelpers.findClassIfExists(TV, cl);
        rvClass = XposedHelpers.findClassIfExists(RV, cl);
        if (tv == null || rvClass == null) { Lx.w("Recents: TaskView / RecentsView missing, moving styles off"); return; }
        try {
            applyScale = tv.getDeclaredMethod("applyScale"); applyScale.setAccessible(true);
            applyTx = tv.getDeclaredMethod("applyTranslationX"); applyTx.setAccessible(true);
        } catch (Throwable t) { Lx.w("Recents: TaskView.applyScale / applyTranslationX missing, moving styles off: " + t); return; }
        try { showAsGrid = rvClass.getMethod("showAsGrid"); } catch (Throwable ignored) { }
        try { runningTaskView = rvClass.getMethod("getRunningTaskView"); } catch (Throwable ignored) { }
        liveTile = field(rvClass, "mEnableDrawingLiveTile");
        pageSpacing = field(rvClass, "mPageSpacing");
        modalness = field(rvClass, "mTaskModalness");

        XposedBridge.hookMethod(applyTx, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { place((View) p.thisObject); } catch (Throwable t) { Lx.w("Recents: place: " + t); }
            }
        });
        XposedBridge.hookMethod(applyScale, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object s = XposedHelpers.getAdditionalInstanceField(p.thisObject, F_SCALE);
                if (!(s instanceof Float) || (Float) s == 1f) return;
                View v = (View) p.thisObject;
                v.setScaleX(v.getScaleX() * (Float) s);
                v.setScaleY(v.getScaleY() * (Float) s);
            }
        });
        Method curve = null;
        for (Method m : rvClass.getDeclaredMethods()) if (m.getName().equals("updateCurveProperties") && m.getParameterTypes().length == 0) curve = m;
        if (curve == null) { Lx.w("Recents: RecentsView.updateCurveProperties missing, cards only update when they move"); return; }
        XposedBridge.hookMethod(curve, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                ViewGroup rv = (ViewGroup) p.thisObject;
                for (int i = 0, n = rv.getChildCount(); i < n; i++) {
                    View c = rv.getChildAt(i);
                    if (!tv.isInstance(c)) continue;
                    try { applyTx.invoke(c); } catch (Throwable ignored) { }
                }
            }
        });
    }

    private static Field field(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f; } catch (Throwable ignored) { }
        }
        return null;
    }

    /** Shift, scale, turn and order one card for its distance from the middle of the screen. */
    private static void place(View tv) {
        RecentsArt.Look l = look();
        Host parent = new Host(tv.getParent());
        boolean active = l.moves() && parent.rv != null && tv.getWidth() > 0 && !parent.grid() && !parent.modal() && portrait(tv) && !parent.liveTile(tv);
        if (!active) { reset(tv); return; }
        ViewGroup rv = parent.rv;
        float w = tv.getWidth();
        float spacing = 0f;
        try { if (pageSpacing != null) spacing = pageSpacing.getInt(rv); } catch (Throwable ignored) { }
        if (spacing <= 0f) spacing = w * 0.06f;
        float step = w + spacing;
        float stockTx = tv.getTranslationX();
        float centre = tv.getLeft() + w / 2f + stockTx - rv.getScrollX();
        float ref = (rv.getWidth() + rv.getPaddingLeft() - rv.getPaddingRight()) / 2f;
        float d = (centre - ref) / step;
        RecentsArt.transform(l.style, d, w, step, TMP);
        tv.setTranslationX(stockTx + TMP[1]);
        if (TMP[2] != 0f || tv.getRotationY() != 0f) {
            float dpi = tv.getResources().getDisplayMetrics().densityDpi;
            tv.setCameraDistance(RecentsArt.CAMERA_DP * dpi * dpi / (160f * 72f));   // the same distance as the preview, in px for HWUI
            tv.setRotationY(TMP[2]);
        }
        // order only, never above 0 (a positive Z would cast a shadow)
        float z = TMP[3] >= 0f && l.style != RecentsArt.STACK ? 0f : TMP[3];
        if (l.style == RecentsArt.STACK) z = -(4f - Math.max(-4f, Math.min(3.9f, TMP[3])));
        tv.setTranslationZ(z * tv.getResources().getDisplayMetrics().density * 0.5f);
        setScale(tv, TMP[0]);
    }

    private static void reset(View tv) {
        Object s = XposedHelpers.getAdditionalInstanceField(tv, F_SCALE);
        if (tv.getRotationY() != 0f) tv.setRotationY(0f);
        if (tv.getTranslationZ() != 0f) tv.setTranslationZ(0f);
        if (s instanceof Float && (Float) s != 1f) setScale(tv, 1f);
    }

    private static void setScale(View tv, float s) {
        Object old = XposedHelpers.getAdditionalInstanceField(tv, F_SCALE);
        if (old instanceof Float && Math.abs((Float) old - s) < 0.0005f) return;
        XposedHelpers.setAdditionalInstanceField(tv, F_SCALE, s);
        try { applyScale.invoke(tv); } catch (Throwable ignored) { }
    }

    /** The RecentsView a card sits in and the states in which cards must keep the stock layout. */
    private static final class Host {
        final ViewGroup rv;
        Host(Object p) { rv = rvClass != null && rvClass.isInstance(p) ? (ViewGroup) p : null; }
        boolean grid() { try { return showAsGrid != null && (Boolean) showAsGrid.invoke(rv); } catch (Throwable t) { return false; } }
        boolean modal() { try { return modalness != null && modalness.getFloat(rv) > 0f; } catch (Throwable t) { return false; } }
        /** The app the swipe came from while its live window is still drawn by the animation, not by the card. */
        boolean liveTile(View tv) {
            try { return liveTile != null && liveTile.getBoolean(rv) && runningTaskView != null && runningTaskView.invoke(rv) == tv; }
            catch (Throwable t) { return false; }
        }
    }

    // ---------------------------------------------------------------------------------------- close all
    private static void hookClearAll(ClassLoader cl) {
        Class<?> oav = XposedHelpers.findClassIfExists(OAV, cl);
        if (oav == null) { Lx.w("Recents: OverviewActionsView missing, close-all button stays stock"); return; }
        if (look().clear == RecentsArt.C_STOCK) return;
        XposedHelpers.findAndHookMethod(oav, "onFinishInflate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { clearAll((ViewGroup) p.thisObject); } catch (Throwable t) { Lx.w("Recents: close all: " + t); }
            }
        });
        try {
            XposedHelpers.findAndHookMethod(oav, "onForeColorChanged", int.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { tint(p.thisObject, (Integer) p.args[0]); }
            });
        } catch (Throwable t) { Lx.w("Recents: close-all colour follows white only: " + t); }
    }

    private static void clearAll(ViewGroup self) {
        int mode = look().clear;
        Context ctx = self.getContext();
        Resources res = ctx.getResources();
        int id = res.getIdentifier("remove_all_button_layout", "id", ctx.getPackageName());
        View layout = id == 0 ? null : self.findViewById(id);
        if (!(layout instanceof LinearLayout)) { Lx.w("Recents: close-all button not found"); return; }
        LinearLayout lay = (LinearLayout) layout;
        if (mode == RecentsArt.C_HIDDEN) {
            lay.setVisibility(View.GONE);
            lay.setClickable(false);
            return;
        }
        if (mode != RecentsArt.C_PILL) return;
        Object img = null;
        try { img = XposedHelpers.getObjectField(self, "mClearAllButton"); } catch (Throwable ignored) { }
        if (img instanceof ImageView) ((ImageView) img).setVisibility(View.GONE);
        float dp = res.getDisplayMetrics().density;
        TextView t = new TextView(ctx);
        t.setText("Close all");
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(Math.round(22 * dp), 0, Math.round(22 * dp), 0);
        t.setClickable(false);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        lay.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(40 * dp)));
        ViewGroup.LayoutParams lp = lay.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        lay.setLayoutParams(lp);
        lay.setContentDescription("Close all");
        XposedHelpers.setAdditionalInstanceField(self, F_PILL, t);
        int color = -1;
        try { color = XposedHelpers.getIntField(self, "mColor"); } catch (Throwable ignored) { }
        tint(self, color);
        Lx.i("Recents: \"Close all\" pill");
    }

    private static void tint(Object self, int color) {
        Object o = XposedHelpers.getAdditionalInstanceField(self, F_PILL);
        if (!(o instanceof TextView)) return;
        TextView t = (TextView) o;
        if (color == 0) color = -1;
        boolean light = color == -1 || (android.graphics.Color.red(color) + android.graphics.Color.green(color) + android.graphics.Color.blue(color)) > 384;
        t.setTextColor(color);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20 * t.getResources().getDisplayMetrics().density);
        bg.setColor(light ? 0x33FFFFFF : 0x1F000000);
        t.setBackground(bg);
    }
}
