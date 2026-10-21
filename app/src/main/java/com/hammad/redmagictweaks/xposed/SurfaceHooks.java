package com.hammad.redmagictweaks.xposed;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import com.hammad.redmagictweaks.SurfaceFeatures;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui — notification cards (NotificationBackend), automatic heads-up suppression
 * (NotificationBehaviorBackend), status-bar notification icon limit / overflow count (NotificationIconBackend),
 * volume sliders (VolumeBackend) and power-menu buttons (PowerMenuBackend), ported from nubia-ly. All hooks read
 * the module settings at call time (snapshot refreshed once per second); drawing lives in SurfaceArt.
 */
final class SurfaceHooks {
    private SurfaceHooks() {}

    private static final String NOTIF_BG = "com.android.systemui.statusbar.notification.row.NotificationBackgroundView";
    private static final String NOTIF_ROW = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow";
    private static final String NOTIF_BASE = "com.android.systemui.statusbar.notification.row.ActivatableNotificationView";
    private static final String HEADS_UP = "com.android.systemui.statusbar.notification.headsup.HeadsUpManagerImpl";
    private static final String NOTIF_ENTRY = "com.android.systemui.statusbar.notification.collection.NotificationEntry";
    private static final String ICON_CONTAINER = "com.android.systemui.statusbar.phone.NotificationIconContainer";
    private static final String ICON_VIEW = "com.android.systemui.statusbar.StatusBarIconView";
    private static final String STATUS_BAR_VIEW = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static final String VOLUME_DIALOG = "com.zte.feature.volume.MfvVolumeDialogImpl";
    private static final String VOLUME_BASE = "com.android.systemui.volume.VolumeDialogImpl";
    private static final String VOLUME_ROW = VOLUME_BASE + "$VolumeRow";
    private static final String VOLUME_SLIDER = "com.zte.mifavor.widget.ColorSeekBarZTE";
    private static final String POWER_DIALOG = "com.zte.feature.globalaction.MfvGlobalActionsDialog$MFVActionsDialog";
    private static final String VOLUME_BG = "com.zte.mifavor.views.ClipRectsDrawable";

    // ------------------------------------------------------------------------------------------- settings snapshot

    static final class S {
        int notifStyle, notifOpacity, notifCorner, iconLimit, volume, power;
        boolean noAutoHeadsUp, overflowCount, volOneUi, pwrOneUi;
        OneUiVolumeArt.Look one = OneUiVolumeArt.look(new OneUiVolumeArt.Src() {
            @Override public int choice(String key) { return 0; }
            @Override public boolean on(String key) { return false; }
        });
    }
    private static final OneUiVolumeArt.Src CFG = new OneUiVolumeArt.Src() {
        @Override public int choice(String key) { return Cfg.choice(key); }
        @Override public boolean on(String key) { return Cfg.on(key); }
    };
    private static volatile S snap = new S();
    private static volatile long snapAt;

    static S s() {
        long now = System.currentTimeMillis();
        if (now - snapAt > 1000) {
            S n = new S();
            n.notifStyle = clamp(Cfg.choice(SurfaceFeatures.NOTIF_STYLE), SurfaceArt.NOTIF_STYLES.length - 1);
            n.notifOpacity = SurfaceArt.opacity(Cfg.choice(SurfaceFeatures.NOTIF_OPACITY));
            n.notifCorner = SurfaceArt.cornerDp(Cfg.choice(SurfaceFeatures.NOTIF_CORNERS));
            n.noAutoHeadsUp = Cfg.on(SurfaceFeatures.NOTIF_NO_AUTO_HEADSUP);
            n.overflowCount = Cfg.on(SurfaceFeatures.NOTIF_OVERFLOW_COUNT);
            n.iconLimit = SurfaceFeatures.iconLimit(Cfg.choice(SurfaceFeatures.NOTIF_ICON_LIMIT));
            n.volume = clamp(Cfg.choice(SurfaceFeatures.VOL_STYLE), SurfaceArt.VOLUME_STYLES.length - 1);
            n.power = clamp(Cfg.choice(SurfaceFeatures.PWR_STYLE), SurfaceArt.CARD_STYLES.length - 1);
            n.volOneUi = Cfg.on(SurfaceFeatures.VOL_ONEUI);
            n.pwrOneUi = Cfg.on(SurfaceFeatures.PWR_ONEUI);
            n.one = OneUiVolumeArt.look(CFG);
            S old = snap;
            snap = n; snapAt = now;
            if (old.iconLimit != n.iconLimit || old.overflowCount != n.overflowCount) refreshIcons();
        }
        return snap;
    }
    private static int clamp(int v, int max) { return v < 0 || v > max ? 0 : v; }
    private static boolean main() { return Looper.myLooper() == Looper.getMainLooper(); }
    private static float density(View v) { try { return v.getResources().getDisplayMetrics().density; } catch (Throwable t) { return 3f; } }
    private static boolean dark(View v) { try { return (v.getResources().getConfiguration().uiMode & 0x30) == 0x20; } catch (Throwable t) { return true; } }
    private static View parentView(View v) { ViewParent p = v.getParent(); return p instanceof View ? (View) p : null; }

