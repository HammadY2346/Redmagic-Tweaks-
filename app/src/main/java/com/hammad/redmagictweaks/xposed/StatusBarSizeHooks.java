package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.View;
import android.widget.TextView;

import com.hammad.redmagictweaks.StatusBarFeatures;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;

/**
 * com.android.systemui — status bar size options that are not a simple view scale (those live in IconPackHooks /
 * ClockHooks): the bar height, the side margins and the network speed text size.
 *
 * Height: every System UI user of the status bar height (StatusBarWindowControllerImpl for the window height and the
 * insets it provides to apps, PhoneStatusBarView, the keyguard bar, heads-up and notification stack offsets…) asks
 * com.android.internal.policy.SystemBarUtils.getStatusBarHeight / getStatusBarHeightForRotation. Scaling those two in
 * this process keeps all of them consistent; apps get the new height through the window insets.
 * Side margins: PhoneStatusBarView.updateSafeInsets() sets the left / right padding of the bar from the rounded-corner
 * insets each time the layout or configuration changes; the chosen extra is added after it.
 * Network speed: StatusBarHooks.styleNetView (StatusBarNetSpeedMFV init / refreshNetSpeedView) calls {@link #scaleText}.
 */
final class StatusBarSizeHooks {
    private StatusBarSizeHooks() {}

    private static final StatusBarHooks.Choice height = new StatusBarHooks.Choice(StatusBarFeatures.SB_HEIGHT);
    private static final StatusBarHooks.Choice side = new StatusBarHooks.Choice(StatusBarFeatures.SB_SIDE_PAD);
    private static final StatusBarHooks.Choice net = new StatusBarHooks.Choice(StatusBarFeatures.SB_NET_SIZE);

    static void install(ClassLoader cl) {
        try { height(cl); } catch (Throwable t) { Lx.e("StatusBar: height install", t); }
        try { sides(cl); } catch (Throwable t) { Lx.e("StatusBar: side margins install", t); }
        Lx.i("StatusBar: size hooks ready");
    }

    // ------------------------------------------------------------------ height

    private static void height(ClassLoader cl) {
        Class<?> sbu = H.cls(cl, "com.android.internal.policy.SystemBarUtils");
        if (sbu == null) return;
        XC_MethodHook scale = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !(p.getResult() instanceof Integer)) return;
                int pct = height.value(StatusBarFeatures.heightPercent(height.get()));
                if (pct == 100) return;
                int v = (Integer) p.getResult();
                if (v > 0) p.setResult(Math.max(1, Math.round(v * pct / 100f)));
            }
        };
        int n = 0;
        // public static int getStatusBarHeight(Resources, DisplayCutout) — getStatusBarHeight(Context) delegates to it
        if (H.hook(H.find(sbu, "getStatusBarHeight", Resources.class, DisplayCutout.class), scale)) n++;
        // public static int getStatusBarHeightForRotation(Context, int) — the per-rotation insets of the status bar window
        if (H.hook(H.find(sbu, "getStatusBarHeightForRotation", Context.class, int.class), scale)) n++;
        if (n < 2) Lx.w("StatusBar: SystemBarUtils height methods hooked " + n + "/2, height option may be partial");
    }

    // ------------------------------------------------------------------ side margins

    private static void sides(ClassLoader cl) {
        Class<?> bar = H.cls(cl, "com.android.systemui.statusbar.phone.PhoneStatusBarView");
        if (bar == null) return;
        // public final void updateSafeInsets() — PhoneStatusBarView: setPadding(insets.left, insets.top, insets.right, bottom)
        Method m = H.find(bar, "updateSafeInsets");
        if (m == null) { Lx.w("StatusBar: PhoneStatusBarView.updateSafeInsets not found, side margins disabled"); return; }
        H.hook(m, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable()) return;
                int dp = side.value(StatusBarFeatures.sidePadDp(side.get()));
                if (dp == 0) return;
                try {
                    View v = (View) p.thisObject;
                    int d = Math.round(dp * v.getResources().getDisplayMetrics().density);
                    v.setPadding(Math.max(0, v.getPaddingLeft() + d), v.getPaddingTop(), Math.max(0, v.getPaddingRight() + d), v.getPaddingBottom());
                } catch (Throwable t) { Lx.e("StatusBar: side margins", t); }
            }
        });
    }

    // ------------------------------------------------------------------ network speed text

    static int netPercent() { return net.value(StatusBarFeatures.netPercent(net.get())); }

    /** text view -> {base px (what the host / net style set), px we applied} */
    private static final Map<TextView, float[]> netBase = new WeakHashMap<>();

    /** Scales the current size; a size we did not set ourselves becomes the new base, so repeated calls never compound. */
    static void scaleText(TextView t, int pct) {
        if (t == null) return;
        float cur = t.getTextSize();
        float[] st = netBase.get(t);
        if (st == null || Math.abs(cur - st[1]) > 0.5f) { st = new float[]{cur, cur}; netBase.put(t, st); }
        float want = st[0] * pct / 100f;
        if (Math.abs(cur - want) > 0.5f) t.setTextSize(TypedValue.COMPLEX_UNIT_PX, want);
        st[1] = want;
    }
}
