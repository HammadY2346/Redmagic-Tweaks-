package com.hammad.redmagictweaks.xposed;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hammad.redmagictweaks.ClockFaces;
import com.hammad.redmagictweaks.LockFeatures;

import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui — the One UI 8 lock screen on RedMagic's (ZTE mifavor) keyguard:
 *   - clock: LockScreenClockStyleItem (the live clock container, scene 1, under KeyguardStatusView) gets our clock face
 *     (ClockFaces, One UI 8 stacked by default) in place of the stock clock style; addClockView() rebuilds the item, so the
 *     views are (re)added after every call;
 *   - tiles: battery ring / date / next alarm tiles under the clock instead of RedMagic's KeyguardWidgetContainer;
 *   - shortcuts: KeyguardBottomButtonView's left (phone) and right (camera) KeyguardAffordanceViews become round
 *     translucent buttons, the slide tip is hidden.
 * Declarations from the firmware 11.0.11 decompile (LockScreenClockStyleItem extends LinearLayout: public
 * LockScreenClockStyleInterface mClock; public boolean mIsSmall; public int mScene; addClockView(int,boolean,boolean),
 * refreshTime(), updateColor(int). KeyguardBottomButtonView extends FrameLayout: mLeftAffordanceView,
 * mRightAffordanceView (KeyguardAffordanceView), mSlideNoticeView; onFinishInflate(), updateAffordanceSize(Boolean),
 * updateLeftAffordanceIcon(), updateRightAffordanceIcon(), updateCameraVisibility()).
 */
final class OneUiLockHooks {
    private OneUiLockHooks() {}

    private static final String ITEM = "com.zte.mifavor.keyguard.settings.LockScreenClockStyleItem";
    private static final String HOST_ZTE = "com.zte.mifavor.keyguard.KeyguardStatusView";
    private static final String HOST_AOSP = "com.android.keyguard.KeyguardStatusView";
    private static final String CONTENT = "com.zte.mifavor.keyguard.personalclock.ClockContentView";
    private static final String WIDGETS = "com.zte.feature.keyguardwidget.KeyguardWidgetContainer";
    private static final String BOTTOM = "com.zte.mifavor.keyguard.KeyguardBottomButtonView";
    private static final String TAG_CLOCK = "rmtweaks_oneui_clock";

    static boolean clockOn() { return Cfg.on(LockFeatures.ONEUI_CLOCK); }

