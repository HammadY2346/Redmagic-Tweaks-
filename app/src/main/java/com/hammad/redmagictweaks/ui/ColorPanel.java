package com.hammad.redmagictweaks.ui;

import android.annotation.SuppressLint;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.hammad.redmagictweaks.ColorPick;
import com.hammad.redmagictweaks.R;
import com.hammad.redmagictweaks.UiPrefs;

/**
 * "Custom colour…" in an option sheet: a saturation / brightness square, a hue bar, an alpha bar (where see-through
 * makes sense), a hex field, the wallpaper's colours and the last 8 custom colours as chips, and, where the option has
 * gradients, Solid / Gradient with the two colours.
 */
final class ColorPanel extends LinearLayout {
    interface Listener { void changed(); }

    private final boolean allowGradient, allowAlpha;
    private final Listener listener;
    private final int[] stops = new int[2];
    private boolean gradient;
    private int active;
    private final float[] hsv = new float[3];
    private int alpha = 255;

    private SatVal sv;
    private Bar hue, alphaBar;
    private EditText hex;
    private View swatchA, swatchB;
    private LinearLayout stopRow;
    private boolean busy;

    ColorPanel(Context c, ColorPick.Spec spec, int[] start, Listener listener) {
        super(c);
        this.allowGradient = spec.gradient;
        this.allowAlpha = spec.alpha;
        this.listener = listener;
        setOrientation(VERTICAL);
        int pad = Ui.dp(c, 20);
        setPadding(pad, Ui.dp(c, 4), pad, Ui.dp(c, 4));
        stops[0] = start != null && start.length > 0 ? start[0] : 0xFFFFFFFF;
        stops[1] = start != null && start.length > 1 ? start[1] : stops[0];
        gradient = allowGradient && start != null && start.length > 1;
        if (!allowAlpha) { stops[0] |= 0xFF000000; stops[1] |= 0xFF000000; }
        build(c);
        load();
    }

    private Runnable onDone;
    /** The keyboard's Done on the hex field (the sheet applies). */
    void setOnDone(Runnable r) { onDone = r; }

    /** {c1} or {c1, c2}. */
    int[] value() { return gradient ? new int[]{stops[0], stops[1]} : new int[]{stops[0]}; }

    void remember(Context c) { for (int x : value()) UiPrefs.addRecentColor(c, x); }

    // ============================================================================================ layout

