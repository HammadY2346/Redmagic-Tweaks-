package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui — the fingerprint symbol System UI itself draws on the PIN/pattern (bouncer) screen
 * ({@code FingerprintUnderLcdFeature.mFingerprintIcon}, hook targets from nubia-ly's FingerprintBackend). On this
 * firmware the lock-screen idle icon and the press animation are rendered by com.fingerprint.sensorservice
 * (see SensorFingerprintHooks); this keeps the bouncer symbol consistent with the chosen custom icon and
 * implements "Hide fingerprint icon" there.
 */
final class FingerprintUiHooks {
    private FingerprintUiHooks() {}

    private static final String FEATURE = "com.zte.feature.fingerprintlcd.FingerprintUnderLcdFeature";

    static void install(ClassLoader cl) {
        Class<?> f = H.cls(cl, FEATURE);
        if (f == null) return;
        // public final void refreshFillLightLayoutFingerColor(boolean)  — FingerprintUnderLcdFeature.java:2400 ; public void setColor(int) (:2681) ;
        // public void tryInflateFingerprintIcon(ViewGroup) (:3187) ; field "public ImageView mFingerprintIcon" (:121), "public boolean mIsDarkTheme" (:151)
        XC_MethodHook after = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!FpArt.iconActive()) return;
                try {
                    Object iv = XposedHelpers.getObjectField(p.thisObject, "mFingerprintIcon");
                    if (!(iv instanceof ImageView)) return;
                    if (FpArt.iconHidden()) { ((ImageView) iv).setVisibility(View.GONE); return; }
                    boolean dark = Boolean.TRUE.equals(XposedHelpers.getObjectField(p.thisObject, "mIsDarkTheme"));
                    if (p.method.getName().equals("refreshFillLightLayoutFingerColor") && p.args.length > 0 && Boolean.TRUE.equals(p.args[0])) dark = true;
                    apply((ImageView) iv, dark ? 0xa6000000 : 0xffffffff);
                } catch (Throwable t) { Lx.e("fingerprint-ui: icon", t); }
            }
        };
        int n = 0;
        n += H.hookAll(f, "refreshFillLightLayoutFingerColor", after);
        n += H.hookAll(f, "setColor", after);
        n += H.hookAll(f, "tryInflateFingerprintIcon", after);
        // public void setFingerprintIconVisibility(int)  — :2840 (every show path ends here); public void handleShowFingerprintIcon() (:1520)
        n += H.hookAll(f, "setFingerprintIconVisibility", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (FpArt.iconHidden() && p.args.length > 0 && Integer.valueOf(View.VISIBLE).equals(p.args[0])) p.args[0] = View.GONE;
            }
        });
        n += H.hookAll(f, "handleShowFingerprintIcon", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!FpArt.iconHidden()) return;
                try {
                    Object iv = XposedHelpers.getObjectField(p.thisObject, "mFingerprintIcon");
                    if (iv instanceof ImageView) ((ImageView) iv).setVisibility(View.GONE);
                } catch (Throwable t) { Lx.e("fingerprint-ui: hide", t); }
            }
        });
        Lx.i("Fingerprint-UI: bouncer icon hooks ready (" + n + " methods)");
    }

    private static void apply(final ImageView view, final int color) {
        final Context c = Lx.context() != null ? Lx.context() : view.getContext();
        if (!FpArt.iconNeedsProvider()) {
            Drawable d = FpArt.icon(c, color);
            if (d != null) { view.setImageTintList(null); view.setImageDrawable(d); }
            return;
        }
        new Thread(() -> {
            try {
                final Drawable d = FpArt.icon(c, color);
                view.post(() -> {
                    if (!FpArt.iconActive() || d == null) return;
                    view.setImageTintList(null);
                    view.setImageDrawable(d);
                });
            } catch (Throwable t) { Lx.e("fingerprint-ui: icon load", t); }
        }, "rmtweaks-fp-icon").start();
    }
}