    static void install(ClassLoader cl) {
        try { notification(cl); } catch (Throwable t) { Lx.e("Surface: notification install", t); }
        try { headsUp(cl); } catch (Throwable t) { Lx.e("Surface: heads-up install", t); }
        try { icons(cl); } catch (Throwable t) { Lx.e("Surface: notification icons install", t); }
        try { volume(cl); } catch (Throwable t) { Lx.e("Surface: volume install", t); }
        try { power(cl); } catch (Throwable t) { Lx.e("Surface: power menu install", t); }
        Lx.i("Surface: hooks ready");
    }

    // ============================================================================================= notification cards

    /** Only the normal background of an ExpandableNotificationRow (not guts, not the dismiss / menu backgrounds). */
    private static boolean notifEligible(View view) {
        if (view == null || !view.isAttachedToWindow() || !view.getClass().getName().equals(NOTIF_BG)) return false;
        View owner = parentView(view);
        for (int depth = 0; depth < 12 && owner != null; depth++, owner = parentView(owner))
            if (owner.getClass().getName().equals(NOTIF_ROW)) { try { return XposedHelpers.getObjectField(owner, "mBackgroundNormal") == view; } catch (Throwable t) { return false; } }
        return false;
    }

    private static void notification(ClassLoader cl) {
        Class<?> bg = H.cls(cl, NOTIF_BG);
        if (bg == null) return;
        if (H.cls(cl, NOTIF_ROW) == null || H.cls(cl, NOTIF_BASE) == null) Lx.w("Surface: notification row classes missing, card styling may not match");
        H.hook(H.find(bg, "draw", Canvas.class, Drawable.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(final MethodHookParam p) {
                S s = s();
                if (s.notifStyle <= 0 && s.notifOpacity <= 0 && s.notifCorner <= 0) return;
                if (!main() || !(p.thisObject instanceof View)) return;
                final View view = (View) p.thisObject;
                if (!notifEligible(view)) return;
                boolean nativeDrawn = false;
                try {
                    if (p.args[1] == null || XposedHelpers.getObjectField(view, "mBackground") != p.args[1] || !(p.args[1] instanceof Drawable)) return;
                    Drawable nativeBg = (Drawable) p.args[1];
                    Canvas canvas = (Canvas) p.args[0];
                    float density = density(view);
                    int saved = canvas.saveLayerAlpha(0, 0, view.getWidth(), view.getHeight(), s.notifOpacity == 0 ? 255 : s.notifOpacity * 255 / 100);
                    try {
                        XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args);
                        nativeDrawn = true;
                        RectF bounds = new RectF(nativeBg.getBounds());
                        float[] radii = (float[]) XposedHelpers.getObjectField(view, "mCornerRadii");
                        if (s.notifStyle > 0 && radii != null) SurfaceArt.drawCard(s.notifStyle, canvas, bounds, radii, density, dark(view));
                        if (s.notifCorner != 0 && !bounds.isEmpty()) {
                            Path path = new Path(); path.addRoundRect(bounds, s.notifCorner * density, s.notifCorner * density, Path.Direction.CW);
                            // Clear outside the extra corner clip on this background-only layer.
                            int clip = canvas.save();
                            try { canvas.clipOutPath(path); canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); } finally { canvas.restoreToCount(clip); }
                        }
                    } finally { canvas.restoreToCount(saved); }
                    p.setResult(null);
                } catch (Throwable t) { Lx.e("Surface: notification draw", t); if (nativeDrawn) p.setResult(null); }
            }
        });
        Lx.i("Surface: notification card hook ready");
    }

    // ============================================================================================= heads-up

    private static void headsUp(ClassLoader cl) {
        Class<?> owner = H.cls(cl, HEADS_UP), entry = H.cls(cl, NOTIF_ENTRY);
        if (owner == null || entry == null) return;
        // public void showNotification(NotificationEntry, boolean pinnedByUser)  — HeadsUpManagerImpl.java:1422
        H.hook(H.find(owner, "showNotification", entry, boolean.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (s().noAutoHeadsUp && Boolean.FALSE.equals(p.args[1])) p.setResult(null);
            }
        });
        Lx.i("Surface: heads-up hook ready");
    }

    // ============================================================================================= status-bar notification icons

    private static final Map<ViewGroup, Boolean> iconOwners = new WeakHashMap<>();
    private static Handler mainHandler;

    private static boolean iconsEligible(ViewGroup owner) {
        if (!owner.getClass().getName().equals(ICON_CONTAINER) || !owner.isAttachedToWindow() || XposedHelpers.getObjectField(owner, "mIsolatedIcon") != null) return false;
        View view = owner;
        for (int n = 0; n < 16 && view != null; n++) { if (view.getClass().getName().equals(STATUS_BAR_VIEW)) return true; view = parentView(view); }
        return false;
    }

    private static void icons(ClassLoader cl) {
        Class<?> owner = H.cls(cl, ICON_CONTAINER), icon = H.cls(cl, ICON_VIEW);
        if (owner == null || icon == null) return;
        H.hook(H.find(owner, "calculateIconXTranslations"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!p.hasThrowable() && main() && p.thisObject instanceof ViewGroup) iconOwners.put((ViewGroup) p.thisObject, Boolean.TRUE);
            }
        });
        // public boolean shouldForceOverflow(int index, int speedBump, float appearAmount, int maxIcons)  — :713
        H.hook(H.find(owner, "shouldForceOverflow", int.class, int.class, float.class, int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                int limit = s().iconLimit;
                if (limit == 0 || !main() || !(p.thisObject instanceof ViewGroup)) return;
                try { if (iconsEligible((ViewGroup) p.thisObject)) p.args[3] = limit; } catch (Throwable t) { Lx.e("Surface: icon limit", t); }
            }
        });
        // Draws "+N" instead of the overflow dot on the icon that is in the dot state (StatusBarIconView.onDraw :458).
        H.hook(H.find(icon, "onDraw", Canvas.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (!s().overflowCount || !main() || !(p.thisObject instanceof View)) return;
                View iconView = (View) p.thisObject;
                if (!(iconView.getParent() instanceof ViewGroup)) return;
                ViewGroup container = (ViewGroup) iconView.getParent();
                try {
                    if (!iconsEligible(container) || !iconView.getClass().getName().equals(ICON_VIEW) || !Boolean.TRUE.equals(XposedHelpers.callMethod(iconView, "isNotification")) || ((Number) XposedHelpers.callMethod(iconView, "getVisibleState")).intValue() != 1) return;
                    Object raw = XposedHelpers.getObjectField(container, "mIconStates");
                    if (!(raw instanceof Map)) return;
                    Map<?, ?> states = (Map<?, ?>) raw;
                    int hidden = 0;
                    for (int n = 0; n < container.getChildCount(); n++) {
                        View child = container.getChildAt(n);
                        if (!child.getClass().getName().equals(ICON_VIEW) || !Boolean.TRUE.equals(XposedHelpers.callMethod(child, "isNotification")) || Boolean.TRUE.equals(XposedHelpers.callMethod(child, "isIconBlocked"))) continue;
                        Object state = states.get(child);
                        if (state != null && !Boolean.TRUE.equals(XposedHelpers.getObjectField(state, "hidden")) && ((Number) XposedHelpers.getObjectField(state, "iconAppearAmount")).floatValue() > 0 && ((Number) XposedHelpers.getObjectField(state, "visibleState")).intValue() != 0) hidden++;
                    }
                    if (hidden < 1 || iconView.getWidth() <= 0 || iconView.getHeight() <= 0) return;
                    float appearance = ((Number) XposedHelpers.getObjectField(iconView, "mDotAppearAmount")).floatValue();
                    if (!Float.isFinite(appearance) || appearance <= 0) return;
                    int color = ((Number) XposedHelpers.getObjectField(iconView, "mDecorColor")).intValue();
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(color); paint.setAlpha(Math.round(Color.alpha(color) * Math.min(1f, appearance)));
                    paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(iconView.getHeight() * .52f);
                    String text = hidden > 99 ? "99+" : "+" + hidden;
                    float width = iconView.getWidth() * .95f, measured = paint.measureText(text);
                    if (measured > width) paint.setTextSize(paint.getTextSize() * width / measured);
                    Canvas c = (Canvas) p.args[0];
                    int save = c.save();
                    try { c.clipRect(0, 0, iconView.getWidth(), iconView.getHeight()); Paint.FontMetrics fm = paint.getFontMetrics(); c.drawText(text, iconView.getWidth() / 2f, (iconView.getHeight() - fm.ascent - fm.descent) / 2f, paint); }
                    finally { c.restoreToCount(save); }
                    p.setResult(null);
                } catch (Throwable t) { Lx.e("Surface: overflow count", t); }
            }
        });
        Lx.i("Surface: notification icon hooks ready");
    }

    /** Setting changed: let every known icon row recompute its overflow (nubia-ly NotificationIconBackend.refresh). */
    private static void refreshIcons() {
        if (iconOwners.isEmpty()) return;
        try {
            if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                for (ViewGroup owner : new ArrayList<>(iconOwners.keySet())) {
                    try { if (iconsEligible(owner)) { XposedHelpers.callMethod(owner, "updateState"); owner.requestLayout(); owner.invalidate(); } } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    // ============================================================================================= volume sliders

    private static final class RowEntry {
        final WeakReference<Object> dialog, row;
        RowEntry(Object dialog, Object row) { this.dialog = new WeakReference<>(dialog); this.row = new WeakReference<>(row); }
    }
    private static final Map<Object, Boolean> volumeDialogs = new WeakHashMap<>();
    private static final Map<SeekBar, RowEntry> volumeRows = new WeakHashMap<>();

    private static void volume(ClassLoader cl) {
        Class<?> dialog = H.cls(cl, VOLUME_DIALOG), base = H.cls(cl, VOLUME_BASE), row = H.cls(cl, VOLUME_ROW), slider = H.cls(cl, VOLUME_SLIDER);
        if (dialog == null || base == null || row == null || slider == null) return;
        try {
            for (String f : new String[]{"mLeftPadding", "mTopPadding", "mLength", "mTrackSize", "mRadii"}) slider.getDeclaredField(f);
            row.getDeclaredField("slider"); row.getDeclaredField("view"); base.getDeclaredField("mRows");
        } catch (Throwable t) { Lx.w("Surface: volume slider geometry fields missing (" + t + "), volume style disabled"); return; }
        XC_MethodHook discover = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main() || !p.thisObject.getClass().getName().equals(VOLUME_DIALOG)) return;
                try {
                    volumeDialogs.put(p.thisObject, Boolean.TRUE); scanRows(p.thisObject);
                    boolean oneUi = s().volOneUi;
                    for (Map.Entry<SeekBar, RowEntry> e : new ArrayList<>(volumeRows.entrySet()))
                        if (e.getValue().dialog.get() == p.thisObject) { if (oneUi) oneUiGeometry(e.getKey()); else stockGeometry(e.getKey(), e.getValue()); }
                } catch (Throwable t) { Lx.e("Surface: volume rows", t); }
            }
        };
        H.hook(H.find(dialog, "initRow", row, int.class, int.class, int.class, boolean.class, boolean.class), discover);
        H.hook(H.find(dialog, "showH", int.class, boolean.class, int.class), discover);
        H.hook(H.find(slider, "onDraw", Canvas.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(final MethodHookParam p) {
                S cur = s();
                int style = cur.volume;
                if ((style <= 0 && !cur.volOneUi) || !main() || !(p.thisObject instanceof SeekBar)) return;
                SeekBar sb = (SeekBar) p.thisObject;
                RowEntry entry = volumeRows.get(sb);
                if (!rowEligible(sb, entry)) return;
                if (cur.volOneUi) {
                    try { if (drawOneUiVolume(sb, entry, (Canvas) p.args[0])) p.setResult(null); } catch (Throwable t) { Lx.e("Surface: One UI volume draw", t); }
                    return;
                }
                try {
                    float left = ((Number) XposedHelpers.getObjectField(sb, "mLeftPadding")).floatValue(), top = ((Number) XposedHelpers.getObjectField(sb, "mTopPadding")).floatValue();
                    float width = ((Number) XposedHelpers.getObjectField(sb, "mLength")).floatValue(), height = ((Number) XposedHelpers.getObjectField(sb, "mTrackSize")).floatValue();
                    if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) return;
                    float[] radii = (float[]) XposedHelpers.getObjectField(sb, "mRadii");
                    if (radii == null) { radii = new float[8]; Arrays.fill(radii, height / 2); }
                    if (SurfaceArt.drawVolume(style, (Canvas) p.args[0], new RectF(left, top, left + width, top + height), radii, density(sb),
                            () -> XposedBridge.invokeOriginalMethod(p.method, p.thisObject, p.args))) p.setResult(null);
                } catch (Throwable t) { Lx.e("Surface: volume draw", t); }
            }
        });
        // One UI 8 panel size: the column width ZTE uses for collapsed rows, then the views it does not size itself
        Method rowsWidth = H.find(dialog, "updateRowsWidth");
        if (rowsWidth != null) {
            H.hook(rowsWidth, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!main()) return;
                    try { volFields(p.thisObject); } catch (Throwable t) { Lx.e("Surface: volume column", t); }
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !main()) return;
                    try { volLayout(p.thisObject); } catch (Throwable t) { Lx.e("Surface: volume layout", t); }
                }
            });
        } else Lx.w("Surface: updateRowsWidth missing, One UI volume size stays stock");
        // the blurred column and ringer button take the bar's shape
        Class<?> bg = H.cls(cl, VOLUME_BG);
        Method rects = bg == null ? null : H.find(bg, "setRects", RectF.class, RectF.class, RectF.class);
        if (rects != null) {
            H.hook(rects, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    S cur = s();
                    if (p.hasThrowable() || !cur.volOneUi || cur.one.shape == 0) return;
                    try { volPanelShape(p.thisObject, cur.one); } catch (Throwable t) { Lx.e("Surface: volume panel shape", t); }
                }
            });
        }
        Lx.i("Surface: volume hooks ready");
    }
    private static void scanRows(Object dialog) {
        Object value = XposedHelpers.getObjectField(dialog, "mRows");
        if (!(value instanceof List) || ((List<?>) value).size() > 32) return;
        volumeRows.entrySet().removeIf(e -> e.getValue().dialog.get() == dialog);
        for (Object row : (List<?>) value) {
            if (row == null || !row.getClass().getName().equals(VOLUME_ROW)) continue;
            Object candidate = XposedHelpers.getObjectField(row, "slider");
            if (candidate instanceof SeekBar && candidate.getClass().getName().equals(VOLUME_SLIDER)) volumeRows.put((SeekBar) candidate, new RowEntry(dialog, row));
        }
    }
    private static boolean rowEligible(SeekBar slider, RowEntry entry) {
        if (slider == null || entry == null || !slider.isAttachedToWindow()) return false;
        Object dialog = entry.dialog.get(), row = entry.row.get();
        if (dialog == null || row == null || !dialog.getClass().getName().equals(VOLUME_DIALOG)) return false;
        try {
            Object list = XposedHelpers.getObjectField(dialog, "mRows");
            if (!(list instanceof List) || ((List<?>) list).size() > 32 || !((List<?>) list).contains(row) || XposedHelpers.getObjectField(row, "slider") != slider) return false;
            Object root = XposedHelpers.getObjectField(row, "view");
            View current = slider;
            for (int depth = 0; depth < 12 && current != null; depth++) { if (current == root) return true; current = parentView(current); }
            return false;
        } catch (Throwable t) { return false; }
    }

    // ------------------------------------------------------------------------------------------- One UI 8 volume panel

    /** slider -> {stock mTrackSize}, its stock corner radii, and the row icon we moved */
    private static final Map<SeekBar, int[]> volStockSize = new WeakHashMap<>();
    private static final Map<SeekBar, float[]> volStockRadii = new WeakHashMap<>();
    private static final Map<View, Boolean> volMovedIcons = new WeakHashMap<>();

    private static int dp(View v, float dp) { return Math.round(dp * density(v)); }

    /**
     * ZTE's row (volume_dialog_row_mfv): a ColorSeekBarZTE (20 dp thick; its measured height is its track) rotated 270
     * degrees in a 56 dp column, a thumb dot, and the stream icon under it. One UI 8: the bar is as thick as the chosen
     * width (the column is 4 dp wider, see volFields), corners per the chosen shape. The cached track path is dropped.
     */
    private static void oneUiGeometry(SeekBar sb) {
        View frame = parentView(sb);
        OneUiVolumeArt.Look l = s().one;
        int column = frame != null && frame.getWidth() > 0 ? frame.getWidth() : dp(sb, 56);
        int want = Math.max(dp(sb, 24), Math.min(dp(sb, OneUiVolumeArt.thickDp(l)), column - dp(sb, 4)));
        int cur = XposedHelpers.getIntField(sb, "mTrackSize");
        if (!volStockSize.containsKey(sb)) {
            volStockSize.put(sb, new int[]{cur});
            Object r = XposedHelpers.getObjectField(sb, "mRadii");
            volStockRadii.put(sb, r instanceof float[] ? ((float[]) r).clone() : null);
        }
        if (cur == want) return;
        XposedHelpers.setIntField(sb, "mTrackSize", want);
        XposedHelpers.setObjectField(sb, "mRadii", OneUiVolumeArt.barRadii(l, want, density(sb)));
        XposedHelpers.setObjectField(sb, "mPath", null);
        sb.requestLayout(); sb.invalidate();
    }
    private static void stockGeometry(SeekBar sb, RowEntry entry) {
        int[] size = volStockSize.remove(sb);
        float[] radii = volStockRadii.remove(sb);
        if (size != null) {
            XposedHelpers.setIntField(sb, "mTrackSize", size[0]);
            if (radii != null) XposedHelpers.setObjectField(sb, "mRadii", radii);
            XposedHelpers.setObjectField(sb, "mPath", null);
            sb.requestLayout(); sb.invalidate();
        }
        Object row = entry == null ? null : entry.row.get();
        Object icon = row == null ? null : XposedHelpers.getObjectField(row, "icon");
        if (icon instanceof ImageView && volMovedIcons.remove(icon) != null) { ((View) icon).setTranslationY(0f); ((ImageView) icon).setImageTintList(null); }
    }

    // --- panel size -------------------------------------------------------------------------------------------------

    /** dialog -> stock {mRowWidth, mDialogMiniWidthWithPadding, mDialogMiniWidthWithPaddingTowPanel} */
    private static final Map<Object, int[]> volStockFields = new WeakHashMap<>();
    /** view -> stock {layout width, layout height, min width, min height, top margin} */
    private static final Map<View, int[]> volStockLp = new WeakHashMap<>();
    private static final int KEEP = Integer.MIN_VALUE;

    /** Before updateRowsWidth: the collapsed column (and the dialog widths ZTE derives from it) for the chosen width. */
    private static void volFields(Object dlg) {
        int[] st = volStockFields.get(dlg);
        if (st == null) {
            st = new int[]{XposedHelpers.getIntField(dlg, "mRowWidth"), XposedHelpers.getIntField(dlg, "mDialogMiniWidthWithPadding"),
                    XposedHelpers.getIntField(dlg, "mDialogMiniWidthWithPaddingTowPanel")};
            if (st[0] <= 0) return;
            volStockFields.put(dlg, st);
        }
        S cur = s();
        int col = st[0];
        if (cur.volOneUi) {
            Object dv = XposedHelpers.getObjectField(dlg, "mDialogView");
            float d = dv instanceof View ? density((View) dv) : 3f;
            col = Math.min(st[0], Math.round(OneUiVolumeArt.columnDp(cur.one) * d));
        }
        int delta = col - st[0];
        if (XposedHelpers.getIntField(dlg, "mRowWidth") == col) return;
        XposedHelpers.setIntField(dlg, "mRowWidth", col);
        XposedHelpers.setIntField(dlg, "mDialogMiniWidthWithPadding", st[1] + delta);
        XposedHelpers.setIntField(dlg, "mDialogMiniWidthWithPaddingTowPanel", st[2] + 2 * delta);
    }

    /**
     * After updateRowsWidth: makes the panel taller (the slider, its frame, the row, the column, the second column and
     * the dialog by the same amount), lets the slider fill its frame, folds the empty icon slot into the bar when the icon
     * sits inside it, and narrows the expand button and (collapsed) the ringer button to the column. Everything goes
     * back to stock when the One UI panel is off.
     */
    private static void volLayout(Object dlg) {
        S cur = s();
        boolean on = cur.volOneUi;
        Object dvo = XposedHelpers.getObjectField(dlg, "mDialogView");
        if (!(dvo instanceof View)) return;
        View dv = (View) dvo;
        int[] stF = volStockFields.get(dlg);
        int stockCol = stF != null ? stF[0] : dp(dv, 56);
        int col = on ? XposedHelpers.getIntField(dlg, "mRowWidth") : stockCol;
        boolean expanded = XposedHelpers.getBooleanField(dlg, "mExpanded");
        int extra = 0;
        if (on) {
            extra = dp(dv, OneUiVolumeArt.extraDp(cur.one));
            int stockH = volStockLp.containsKey(dv) ? volStockLp.get(dv)[1] : dv.getLayoutParams() != null ? dv.getLayoutParams().height : -1;
            if (stockH <= 0) stockH = dp(dv, 326);
            int room = dv.getResources().getDisplayMetrics().heightPixels - stockH - dp(dv, 64);
            extra = Math.max(0, Math.min(extra, room));
        }
        int dCol = col - stockCol, minCol = on ? col : -1;
        boolean inside = on && OneUiVolumeArt.iconInside(cur.one);
        Object list = XposedHelpers.getObjectField(dlg, "mRows");
        if (list instanceof List && ((List<?>) list).size() <= 32) {
            for (Object row : (List<?>) list) {
                if (row == null) continue;
                Object so = XposedHelpers.getObjectField(row, "slider"), io = XposedHelpers.getObjectField(row, "icon");
                if (!(so instanceof SeekBar) || !so.getClass().getName().equals(VOLUME_SLIDER)) continue;
                View sb = (View) so, frame = parentView(sb), inner = frame == null ? null : parentView(frame);
                if (frame == null || inner == null) continue;
                View icon = io instanceof View && parentView((View) io) == inner ? (View) io : null;
                int fold = 0;
                if (inside && icon != null && icon.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    int[] ist = stockLp(icon);
                    ViewGroup.MarginLayoutParams ilp = (ViewGroup.MarginLayoutParams) icon.getLayoutParams();
                    fold = Math.max(0, (ist[1] > 0 ? ist[1] : dp(dv, 24)) + ilp.bottomMargin - dp(dv, 6));
                }
                int[] fst = stockLp(frame), sst = stockLp(sb);
                int slider = on && fst[1] > 0 && sst[0] > 0 ? fst[1] + extra + fold - dp(dv, 6) - sst[0] : 0;
                fit(sb, on ? slider : 0, KEEP, -1, -1, 0);
                fit(frame, KEEP, extra + fold, -1, -1, 0);
                fit(inner, dCol, extra, -1, -1, 0);
                if (icon != null) fit(icon, KEEP, KEEP, -1, -1, -fold);
                if (!on && icon != null) { icon.setTranslationY(0f); if (volMovedIcons.remove(icon) != null && icon instanceof ImageView) ((ImageView) icon).setImageTintList(null); }
                else if (on && !inside && icon != null && volMovedIcons.remove(icon) != null) { icon.setTranslationY(0f); if (icon instanceof ImageView) ((ImageView) icon).setImageTintList(null); }
            }
        }
        View mainV = viewField(dlg, "mDialogMain"), rows = viewField(dlg, "mDialogRowsView"), rows2 = viewField(dlg, "mDialogRowsView2");
        fit(mainV, KEEP, extra, minCol, -1, 0);
        fit(rows, KEEP, KEEP, minCol, -1, 0);
        fit(rows2, KEEP, extra, minCol, -1, 0);
        fit(dv, KEEP, extra, -1, -1, 0);
        fit(viewField(dlg, "mExpandButton"), dCol, KEEP, -1, -1, 0);
        View ringer = viewField(dlg, "mRinger");
        if (on && !expanded) fit(ringer, KEEP, dCol, col, col, 0);
        else fit(ringer, KEEP, 0, -1, -1, 0);
    }

    private static View viewField(Object o, String name) {
        try { Object v = XposedHelpers.getObjectField(o, name); return v instanceof View ? (View) v : null; } catch (Throwable t) { return null; }
    }

    private static int[] stockLp(View v) {
        int[] st = volStockLp.get(v);
        if (st == null) {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            st = new int[]{lp == null ? 0 : lp.width, lp == null ? 0 : lp.height, v.getMinimumWidth(), v.getMinimumHeight(),
                    lp instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) lp).topMargin : 0};
            volStockLp.put(v, st);
        }
        return st;
    }

    /**
     * Sets a fixed layout width / height to stock + dw / dh (KEEP = leave it; a size ZTE sets itself is never touched),
     * the minimum width / height to minW / minH (-1 = stock) and the top margin to stock + dTop. A view that was never
     * changed and needs no change is left alone.
     */
    private static void fit(View v, int dw, int dh, int minW, int minH, int dTop) {
        if (v == null || v.getLayoutParams() == null) return;
        boolean wants = (dw != KEEP && dw != 0) || (dh != KEEP && dh != 0) || minW >= 0 || minH >= 0 || dTop != 0;
        if (!wants && !volStockLp.containsKey(v)) return;
        int[] st = stockLp(v);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        boolean changed = false;
        if (dw != KEEP && st[0] > 0) { int w = Math.max(1, st[0] + dw); if (lp.width != w) { lp.width = w; changed = true; } }
        if (dh != KEEP && st[1] > 0) { int h = Math.max(1, st[1] + dh); if (lp.height != h) { lp.height = h; changed = true; } }
        int mw = minW >= 0 ? minW : st[2], mh = minH >= 0 ? minH : st[3];
        if (v.getMinimumWidth() != mw) { v.setMinimumWidth(mw); changed = true; }
        if (v.getMinimumHeight() != mh) { v.setMinimumHeight(mh); changed = true; }
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
            if (m.topMargin != st[4] + dTop) { m.topMargin = st[4] + dTop; changed = true; }
        }
        if (changed) v.setLayoutParams(lp);
    }

    /** The blurred column, second column and ringer button get the bar's corners (the wide expanded panel stays stock). */
    private static void volPanelShape(Object drawable, OneUiVolumeArt.Look l) {
        float d = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        Path all = (Path) XposedHelpers.getObjectField(drawable, "path");
        String[][] parts = {{"rowsRect", "rowsPath"}, {"rows2Rect", "rows2Path"}, {"ringerRect", "ringerPath"}};
        boolean any = false;
        for (String[] part : parts) {
            RectF r = (RectF) XposedHelpers.getObjectField(drawable, part[0]);
            if (r == null || r.isEmpty() || r.width() > 64 * d) continue;
            float[] radii = OneUiVolumeArt.panelRadii(l, r.width(), d);
            if (radii == null) return;
            Path p = (Path) XposedHelpers.getObjectField(drawable, part[1]);
            p.reset();
            p.addRoundRect(r, radii, Path.Direction.CW);
            any = true;
        }
        if (!any || all == null) return;
        all.reset();
        for (String[] part : parts) {
            RectF r = (RectF) XposedHelpers.getObjectField(drawable, part[0]);
            if (r != null && !r.isEmpty()) all.addPath((Path) XposedHelpers.getObjectField(drawable, part[1]));
        }
    }

    // --- drawing ----------------------------------------------------------------------------------------------------

    /**
     * Track, fill (a circle at 0 % that grows to the whole bar at 100 %, from the start of the slider: the bottom, as
     * the bar is rotated 270 degrees), colours, shape and level number as chosen. The row icon is moved into that circle
     * and tinted to read over the fill (or left under the bar). Returns false (stock drawing) when the view is not the
     * shape we expect.
     */
    private static boolean drawOneUiVolume(SeekBar sb, RowEntry entry, Canvas canvas) {
        if (!volStockSize.containsKey(sb)) oneUiGeometry(sb);
        float left = Math.max(0, XposedHelpers.getIntField(sb, "mLeftPadding")), top = Math.max(0, XposedHelpers.getIntField(sb, "mTopPadding"));
        float length = XposedHelpers.getIntField(sb, "mLength"), thick = XposedHelpers.getIntField(sb, "mTrackSize");
        if (length <= 0 || thick <= 0 || length < thick) return false;
        float min = XposedHelpers.getFloatField(sb, "mMin"), max = XposedHelpers.getFloatField(sb, "mMax"), progress = XposedHelpers.getFloatField(sb, "mProgress");
        float fraction = max > min ? (progress - min) / (max - min) : 0f;
        boolean dark = dark(sb);
        OneUiVolumeArt.Look l = s().one;
        int[] fill = OneUiVolumeArt.fill(sb.getContext(), l, dark);
        OneUiVolumeArt.drawBar(canvas, new RectF(left, top, left + length, top + thick), fraction, l, fill, dark, sb.isEnabled(), density(sb));
        if (OneUiVolumeArt.iconInside(l)) placeIcon(sb, entry, thick, left, OneUiVolumeArt.over(fill[0]));
        return true;
    }

    /** Puts the row's stream icon (the mute button) into the start circle of the slider and tints it. */
    private static void placeIcon(SeekBar sb, RowEntry entry, float thick, float left, int tint) {
        Object row = entry == null ? null : entry.row.get();
        Object o = row == null ? null : XposedHelpers.getObjectField(row, "icon");
        if (!(o instanceof ImageView)) return;
        ImageView icon = (ImageView) o;
        View frame = parentView(sb);
        if (frame == null || parentView(icon) != parentView(frame)) return;
        float rot = ((sb.getRotation() % 360f) + 360f) % 360f;
        if (Math.abs(rot - 270f) > 1f) return;                       // only the stock bottom-to-top bar
        // rotated 270 degrees about its centre: the slider's start (left) edge is at the bottom of what we see
        float centre = frame.getTop() + sb.getTop() + sb.getHeight() / 2f;
        float startCircle = centre + sb.getWidth() / 2f - left - thick / 2f;
        float want = startCircle - (icon.getTop() + icon.getHeight() / 2f);
        if (Math.abs(icon.getTranslationY() - want) > 0.5f) icon.setTranslationY(want);
        volMovedIcons.put(icon, Boolean.TRUE);
        android.content.res.ColorStateList cur = icon.getImageTintList();
        if (cur == null || cur.getDefaultColor() != tint) icon.setImageTintList(android.content.res.ColorStateList.valueOf(tint));
        if (icon.getAlpha() != 1f) icon.setAlpha(1f);
    }

    // ============================================================================================= power menu

    private static final Map<Dialog, Boolean> powerDialogs = new WeakHashMap<>();

    private static void power(ClassLoader cl) {
        Class<?> owner = H.cls(cl, POWER_DIALOG);
        if (owner == null) return;
        try { owner.getDeclaredField("mContentView"); owner.getDeclaredField("mPowerContent"); owner.getDeclaredField("mResetContent"); }
        catch (Throwable t) { Lx.w("Surface: power dialog fields missing (" + t + "), power-menu style disabled"); return; }
        H.hook(H.find(owner, "initView"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.hasThrowable() || !main() || !(p.thisObject instanceof Dialog)) return;
                powerDialogs.put((Dialog) p.thisObject, Boolean.TRUE);
                if (s().pwrOneUi) { try { oneUiPower(XposedHelpers.getObjectField(p.thisObject, "mContentView")); } catch (Throwable t) { Lx.e("Surface: One UI power menu", t); } }
            }
        });
        // Decorates the native circular ImageButtons after their backgrounds and before their glyphs.
        H.hook(H.find(ImageView.class, "onDraw", Canvas.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                int style = s().power;
                if (style <= 0 || s().pwrOneUi || powerDialogs.isEmpty() || !main() || !(p.thisObject instanceof ImageView)) return;
                ImageView view = (ImageView) p.thisObject;
                try {
                    if (!powerEligible(view)) return;
                    float diameter = Math.min(view.getWidth(), view.getHeight());
                    float left = (view.getWidth() - diameter) / 2f, top = (view.getHeight() - diameter) / 2f;
                    RectF bounds = new RectF(left, top, left + diameter, top + diameter);
                    float[] corners = new float[8]; Arrays.fill(corners, diameter / 2);
                    SurfaceArt.drawCard(style, (Canvas) p.args[0], bounds, corners, density(view));
                } catch (Throwable t) { Lx.e("Surface: power draw", t); }
            }
        });
        Lx.i("Surface: power menu hooks ready");
    }
    /**
     * One UI 8 power menu over ZTE's (global_dialog_simplified_*): the round ImageButtons (tag "image_button") get a solid
     * button in the chosen colours (One UI: red for Power off, green for Restart, grey for the rest), shape and size
     * (72 dp instead of 60 by default); the names under them (tag "text_view") get the chosen label style. ZTE's blur
     * and its tap-twice confirmation stay.
     */
    private static void oneUiPower(Object content) {
        if (!(content instanceof ViewGroup)) return;
        OneUiVolumeArt.Look l = s().one;
        List<View> all = new ArrayList<>();
        collect((ViewGroup) content, all);
        for (View v : all) {
            Object tag = v.getTag();
            if (v instanceof ImageButton && "image_button".equals(tag)) {
                String id = "";
                try { id = v.getResources().getResourceEntryName(v.getId()); } catch (Throwable ignored) { }
                int[] colors = OneUiVolumeArt.pwrColors(v.getContext(), l, id);
                int size = dp(v, OneUiVolumeArt.pwrSizeDp(l));
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                float corner = OneUiVolumeArt.pwrCorner(l, size);
                if (corner < 0) shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                else { shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE); shape.setCornerRadius(corner); }
                shape.setColor(colors[0]);
                if (colors[2] != 0) shape.setStroke(Math.max(1, dp(v, 1)), colors[2]);
                v.setBackground(shape);
                v.setAlpha(1f);
                ((ImageButton) v).setImageTintList(android.content.res.ColorStateList.valueOf(colors[1]));
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp != null && (lp.width != size || lp.height != size)) { lp.width = size; lp.height = size; v.setLayoutParams(lp); }
                int pad = Math.round(size * 0.28f);
                v.setPadding(pad, pad, pad, pad);
                ((ImageButton) v).setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else if (v instanceof android.widget.TextView && "text_view".equals(tag)) {
                android.widget.TextView t = (android.widget.TextView) v;
                t.setTypeface(Typeface.create(t.getTypeface(), OneUiVolumeArt.pwrLabelBold(l) ? Typeface.BOLD : Typeface.NORMAL));
                t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, OneUiVolumeArt.pwrLabelSp(l));
                t.setTextColor(OneUiVolumeArt.pwrLabelColor(l));
                ViewGroup.LayoutParams lp = t.getLayoutParams();
                if (lp instanceof ViewGroup.MarginLayoutParams) { ((ViewGroup.MarginLayoutParams) lp).topMargin = dp(t, 12); t.setLayoutParams(lp); }
            }
        }
    }
    private static void collect(ViewGroup g, List<View> out) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            out.add(c);
            if (c instanceof ViewGroup) collect((ViewGroup) c, out);
        }
    }

    private static boolean powerEligible(ImageView view) {
        if (!view.isAttachedToWindow() || view.getClass() != ImageButton.class || !(view.getParent() instanceof RelativeLayout)) return false;
        RelativeLayout container = (RelativeLayout) view.getParent();
        String id;
        try { if (!"com.android.systemui".equals(view.getResources().getResourcePackageName(view.getId()))) return false; id = view.getResources().getResourceEntryName(view.getId()); } catch (Throwable absent) { return false; }
        String fieldName = id.equals("power") ? "mPowerContent" : id.equals("reset") ? "mResetContent" : null;
        if (fieldName == null) return false;
        for (Dialog dialog : new ArrayList<>(powerDialogs.keySet())) {
            if (!dialog.isShowing() || XposedHelpers.getObjectField(dialog, fieldName) != container) continue;
            Object root = XposedHelpers.getObjectField(dialog, "mContentView");
            if (!(root instanceof ViewGroup)) continue;
            for (View current = container; current != null; current = parentView(current)) if (current == root) return true;
        }
        return false;
    }
}
