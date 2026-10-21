package com.hammad.redmagictweaks.xposed;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.hammad.redmagictweaks.ClockFeatures;
import com.hammad.redmagictweaks.StatusBarFeatures;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * com.android.systemui — status bar clock typography / colour / date / visibility, ported from nubia-ly's
 * ClockStyleBackend, ClockColorBackend, ClockDateBackend and ClockVisibilityBackend. nubia-ly installs
 * temporary setter hooks for a 30-second preview; we instead re-apply on the clock's own callbacks
 * (updateClock every tick, reloadDimens, onDarkChanged, getSmallTime) so every change lands within a minute
 * and stock values are captured from the view itself before they are overridden.
 * Only the clock in the top status bar is touched (ClockAdapt.mViewType.isTopStatusBar()), as in nubia-ly.
 *
 * Targets (firmware 11.0.11 decompile, com/android/systemui/statusbar/policy/Clock.java):
 *   public final void updateClock()  (:557)      private final CharSequence getSmallTime()  (:212)
 *   private void reloadDimens()  (:277)          public void onDarkChanged(ArrayList<Rect>, float, int)  (:398)
 *   public void setVisibility(int)  (:545)       fields: public Calendar mCalendar (:56), public Locale mLocale (:67),
 *   public int mNonAdaptedColor (set in onDarkChanged).
 */
final class ClockHooks {
    private ClockHooks() {}

    private static final String CLOCK = "com.android.systemui.statusbar.policy.Clock";

    // ------------------------------------------------------------------------------------------- settings snapshot

    static final class S {
        int font, size, spacing, color, date, datePos, dateSep, dateCase, offX, offY;
        boolean bold, italic, hide, customFormat;
        int hash;
        boolean typography() { return font > 0 || bold || italic; }
    }
    private static volatile S snap = new S();
    private static volatile long snapAt;

    static S s() {
        long now = System.currentTimeMillis();
        if (now - snapAt > 1000) {
            S n = new S();
            n.font = idx(Cfg.choice(ClockFeatures.CLK_FONT), ClockFeatures.FONT_DEFS.length);
            n.bold = Cfg.on(ClockFeatures.CLK_BOLD);
            n.italic = Cfg.on(ClockFeatures.CLK_ITALIC);
            n.color = idx(Cfg.choice(ClockFeatures.CLK_COLOR), ClockFeatures.COLOR_VALUES.length);
            n.size = idx(Cfg.choice(ClockFeatures.CLK_SIZE), ClockFeatures.SIZE_PERCENT.length);
            n.spacing = idx(Cfg.choice(ClockFeatures.CLK_SPACING), ClockFeatures.SPACING_HUNDREDTHS.length);
            n.date = idx(Cfg.choice(ClockFeatures.CLK_DATE), ClockFeatures.DATE_PATTERNS.length);
            n.datePos = idx(Cfg.choice(ClockFeatures.CLK_DATE_POS), ClockFeatures.DATE_POSITIONS.length);
            n.dateSep = idx(Cfg.choice(ClockFeatures.CLK_DATE_SEP), ClockFeatures.DATE_SEPARATOR_TEXT.length);
            n.dateCase = idx(Cfg.choice(ClockFeatures.CLK_DATE_CASE), ClockFeatures.DATE_CASES.length);
            n.offX = idx(Cfg.choice(ClockFeatures.CLK_OFFSET_X), ClockFeatures.OFFSETS_X_DP.length);
            n.offY = idx(Cfg.choice(ClockFeatures.CLK_OFFSET_Y), ClockFeatures.OFFSETS_Y_DP.length);
            n.hide = Cfg.on(ClockFeatures.CLK_HIDE);
            n.customFormat = Cfg.choice(StatusBarFeatures.SB_CLOCK_FORMAT) > 0;
            n.hash = (n.font * 31 + n.size) * 31 + n.spacing; n.hash = n.hash * 31 + n.color; n.hash = n.hash * 31 + n.date; n.hash = n.hash * 31 + n.datePos;
            n.hash = n.hash * 31 + n.dateSep; n.hash = n.hash * 31 + n.dateCase; n.hash = n.hash * 31 + n.offX; n.hash = n.hash * 31 + n.offY;
            n.hash = n.hash * 31 + (n.bold ? 1 : 0) + (n.italic ? 2 : 0) + (n.hide ? 4 : 0) + (n.customFormat ? 8 : 0);
            snap = n; snapAt = now;
        }
        return snap;
    }