    static void install(ClassLoader cl) {
        Class<?> item = H.cls(cl, ITEM);
        if (item != null) {
            XC_MethodHook after = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { apply((LinearLayout) p.thisObject); } catch (Throwable t) { Lx.e("OneUiLock: clock", t); }
                }
            };
            H.hookAll(item, "addClockView", after);
            H.hookAll(item, "refreshTime", after);
            H.hookAll(item, "updateColor", after);
        }
        Class<?> bottom = H.cls(cl, BOTTOM);
        if (bottom != null) {
            XC_MethodHook after = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { shortcuts((ViewGroup) p.thisObject); } catch (Throwable t) { Lx.e("OneUiLock: shortcuts", t); }
                }
            };
            for (String m : new String[]{"onFinishInflate", "updateAffordanceSize", "updateLeftAffordanceIcon", "updateRightAffordanceIcon", "updateCameraVisibility", "setDozing", "onConfigurationChanged"})
                H.hookAll(bottom, m, after);
        }
        // notifications below the clock: ZTE puts the keyguard notification stack at a fixed top margin made for its own
        // clock styles, so it covered most of the One UI clock. The padding is raised to the clock's (tiles') bottom.
        Class<?> algo = H.cls(cl, "com.android.systemui.statusbar.phone.KeyguardClockPositionAlgorithm");
        if (algo != null) {
            H.hookAll(algo, "run", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try { stackBelowClock(p.args.length > 0 ? p.args[0] : null); } catch (Throwable t) { Lx.e("OneUiLock: stack padding", t); }
                }
            });
        }
        // ZTE positions the lock screen notifications itself (KeyguardNotificationPosition, a fixed top margin; with
        // notifications on screen its clock ended up below them): its padding is raised to below our clock as well.
        XC_MethodHook zteInt = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    int want = wantedStackPadding();
                    Object r = p.getResult();
                    if (want > 0 && r instanceof Integer && want > (Integer) r) p.setResult(want);
                } catch (Throwable t) { Lx.e("OneUiLock: zte stack padding", t); }
            }
        };
        Class<?> knp = H.cls(cl, "com.zte.feature.notification.module.KeyguardNotificationPosition");
        if (knp != null) H.hookAll(knp, "getStackScrollerPadding", zteInt);
        Class<?> nf = H.cls(cl, "com.zte.feature.notification.NotificationFeature");
        if (nf != null) H.hookAll(nf, "getKeyguardStackScrollerPadding", zteInt);
        Class<?> npa = H.cls(cl, "com.zte.adapt.mifavor.statusbar.NotificationPanelViewControllerAdapt");
        if (npa != null) H.hookAll(npa, "getLockscreenStaticTopPadding", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    int want = wantedStackPadding();
                    Object r = p.getResult();
                    if (want > 0 && r instanceof Float && want > (Float) r) p.setResult((float) want);
                } catch (Throwable t) { Lx.e("OneUiLock: static top padding", t); }
            }
        });
        Lx.i("OneUiLock: hooks ready (clock " + clockOn() + ", tiles " + Cfg.on(LockFeatures.ONEUI_TILES) + ", shortcuts " + Cfg.on(LockFeatures.ONEUI_SHORTCUTS) + ")");
    }

    // ------------------------------------------------------------------ clock

    /** The live lock screen container (not the chooser previews in the lock screen settings). */
    private static boolean live(LinearLayout item) {
        try {
            if (XposedHelpers.getIntField(item, "mScene") != 1 || XposedHelpers.getBooleanField(item, "mIsSmall")) return false;
            View cur = item;
            for (int depth = 0; cur != null && depth < 24; depth++) {
                String n = cur.getClass().getName();
                if (n.equals(CONTENT)) return false;
                if (n.equals(HOST_ZTE) || n.equals(HOST_AOSP)) return true;
                android.view.ViewParent pr = cur.getParent();
                cur = pr instanceof View ? (View) pr : null;
            }
        } catch (Throwable t) { Lx.w("OneUiLock: item state unreadable: " + t); }
        return false;
    }

    private static void apply(LinearLayout item) {
        if (!clockOn() || !live(item)) return;
        Object clock = XposedHelpers.getObjectField(item, "mClock");
        View ours = item.findViewWithTag(TAG_CLOCK);
        item.setOrientation(LinearLayout.VERTICAL);
        if (ours == null) {
            ours = new OneUiClock(item.getContext());
            ours.setTag(TAG_CLOCK);
            item.addView(ours, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            Lx.i("OneUiLock: One UI clock added");
        }
        if (clock instanceof View && ((View) clock).getVisibility() != View.GONE) ((View) clock).setVisibility(View.GONE);
        boolean tiles = Cfg.on(LockFeatures.ONEUI_TILES);
        for (int i = 0; i < item.getChildCount(); i++) {
            View c = item.getChildAt(i);
            if (c.getClass().getName().equals(WIDGETS) && tiles && c.getVisibility() != View.GONE) c.setVisibility(View.GONE);
        }
        ((OneUiClock) ours).refresh();
    }

    private static float dp(Context c, float v) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()); }

    /** The One UI clock on the live lock screen (for the notification padding). */
    private static java.lang.ref.WeakReference<OneUiClock> current = new java.lang.ref.WeakReference<>(null);
    private static int stackerId = -1;

    /** KeyguardClockPositionAlgorithm.Result.stackScrollerPadding at least down to the bottom of our clock (+ tiles). */
    private static void stackBelowClock(Object result) {
        if (result == null) return;
        int want = wantedStackPadding();
        if (want <= 0) return;
        int cur = XposedHelpers.getIntField(result, "stackScrollerPadding");
        if (want > cur) XposedHelpers.setIntField(result, "stackScrollerPadding", want);
    }

    /** Top padding of the notification stack that puts the first notification just below our clock (+ tiles), in the
     *  stack's own coordinates; -1 when the One UI clock isn't on screen. */
    private static int wantedStackPadding() {
        if (!clockOn()) return -1;
        OneUiClock clock = current.get();
        if (clock == null || !clock.isAttachedToWindow() || !clock.isShown() || clock.getHeight() == 0) return -1;
        int[] loc = new int[2];
        clock.getLocationInWindow(loc);
        int bottom = loc[1] + clock.getHeight();
        int top = 0;
        try {
            if (stackerId == -1) stackerId = clock.getResources().getIdentifier("notification_stack_scroller", "id", "com.android.systemui");
            View stack = stackerId == 0 ? null : clock.getRootView().findViewById(stackerId);
            if (stack != null) { int[] sl = new int[2]; stack.getLocationInWindow(sl); top = sl[1] - Math.round(stack.getTranslationY()); }
        } catch (Throwable ignored) { }
        return bottom - top + Math.round(dp(clock.getContext(), 14));
    }

    private static Typeface digits;
    private static boolean digitsTried;

    /** Rounded bold face: the font chosen in Look & feel, else the bundled One UI-like face, else system sans 600. */
    static Typeface digitFace() {
        try { Typeface t = CosmeticsHooks.clockTypeface(); if (t != null) return t; } catch (Throwable ignored) { }
        if (!digitsTried) {
            digitsTried = true;
            try {
                if (Entry.modulePath != null) {
                    android.content.res.AssetManager am = android.content.res.XModuleResources.createInstance(Entry.modulePath, null).getAssets();
                    digits = Typeface.createFromAsset(am, "fonts/oneui_clock.ttf");
                }
            } catch (Throwable t) { Lx.w("OneUiLock: bundled clock font unavailable (" + t + "), using sans-serif"); }
        }
        if (digits != null) return digits;
        return Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 600, false);
    }

    /** One UI lock screen clock: the chosen clock face (ClockFaces) over the optional tiles row. */
    static final class OneUiClock extends LinearLayout {
        final FaceView face;
        final LinearLayout tiles;
        final BatteryTile battery;
        final TextView calendarDay, alarmText;
        int color = Color.WHITE;
        int position;
        private final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                    int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
                    battery.set(scale > 0 ? level * 100 / scale : level, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                } else if (Intent.ACTION_TIME_TICK.equals(a)) {
                    face.invalidate();
                } else {
                    face.requestLayout();
                    face.invalidate();
                    updateDayAndAlarm();
                }
            }
        };

        OneUiClock(Context ctx) {
            super(ctx);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setClipChildren(false);
            setClipToPadding(false);

            face = new FaceView(ctx);
            addView(face, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            tiles = new LinearLayout(ctx);
            tiles.setOrientation(HORIZONTAL);
            tiles.setGravity(Gravity.CENTER_HORIZONTAL);
            LayoutParams tlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Math.round(dp(ctx, 20));
            addView(tiles, tlp);
            battery = new BatteryTile(ctx);
            tiles.addView(tile(ctx, battery), tileParams(ctx, true));
            calendarDay = new TextView(ctx);
            tiles.addView(tile(ctx, iconWithText(ctx, LockGlyph.CALENDAR, calendarDay, true)), tileParams(ctx, true));
            alarmText = new TextView(ctx);
            tiles.addView(tile(ctx, iconWithText(ctx, LockGlyph.ALARM, alarmText, false)), tileParams(ctx, false));

            setPadding(0, Math.round(dp(ctx, 4)), 0, Math.round(dp(ctx, 8)));
            getViewTreeObserver().addOnGlobalLayoutListener(this::place);
        }

        private static void shadow(TextView t) { t.setShadowLayer(t.getResources().getDisplayMetrics().density * 3, 0, 0, 0x33000000); }

        private static View tile(Context ctx, View content) {
            FrameLayout f = new FrameLayout(ctx);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x47000000);
            bg.setCornerRadius(dp(ctx, 20));
            f.setBackground(bg);
            f.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            return f;
        }

        private static LayoutParams tileParams(Context ctx, boolean gapAfter) {
            int s = Math.round(dp(ctx, 62));
            LayoutParams lp = new LayoutParams(s, s);
            if (gapAfter) lp.setMarginEnd(Math.round(dp(ctx, 12)));
            return lp;
        }

        private static View iconWithText(Context ctx, int glyph, TextView text, boolean insideIcon) {
            FrameLayout f = new FrameLayout(ctx);
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(new LockGlyph(glyph, Color.WHITE));
            int s = Math.round(dp(ctx, insideIcon ? 30 : 26));
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(s, s, Gravity.CENTER_HORIZONTAL | (insideIcon ? Gravity.CENTER_VERTICAL : Gravity.TOP));
            if (!insideIcon) ip.topMargin = Math.round(dp(ctx, 9));
            f.addView(icon, ip);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, insideIcon ? 11 : 11.5f);
            text.setIncludeFontPadding(false);
            text.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL | (insideIcon ? Gravity.CENTER_VERTICAL : Gravity.BOTTOM));
            if (insideIcon) tp.topMargin = Math.round(dp(ctx, 3)); else tp.bottomMargin = Math.round(dp(ctx, 9));
            f.addView(text, tp);
            return f;
        }

        void refresh() {
            position = Cfg.choice(LockFeatures.ONEUI_POSITION);
            face.configure();
            tiles.setVisibility(Cfg.on(LockFeatures.ONEUI_TILES) ? VISIBLE : GONE);
            int c = Color.WHITE;
            try {
                // the stock clock style's colour (dark text on light wallpapers) when the item has one
                View parent = (View) getParent();
                Object clock = parent == null ? null : XposedHelpers.getObjectField(parent, "mClock");
                if (clock instanceof ViewGroup) {
                    TextView any = firstText((ViewGroup) clock);
                    if (any != null) c = any.getCurrentTextColor() | 0xff000000;
                }
            } catch (Throwable ignored) { }
            setColor(c);
            updateDayAndAlarm();
            place();
        }

        private static TextView firstText(ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View v = g.getChildAt(i);
                if (v instanceof TextView) return (TextView) v;
                if (v instanceof ViewGroup) { TextView t = firstText((ViewGroup) v); if (t != null) return t; }
            }
            return null;
        }

        void setColor(int c) {
            color = c;
            face.setColor(c);
            calendarDay.setTextColor(c); alarmText.setTextColor(c);
            battery.setColor(c);
        }

        void updateDayAndAlarm() {
            calendarDay.setText(String.valueOf(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)));
            String alarm = "Off";
            try {
                AlarmManager am = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
                AlarmManager.AlarmClockInfo next = am == null ? null : am.getNextAlarmClock();
                if (next != null) alarm = DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm", next.getTriggerTime()).toString();
            } catch (Throwable ignored) { }
            alarmText.setText(alarm);
        }

        /** Vertical position: the hours start about 100 dp from the top of the screen (One UI), or the chosen variant. */
        void place() {
            if (!isAttachedToWindow() || getHeight() == 0) return;
            int pos = position;
            if (pos == 3) { if (getTranslationY() != 0) { setTranslationY(0); requestLayout(); } return; }
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            float current = loc[1] - getTranslationY();
            float target = dp(getContext(), pos == 1 ? 72 : pos == 2 ? 150 : 100);
            float t = Math.max(-dp(getContext(), 260), Math.min(dp(getContext(), 260), target - current));
            if (Math.abs(getTranslationY() - t) > 1) { setTranslationY(t); requestLayout(); }
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            current = new java.lang.ref.WeakReference<>(this);
            IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
            f.addAction(Intent.ACTION_DATE_CHANGED);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            try {
                Intent sticky = getContext().registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
                if (sticky != null) receiver.onReceive(getContext(), sticky);
            } catch (Throwable t) { Lx.w("OneUiLock: receiver " + t); }
            refresh();
        }

        @Override protected void onDetachedFromWindow() {
            try { getContext().unregisterReceiver(receiver); } catch (Throwable ignored) { }
            super.onDetachedFromWindow();
        }
    }

    /** Draws the chosen ClockFaces face at the chosen size; height follows the face. */
    static final class FaceView extends View {
        final ClockFaces.Spec spec = new ClockFaces.Spec();
        int faceIndex;

        FaceView(Context c) {
            super(c);
            spec.density = c.getResources().getDisplayMetrics().density;
            configure();
        }

        void configure() {
            faceIndex = Math.max(0, Math.min(ClockFaces.COUNT - 1, Cfg.choice(LockFeatures.ONEUI_FACE)));
            int si = Math.max(0, Math.min(LockFeatures.ONEUI_SIZE_DP.length - 1, Cfg.choice(LockFeatures.ONEUI_SIZE)));
            spec.size = dp(getContext(), LockFeatures.ONEUI_SIZE_DP[si]);
            spec.digits = digitFace();
            requestLayout();
            invalidate();
        }

        void setColor(int c) { if (spec.color != c) { spec.color = c; invalidate(); } }

        private void stamp() {
            spec.time = java.util.Calendar.getInstance();
            spec.h24 = DateFormat.is24HourFormat(getContext());
            spec.locale = Locale.getDefault();
        }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            if (w == 0) w = getResources().getDisplayMetrics().widthPixels;
            stamp();
            int h = (int) Math.ceil(ClockFaces.height(faceIndex, spec, w));
            setMeasuredDimension(w, h);
        }

        @Override protected void onDraw(Canvas c) {
            stamp();
            ClockFaces.draw(c, faceIndex, spec, getWidth());
        }
    }

    /** Battery tile: a 270° ring with the level, a small battery and the number (One UI 8 battery widget). */
    static final class BatteryTile extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int level = 50, color = Color.WHITE;
        private boolean charging;

        BatteryTile(Context c) { super(c); }

        void set(int level, boolean charging) { if (level >= 0) this.level = Math.min(100, level); this.charging = charging; invalidate(); }
        void setColor(int c) { color = c; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            float r = Math.min(w, h) / 2f - 9 * d, cx = w / 2f, cy = h / 2f - 2 * d;
            RectF ring = new RectF(cx - r, cy - r, cx + r, cy + r);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(3.2f * d);
            p.setColor((color & 0x00ffffff) | 0x40000000);
            c.drawArc(ring, 135, 270, false, p);
            p.setColor(charging ? 0xFF3DDC84 : color);
            c.drawArc(ring, 135, 270 * level / 100f, false, p);
            // battery glyph
            float bw = 6.5f * d, bh = 11f * d, by = cy - bh / 2f - 1.5f * d;
            p.setStrokeWidth(1.4f * d);
            p.setColor(color);
            c.drawRoundRect(new RectF(cx - bw / 2, by, cx + bw / 2, by + bh), 1.6f * d, 1.6f * d, p);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(cx - 1.6f * d, by - 1.5f * d, cx + 1.6f * d, by, p);
            float fill = (bh - 2.6f * d) * level / 100f;
            c.drawRect(cx - bw / 2 + 1.3f * d, by + bh - 1.3f * d - fill, cx + bw / 2 - 1.3f * d, by + bh - 1.3f * d, p);
            // number under the ring opening
            p.setTextSize(11 * d);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(String.valueOf(level), cx, cy + r + 3.5f * d, p);
        }
    }

    // ------------------------------------------------------------------ bottom shortcuts

    private static void shortcuts(ViewGroup bottom) {
        if (!Cfg.on(LockFeatures.ONEUI_SHORTCUTS)) return;
        Context ctx = bottom.getContext();
        View left = (View) XposedHelpers.getObjectField(bottom, "mLeftAffordanceView");
        View right = (View) XposedHelpers.getObjectField(bottom, "mRightAffordanceView");
        Object tip = XposedHelpers.getObjectField(bottom, "mSlideNoticeView");
        if (tip instanceof View && ((View) tip).getVisibility() != View.GONE) ((View) tip).setVisibility(View.GONE);
        styleButton(ctx, left, LockGlyph.PHONE, Gravity.START | Gravity.BOTTOM);
        styleButton(ctx, right, LockGlyph.CAMERA, Gravity.END | Gravity.BOTTOM);
    }

    private static final java.util.Map<View, Boolean> STYLED = new java.util.WeakHashMap<>();

    private static void styleButton(Context ctx, View v, int glyph, int gravity) {
        if (!(v instanceof ImageView)) return;
        ImageView iv = (ImageView) v;
        int size = Math.round(dp(ctx, 52)), pad = Math.round(dp(ctx, 14)), margin = Math.round(dp(ctx, 24)), bottom = Math.round(dp(ctx, 26));
        boolean start = (gravity & Gravity.START) == Gravity.START;
        ViewGroup.LayoutParams lp = iv.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams f = (FrameLayout.LayoutParams) lp;
            boolean same = f.width == size && f.height == size && f.gravity == gravity && f.bottomMargin == bottom
                    && (start ? f.getMarginStart() == margin : f.getMarginEnd() == margin);
            if (!same) {
                f.width = size; f.height = size; f.gravity = gravity;
                f.setMargins(0, 0, 0, bottom);
                if (start) f.setMarginStart(margin); else f.setMarginEnd(margin);
                iv.setLayoutParams(f);
            }
        }
        if (STYLED.get(iv) == null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0x47000000);
            iv.setBackground(bg);
            STYLED.put(iv, Boolean.TRUE);
        }
        if (iv.getPaddingLeft() != pad) iv.setPadding(pad, pad, pad, pad);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (!(iv.getDrawable() instanceof LockGlyph)) {
            LockGlyph g = new LockGlyph(glyph, Color.WHITE);
            try { XposedHelpers.callMethod(iv, "setImageDrawable", g, false); } catch (Throwable t) { iv.setImageDrawable(g); }
        }
        iv.setImageTintList(null);
    }
}