    private void build(Context c) {
        if (allowGradient) {
            MaterialButtonToggleGroup mode = new MaterialButtonToggleGroup(c);
            mode.setSingleSelection(true);
            mode.setSelectionRequired(true);
            MaterialButton solid = outlined(c, "Solid"), grad = outlined(c, "Gradient");
            solid.setId(View.generateViewId()); grad.setId(View.generateViewId());
            mode.addView(solid, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            mode.addView(grad, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            mode.check(gradient ? grad.getId() : solid.getId());
            mode.addOnButtonCheckedListener((g, id, checked) -> {
                if (!checked) return;
                gradient = id == grad.getId();
                if (!gradient) active = 0;
                stopRow.setVisibility(gradient ? VISIBLE : GONE);
                load();
                changed();
            });
            addView(mode, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            stopRow = new LinearLayout(c);
            stopRow.setGravity(Gravity.CENTER_VERTICAL);
            stopRow.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 2));
            swatchA = stopButton(c, 0, "Colour 1");
            swatchB = stopButton(c, 1, "Colour 2");
            stopRow.setVisibility(gradient ? VISIBLE : GONE);
            addView(stopRow);
        }
        sv = new SatVal(c);
        LayoutParams svp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 150));
        svp.topMargin = Ui.dp(c, 8);
        addView(sv, svp);
        hue = new Bar(c, true);
        LayoutParams hp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 30));
        hp.topMargin = Ui.dp(c, 10);
        addView(hue, hp);
        if (allowAlpha) {
            alphaBar = new Bar(c, false);
            LayoutParams ap = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 30));
            ap.topMargin = Ui.dp(c, 8);
            addView(alphaBar, ap);
        }
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 6), 0, 0);
        TextView label = new TextView(c);
        label.setText("Hex");
        label.setTextColor(c.getColor(R.color.text_dim));
        hex = new EditText(c);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setEms(6);
        hex.setSingleLine(true);
        hex.setSelectAllOnFocus(true);
        hex.setHint(allowAlpha ? "#AARRGGBB" : "#RRGGBB");
        hex.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        hex.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_DONE && !(ev != null && ev.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) return false;
            if (ColorPick.parse(hex.getText().toString()) == null) { hex.setError("#RRGGBB"); return true; }
            if (onDone != null) onDone.run();
            return true;
        });
        row.addView(label);
        LayoutParams hxp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        hxp.leftMargin = Ui.dp(c, 10);
        row.addView(hex, hxp);
        addView(row);
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable e) {
                if (busy) return;
                Integer v = ColorPick.parse(e.toString());
                if (v == null) return;
                if (!allowAlpha) v |= 0xFF000000;
                stops[active] = v;
                busy = true;
                fromColor(v);
                sv.invalidate(); hue.invalidate(); if (alphaBar != null) alphaBar.invalidate();
                paintSwatches();
                busy = false;
                changed();
            }
        });
        chips(c);
    }

    private static MaterialButton outlined(Context c, String text) {
        MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private View stopButton(Context c, final int i, String text) {
        LinearLayout box = new LinearLayout(c);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 16), Ui.dp(c, 4));
        View sw = new View(c);
        box.addView(sw, new LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26)));
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(c.getColor(R.color.text));
        t.setPadding(Ui.dp(c, 8), 0, 0, 0);
        box.addView(t);
        box.setOnClickListener(v -> { active = i; load(); });
        stopRow.addView(box);
        return sw;
    }

    /** The wallpaper's colours (an eyedropper on what you see most) and the last 8 custom colours. */
    private void chips(Context c) {
        LinearLayout strip = new LinearLayout(c);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        int n = 0;
        try {
            WallpaperColors wc = WallpaperManager.getInstance(c).getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc != null) {
                for (Color col : new Color[]{wc.getPrimaryColor(), wc.getSecondaryColor(), wc.getTertiaryColor()}) {
                    if (col == null) continue;
                    strip.addView(chip(c, col.toArgb() | 0xFF000000, true));
                    n++;
                }
            }
        } catch (Throwable ignored) { }
        for (int col : UiPrefs.recentColors(c)) { strip.addView(chip(c, allowAlpha ? col : col | 0xFF000000, false)); n++; }
        if (n == 0) return;
        TextView t = new TextView(c);
        t.setText("Wallpaper colours and your recent colours");
        t.setTextColor(c.getColor(R.color.text_dim));
        t.setTextSize(12);
        t.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 2));
        addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(strip);
        addView(hs);
    }

    private View chip(Context c, final int color, boolean wallpaper) {
        View v = new View(c);
        v.setBackground(ColorPick.swatch(c, new int[]{color}, 32));
        v.setContentDescription((wallpaper ? "Wallpaper colour " : "Recent colour ") + ColorPick.hex(color, allowAlpha));
        LayoutParams lp = new LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32));
        lp.rightMargin = Ui.dp(c, 10);
        lp.topMargin = lp.bottomMargin = Ui.dp(c, 4);
        v.setLayoutParams(lp);
        v.setOnClickListener(x -> {
            stops[active] = color;
            load();
            changed();
        });
        return v;
    }

    // ============================================================================================ state

    private void fromColor(int c) {
        Color.colorToHSV(c, hsv);
        alpha = allowAlpha ? (c >>> 24) : 255;
    }

    private int current() { return (Color.HSVToColor(hsv) & 0x00FFFFFF) | (alpha << 24); }

    /** Shows the active colour on every control. */
    private void load() {
        busy = true;
        fromColor(stops[active]);
        hex.setText(ColorPick.hex(stops[active], allowAlpha));
        sv.invalidate(); hue.invalidate(); if (alphaBar != null) alphaBar.invalidate();
        paintSwatches();
        busy = false;
    }

    private void paintSwatches() {
        if (swatchA == null) return;
        swatchA.setBackground(ColorPick.swatch(getContext(), new int[]{stops[0]}, 26));
        swatchB.setBackground(ColorPick.swatch(getContext(), new int[]{stops[1]}, 26));
        swatchA.setAlpha(active == 0 ? 1f : 0.55f);
        swatchB.setAlpha(active == 1 ? 1f : 0.55f);
        swatchA.setScaleX(active == 0 ? 1.15f : 1f); swatchA.setScaleY(active == 0 ? 1.15f : 1f);
        swatchB.setScaleX(active == 1 ? 1.15f : 1f); swatchB.setScaleY(active == 1 ? 1.15f : 1f);
    }

    /** A control moved: store the colour and tell the sheet. */
    private void edited() {
        stops[active] = current();
        busy = true;
        hex.setText(ColorPick.hex(stops[active], allowAlpha));
        busy = false;
        paintSwatches();
        sv.invalidate(); hue.invalidate(); if (alphaBar != null) alphaBar.invalidate();
        changed();
    }

    private void changed() { if (listener != null) listener.changed(); }

    // ============================================================================================ controls

    /** Saturation (left to right) and brightness (bottom to top) of the current hue. */
    private final class SatVal extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        SatVal(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Ui.dp(c, 2.5f));
        }
        @Override protected void onDraw(Canvas canvas) {
            float rad = Ui.dp(getContext(), 14);
            r.set(0, 0, getWidth(), getHeight());
            int pure = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});
            p.setShader(new LinearGradient(0, 0, getWidth(), 0, 0xFFFFFFFF, pure, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, rad, rad, p);
            p.setShader(new LinearGradient(0, 0, 0, getHeight(), 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
            float x = hsv[1] * getWidth(), y = (1f - hsv[2]) * getHeight();
            float tr = Ui.dp(getContext(), 10);
            p.setColor(Color.HSVToColor(hsv));
            canvas.drawCircle(x, y, tr, p);
            ring.setColor(hsv[2] > 0.6f && hsv[1] < 0.4f ? 0xFF202024 : 0xFFFFFFFF);
            canvas.drawCircle(x, y, tr, ring);
        }
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            hsv[1] = clamp01(e.getX() / Math.max(1, getWidth()));
            hsv[2] = clamp01(1f - e.getY() / Math.max(1, getHeight()));
            edited();
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
    }

    /** The hue bar (a rainbow) or the alpha bar (see-through to solid over a checkerboard). */
    private final class Bar extends View {
        private final boolean isHue;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), check = new Paint();
        private final RectF r = new RectF();
        Bar(Context c, boolean isHue) {
            super(c);
            this.isHue = isHue;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Ui.dp(c, 2.5f));
            ring.setColor(0xFFFFFFFF);
            setContentDescription(isHue ? "Hue" : "Opacity");
        }
        @Override protected void onDraw(Canvas canvas) {
            float h = getHeight(), pad = h / 2f;
            r.set(0, h * 0.2f, getWidth(), h * 0.8f);
            float rad = r.height() / 2f;
            if (isHue) {
                int[] cols = new int[7];
                for (int i = 0; i < 7; i++) cols[i] = Color.HSVToColor(new float[]{i * 60f % 360f, 1f, 1f});
                cols[6] = Color.HSVToColor(new float[]{359.9f, 1f, 1f});
                p.setShader(new LinearGradient(pad, 0, getWidth() - pad, 0, cols, null, Shader.TileMode.CLAMP));
            } else {
                int save = canvas.save();
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(r, rad, rad, android.graphics.Path.Direction.CW);
                canvas.clipPath(clip);
                float s = r.height() / 2f;
                for (float x = 0; x < getWidth(); x += s) for (float y = r.top; y < r.bottom; y += s) {
                    check.setColor((((int) (x / s) + (int) ((y - r.top) / s)) & 1) == 0 ? 0xFFBDBDBD : 0xFF7A7A7A);
                    canvas.drawRect(x, y, x + s, y + s, check);
                }
                canvas.restoreToCount(save);
                int solid = Color.HSVToColor(hsv);
                p.setShader(new LinearGradient(pad, 0, getWidth() - pad, 0, solid & 0x00FFFFFF, solid | 0xFF000000, Shader.TileMode.CLAMP));
            }
            canvas.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
            float frac = isHue ? hsv[0] / 360f : alpha / 255f;
            float x = pad + frac * (getWidth() - 2 * pad);
            p.setColor(isHue ? Color.HSVToColor(new float[]{hsv[0], 1f, 1f}) : current());
            canvas.drawCircle(x, h / 2f, h * 0.38f, p);
            canvas.drawCircle(x, h / 2f, h * 0.38f, ring);
        }
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float pad = getHeight() / 2f;
            float f = clamp01((e.getX() - pad) / Math.max(1, getWidth() - 2 * pad));
            if (isHue) hsv[0] = Math.min(359.9f, f * 360f); else alpha = Math.round(f * 255);
            edited();
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
    }

    private static float clamp01(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
}