    private static int idx(int v, int len) { return v > 0 && v < len ? v : 0; }

    /** Stock values captured from the view, plus what we last wrote so later stock writes can be told apart. */
    private static final class Stock {
        float px = -1, appliedPx = -1;
        Typeface face, appliedFace; boolean faceApplied;
        float spacing, appliedSpacing; boolean spacingApplied;
        float tx, ty; boolean offsetApplied;
        boolean colorApplied, hidden;
        int lastHash = Integer.MIN_VALUE;
    }
    private static final Map<TextView, Stock> stocks = new WeakHashMap<>();
    /** Guards against our own setters re-entering the hooks. */
    private static boolean writing;

    // ------------------------------------------------------------------------------------------- install

    static void install(ClassLoader cl) {
        Class<?> clock = H.cls(cl, CLOCK);
        if (clock == null) return;
        // private void reloadDimens(): the vendor re-applies the stock text size — capture it, then re-scale.
        H.hook(H.find(clock, "reloadDimens"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    if (!StatusBarHooks.isTopStatusBarClock(p.thisObject)) return;
                    TextView v = (TextView) p.thisObject;
                    Stock st = stock(v);
                    st.px = v.getTextSize(); st.appliedPx = -1;
                    apply(v, st, s());
                } catch (Throwable t) { Lx.e("Clock: reloadDimens", t); }
            }
        });
        // public final void updateClock(): every tick (each minute, or each second with seconds on).
        H.hook(H.find(clock, "updateClock"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (writing) return;
                try {
                    if (!StatusBarHooks.isTopStatusBarClock(p.thisObject)) return;
                    TextView v = (TextView) p.thisObject;
                    S s = s();
                    apply(v, stock(v), s);
                    if (s.date > 0 && !s.customFormat) {
                        CharSequence d = v.getContentDescription();
                        if (d != null && d.length() > 0) v.setContentDescription(compose(d, dateText(v, s), s));
                    }
                    IconPackHooks.tick(v);
                } catch (Throwable t) { Lx.e("Clock: tick", t); }
            }
        });
        // private final CharSequence getSmallTime(): stock formatter — append / prepend the date, keep its spans.
        H.hook(H.find(clock, "getSmallTime"), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    S s = s();
                    if (s.date <= 0 || s.customFormat || p.hasThrowable()) return;
                    if (!(p.getResult() instanceof CharSequence) || !StatusBarHooks.isTopStatusBarClock(p.thisObject)) return;
                    TextView v = (TextView) p.thisObject;
                    p.setResult(compose((CharSequence) p.getResult(), dateText(v, s), s));
                } catch (Throwable t) { Lx.e("Clock: date", t); }
            }
        });
        // public void onDarkChanged(ArrayList<Rect>, float, int): stock writes its tint; put our colour back on top.
        H.hook(H.find(clock, "onDarkChanged", ArrayList.class, float.class, int.class), new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try {
                    S s = s();
                    if (s.color <= 0 || !StatusBarHooks.isTopStatusBarClock(p.thisObject)) return;
                    TextView v = (TextView) p.thisObject;
                    paintColor(v, stock(v), s);
                } catch (Throwable t) { Lx.e("Clock: dark", t); }
            }
        });
        // public void setVisibility(int): keep a hidden clock hidden whatever the stock policy decides.
        H.hook(H.find(clock, "setVisibility", int.class), new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    if (writing || !s().hide) return;
                    if (!StatusBarHooks.isTopStatusBarClock(p.thisObject)) return;
                    if (p.args[0] instanceof Integer && (Integer) p.args[0] != View.GONE) p.args[0] = View.GONE;
                } catch (Throwable ignored) { }
            }
        });
        Lx.i("Clock: hooks ready");
    }

    private static Stock stock(TextView v) {
        synchronized (stocks) {
            Stock st = stocks.get(v);
            if (st == null) {
                st = new Stock();
                st.px = v.getTextSize(); st.face = v.getTypeface(); st.spacing = v.getLetterSpacing();
                st.tx = v.getTranslationX(); st.ty = v.getTranslationY();
                stocks.put(v, st);
            }
            return st;
        }
    }

    // ------------------------------------------------------------------------------------------- apply

    /** Re-applies every setting; cheap when nothing changed (compares against what we last wrote). */
    private static void apply(TextView v, Stock st, S s) {
        writing = true;
        try {
            // size: a stock write we did not make (reloadDimens, font scale) replaces the baseline
            float cur = v.getTextSize();
            if (st.appliedPx >= 0 && Math.abs(cur - st.appliedPx) > 0.01f) { st.px = cur; st.appliedPx = -1; }
            else if (st.appliedPx < 0) st.px = cur;
            float want = s.size > 0 ? st.px * ClockFeatures.SIZE_PERCENT[s.size] / 100f : st.px;
            if (Math.abs(cur - want) > 0.01f) { v.setTextSize(TypedValue.COMPLEX_UNIT_PX, want); st.appliedPx = s.size > 0 ? want : -1; }
            else st.appliedPx = s.size > 0 ? want : -1;

            // typeface
            Typeface curFace = v.getTypeface();
            if (st.faceApplied && !same(curFace, st.appliedFace)) { st.face = curFace; st.faceApplied = false; }
            else if (!st.faceApplied) st.face = curFace;
            if (s.typography()) {
                int style = (st.face == null ? 0 : st.face.getStyle()) | (s.bold ? Typeface.BOLD : 0) | (s.italic ? Typeface.ITALIC : 0);
                Typeface desired;
                if (s.font > 0) {
                    String[] d = ClockFeatures.FONT_DEFS[s.font];
                    desired = Typeface.create(d[1], Integer.parseInt(d[2]) | style);
                } else desired = Typeface.create(st.face, style);
                if (!same(curFace, desired)) { v.setTypeface(desired); v.getPaint().setFakeBoldText(false); v.getPaint().setTextSkewX(0); }
                st.appliedFace = v.getTypeface(); st.faceApplied = true;
            } else if (st.faceApplied) {
                v.setTypeface(st.face); st.faceApplied = false; st.appliedFace = null;
            }

            // letter spacing
            float curSp = v.getLetterSpacing();
            if (st.spacingApplied && Math.abs(curSp - st.appliedSpacing) > 0.00001f) { st.spacing = curSp; st.spacingApplied = false; }
            else if (!st.spacingApplied) st.spacing = curSp;
            if (s.spacing > 0) {
                float wantSp = st.spacing + ClockFeatures.SPACING_HUNDREDTHS[s.spacing] / 100f;
                if (Math.abs(curSp - wantSp) > 0.00001f) v.setLetterSpacing(wantSp);
                st.appliedSpacing = v.getLetterSpacing(); st.spacingApplied = true;
            } else if (st.spacingApplied) { v.setLetterSpacing(st.spacing); st.spacingApplied = false; }

            // position nudges
            if (s.offX > 0 || s.offY > 0) {
                float density = v.getResources().getDisplayMetrics().density;
                if (!st.offsetApplied) { st.tx = v.getTranslationX(); st.ty = v.getTranslationY(); }
                float tx = st.tx + ClockFeatures.OFFSETS_X_DP[s.offX] * density, ty = st.ty + ClockFeatures.OFFSETS_Y_DP[s.offY] * density;
                if (Math.abs(v.getTranslationX() - tx) > 0.01f) v.setTranslationX(tx);
                if (Math.abs(v.getTranslationY() - ty) > 0.01f) v.setTranslationY(ty);
                st.offsetApplied = true;
            } else if (st.offsetApplied) { v.setTranslationX(st.tx); v.setTranslationY(st.ty); st.offsetApplied = false; }

            // colour
            if (s.color > 0) paintColor(v, st, s);
            else if (st.colorApplied) {
                st.colorApplied = false;
                try {
                    int stockColor = XposedHelpers.getIntField(v, "mNonAdaptedColor");
                    if (stockColor != 0) v.setTextColor(stockColor);
                } catch (Throwable t) { Lx.w("Clock: mNonAdaptedColor missing: " + t); }
            }

            // visibility
            if (s.hide) {
                if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
                st.hidden = true;
            } else if (st.hidden) {
                st.hidden = false;
                try { XposedHelpers.callMethod(v, "setVisibility", View.VISIBLE); } catch (Throwable t) { v.setVisibility(View.VISIBLE); }
            }
            if (st.lastHash != s.hash) { st.lastHash = s.hash; v.requestLayout(); }
        } finally { writing = false; }
    }

    private static void paintColor(TextView v, Stock st, S s) {
        int color = ClockFeatures.COLOR_VALUES[s.color];
        if (v.getCurrentTextColor() != color) {
            boolean was = writing; writing = true;
            try { v.setTextColor(color); } finally { writing = was; }
        }
        st.colorApplied = true;
    }

    private static boolean same(Typeface a, Typeface b) { return a == null ? b == null : a.equals(b); }

    // ------------------------------------------------------------------------------------------- date

    private static final class DateCache { String text; int era, year, day, mode; Locale locale; }
    private static final Map<TextView, DateCache> dates = new WeakHashMap<>();

    /** nubia-ly ClockDatePolicy.format with the clock's own calendar (time zone) and locale, cached per day. */
    private static String dateText(TextView v, S s) {
        Calendar cal = null; Locale locale = null;
        try { cal = (Calendar) XposedHelpers.getObjectField(v, "mCalendar"); } catch (Throwable ignored) { }
        try { locale = (Locale) XposedHelpers.getObjectField(v, "mLocale"); } catch (Throwable ignored) { }
        if (cal == null) cal = Calendar.getInstance();
        if (locale == null) locale = Locale.getDefault();
        int mode = s.date * 4 + s.dateCase;
        int era = cal.get(Calendar.ERA), year = cal.get(Calendar.YEAR), day = cal.get(Calendar.DAY_OF_YEAR);
        DateCache c;
        synchronized (dates) { c = dates.get(v); if (c == null) { c = new DateCache(); dates.put(v, c); } }
        if (c.text == null || c.mode != mode || c.era != era || c.year != year || c.day != day || !locale.equals(c.locale)) {
            SimpleDateFormat f = new SimpleDateFormat(ClockFeatures.DATE_PATTERNS[s.date], locale);
            f.setTimeZone(cal.getTimeZone());
            String value = f.format(cal.getTime());
            if (s.dateCase == 1) value = value.toUpperCase(locale); else if (s.dateCase == 2) value = value.toLowerCase(locale);
            c.text = value; c.mode = mode; c.era = era; c.year = year; c.day = day; c.locale = locale;
        }
        return c.text;
    }

    /** Appending the original CharSequence keeps the stock time spans in either position. */
    private static CharSequence compose(CharSequence stock, String date, S s) {
        String sep = ClockFeatures.DATE_SEPARATOR_TEXT[s.dateSep];
        return s.datePos == 0 ? new SpannableStringBuilder(stock).append(sep).append(date)
                : new SpannableStringBuilder(date).append(sep).append(stock);
    }
}
