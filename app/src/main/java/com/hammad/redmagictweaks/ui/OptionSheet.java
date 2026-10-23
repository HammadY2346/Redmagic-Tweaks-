package com.hammad.redmagictweaks.ui;

import android.app.Activity;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.hammad.redmagictweaks.Catalog;
import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.LookPreview;
import com.hammad.redmagictweaks.Looks;
import com.hammad.redmagictweaks.Previews;
import com.hammad.redmagictweaks.R;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Choosing an option: a bottom sheet with the preview on top that redraws as you tap options (the whole screen when the
 * category has a picture of it, else the option's own artwork, animated when it moves), a picture grid for visual
 * options or a list for the rest. Nothing is saved until Apply.
 */
final class OptionSheet {
    interface Result { void done(int value, boolean changed); }

    private final Activity a;
    private final Catalog.Item item;
    /** Position of the "Custom value" row (after the presets), or -1 when the setting has no Features.Num. */
    private final int customIndex;
    private final boolean hadNum;
    private int numVal;
    private android.widget.LinearLayout numPanel;
    /** Position of the "Custom colour…" row (after the presets) for colour options (ColorPick), else -1. */
    private final int colorIndex;
    private final boolean hadColor;
    private final int[] startColor;
    private ColorPanel colorPanel;
    private final String screenCategory;
    private final Result result;
    private final int current;
    private int sel;
    private final Previews.Provider provider;
    private final Previews.FrameProvider frames;
    private final Map<Integer, Drawable> cache = new HashMap<>();
    /** The options this build offers, in order (the public build hides the One UI looks): list position -> index. */
    private final int[] vis;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private ImageView preview;
    private RecyclerView list;
    private int animToken;

    /** @param screenCategory category whose screens to draw with the option applied, or null for the option's own artwork */
    OptionSheet(Activity a, Catalog.Item item, String screenCategory, Result result) {
        this.a = a; this.item = item; this.screenCategory = screenCategory; this.result = result;
        Features.Num n = item.feature.num;
        customIndex = n == null ? -1 : item.feature.choices.length;
        hadNum = Catalog.hasNum(a, item);
        if (n != null) {
            int mid = n.min <= 0 && n.max >= 0 ? 0 : n.min + ((n.max - n.min) / 2 / n.step) * n.step;
            numVal = hadNum ? Catalog.num(a, item) : mid;
        }
        colorIndex = n == null && item.feature.isColor() ? item.feature.choices.length : -1;
        hadColor = colorIndex >= 0 && Catalog.hasColor(a, item);
        startColor = hadColor ? Catalog.color(a, item) : startFromPreset();
        current = hadNum ? customIndex : hadColor ? colorIndex : Catalog.choice(a, item);
        sel = current;
        int[] v = new int[item.feature.choices.length];
        int k = 0;
        for (int i = 0; i < v.length; i++) if (item.feature.offered(i)) v[k++] = i;
        vis = java.util.Arrays.copyOf(v, k);
        provider = Previews.of(item.key());
        frames = Previews.frames(item.key());
    }

    void show() {
        final BottomSheetDialog d = new BottomSheetDialog(a);
        View v = LayoutInflater.from(a).inflate(R.layout.sheet_options, null);
        ((TextView) v.findViewById(R.id.sheet_title)).setText(item.feature.title);
        TextView sub = v.findViewById(R.id.sheet_sub);
        String hint = Previews.hint(item.key());
        sub.setText(hint != null ? hint : screenCategory != null || provider != null ? "Tap an option to see it here. Nothing changes until you apply it." : "Nothing changes until you apply it.");
        preview = v.findViewById(R.id.sheet_preview);
        list = v.findViewById(R.id.sheet_list);
        boolean grid = provider != null;
        list.setLayoutManager(grid ? new GridLayoutManager(a, 3) : new LinearLayoutManager(a));
        list.setAdapter(grid ? new GridAdapter() : new ListAdapter());
        list.scrollToPosition(Math.max(0, posOf(sel) - (grid ? 3 : 2)));
        dialog = d;
        if (customIndex >= 0) addNumPanel(v);
        if (colorIndex >= 0) addColorPanel();
        v.findViewById(R.id.sheet_cancel).setOnClickListener(x -> d.dismiss());
        v.findViewById(R.id.sheet_apply).setOnClickListener(x -> apply());
        Ui.fonts(v);
        d.setContentView(v);
        d.setOnDismissListener(x -> { animToken++; pool.shutdownNow(); });
        BottomSheetBehavior<?> b = d.getBehavior();
        b.setSkipCollapsed(true);
        b.setState(BottomSheetBehavior.STATE_EXPANDED);
        keepFooterAboveKeyboard(d, v);
        // the list scrolls inside a sheet that may take most of the screen
        listHeight();
        fitPreview();
        drawPreview();
        d.show();
    }

    private BottomSheetDialog dialog;
    private boolean imeOpen;

    /**
     * Cancel / Apply stay pinned under the scrolling content (sheet_options.xml). While the keyboard is open (a typed
     * value or a hex code) the sheet is padded by the part of the keyboard that covers it, so the buttons sit right above
     * the keyboard; the preview shrinks meanwhile.
     */
    private void keepFooterAboveKeyboard(BottomSheetDialog d, final View root) {
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        final int basePad = root.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;
            boolean open = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) && ime > 0;
            int pad = 0;
            if (open) {
                // the sheet's bottom may already sit above the bottom of the window (navigation-bar padding, or the
                // dialog moved up by the system): only the part of the keyboard below the sheet's bottom is padded
                int[] loc = new int[2];
                view.getLocationInWindow(loc);
                int below = Math.max(0, view.getRootView().getHeight() - (loc[1] + view.getHeight()));
                pad = Math.max(0, ime - below);
            }
            if (view.getPaddingBottom() != basePad + pad) view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), basePad + pad);
            if (open != imeOpen) {
                imeOpen = open;
                fitPreview();
                if (open) {
                    View f = view.findFocus();
                    if (f != null) view.post(() -> { android.graphics.Rect r = new android.graphics.Rect(); f.getDrawingRect(r); f.requestRectangleOnScreen(r, false); });
                }
            }
            return insets;
        });
    }

    /**
     * The preview phones give way when space is short: smaller in landscape, on short screens, while a custom value or
     * colour panel is open, and smallest while the keyboard is up.
     */
    private void fitPreview() {
        android.util.DisplayMetrics dm = a.getResources().getDisplayMetrics();
        float hDp = dm.heightPixels / dm.density;
        boolean land = a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        boolean panel = sel == customIndex && customIndex >= 0 || sel == colorIndex && colorIndex >= 0;
        int maxDp = imeOpen ? 90 : land ? 120 : hDp < 680 ? 160 : panel ? (hDp < 860 ? 170 : 220) : hDp < 800 ? 240 : 300;
        preview.setMaxHeight(Ui.dp(a, maxDp));
        ViewGroup.LayoutParams lp = preview.getLayoutParams();
        if (lp.height > 0 && lp.height > Ui.dp(a, maxDp)) { lp.height = Ui.dp(a, maxDp); preview.setLayoutParams(lp); }
        else if (lp.height > 0 && big && lp.height < Ui.dp(a, Math.min(180, maxDp))) { lp.height = Ui.dp(a, Math.min(180, maxDp)); preview.setLayoutParams(lp); }
    }

    /** Saves what is selected (preset, typed value or custom colour) and closes the sheet. */
    private void apply() {
        BottomSheetDialog d = dialog;
        if (d != null) d.dismiss();
        {
            if (customIndex >= 0 && sel == customIndex) {
                boolean changed = !hadNum || Catalog.num(a, item) != numVal;
                Catalog.setNum(a, item, numVal);
                result.done(Catalog.choice(a, item), changed);
            } else if (colorIndex >= 0 && sel == colorIndex) {
                int[] c = colorPanel.value();
                boolean changed = !hadColor || !java.util.Arrays.equals(c, Catalog.color(a, item));
                Catalog.setColor(a, item, c);
                colorPanel.remember(a);
                int proxy = com.hammad.redmagictweaks.ColorPick.proxy(item.key(), c[0], c.length > 1);
                result.done(proxy, changed || proxy != Catalog.choice(a, item));
            } else {
                Catalog.clearNum(a, item);
                if (colorIndex >= 0) Catalog.clearColor(a, item);
                result.done(sel, sel != current || hadNum || hadColor);
            }
        }
    }

    /** The list takes up to a third (pictures) or half of the screen; less while the colour picker is open. */
    private void listHeight() {
        boolean grid = provider != null;
        int maxList = Math.round(a.getResources().getDisplayMetrics().heightPixels * (screenCategory != null || provider != null ? 0.34f : 0.5f));
        boolean panel = colorPanel != null && sel == colorIndex || numPanel != null && sel == customIndex;
        if (panel) maxList = Math.min(maxList, grid ? Ui.dp(a, 150) : Ui.dp(a, 3 * 52 + 8));
        int h = Math.min(maxList, estimatedListHeight(grid));
        if (list.getLayoutParams().height != h) { list.getLayoutParams().height = h; list.requestLayout(); }
        if (panel) list.scrollToPosition(posOf(sel));
    }

    /** Slider + typed number for the "Custom value" row (Features.Num). */
    private void addNumPanel(View sheet) {
        final Features.Num n = item.feature.num;
        ViewGroup parent = (ViewGroup) list.getParent();
        numPanel = new android.widget.LinearLayout(a);
        numPanel.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 20);
        numPanel.setPadding(pad, Ui.dp(a, 4), pad, 0);
        final com.google.android.material.slider.Slider slider = new com.google.android.material.slider.Slider(a);
        slider.setValueFrom(n.min);
        slider.setValueTo(n.max);
        slider.setStepSize(n.step);
        final android.widget.EditText field = new android.widget.EditText(a);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | (n.min < 0 ? android.text.InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
        field.setEms(4);
        field.setSelectAllOnFocus(true);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        field.setSingleLine(true);
        field.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_DONE && !(ev != null && ev.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) return false;
            if (field.getError() != null) return true;               // out of range: stay, the error shows why
            if (sel != customIndex) select(customIndex);
            apply();
            return true;
        });
        TextView unit = new TextView(a);
        unit.setText(n.unit == null ? "" : n.unit);
        unit.setTextColor(a.getColor(R.color.text_dim));
        TextView range = new TextView(a);
        range.setText("Any value from " + n.format(n.min) + " to " + n.format(n.max));
        range.setTextColor(a.getColor(R.color.text_dim));
        range.setTextSize(12);
        android.widget.LinearLayout row = new android.widget.LinearLayout(a);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(field);
        row.addView(unit);
        TextView space = new TextView(a);
        row.addView(space, new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(range);
        numPanel.addView(slider);
        numPanel.addView(row);
        final boolean[] busy = {false};
        final Runnable show = () -> {
            busy[0] = true;
            float sv = Math.max(n.min, Math.min(n.max, numVal));
            sv = n.min + Math.round((sv - n.min) / n.step) * n.step;
            slider.setValue(Math.max(n.min, Math.min(n.max, sv)));
            String t = String.valueOf(numVal);
            if (!t.equals(field.getText().toString())) field.setText(t);
            busy[0] = false;
        };
        show.run();
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (busy[0] || !fromUser) return;
            numVal = Math.round(value);
            busy[0] = true; field.setText(String.valueOf(numVal)); busy[0] = false;
            if (sel != customIndex) select(customIndex); else drawPreview();
        });
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (busy[0]) return;
                try {
                    int v = Integer.parseInt(e.toString().trim());
                    if (v < n.min || v > n.max) { field.setError(n.format(n.min) + " – " + n.format(n.max)); return; }
                    numVal = v;
                    busy[0] = true;
                    slider.setValue(Math.max(n.min, Math.min(n.max, n.min + Math.round((float) (v - n.min) / n.step) * n.step)));
                    busy[0] = false;
                    if (sel != customIndex) select(customIndex); else drawPreview();
                } catch (NumberFormatException ignored) { }
            }
        });
        parent.addView(numPanel, parent.indexOfChild(list) + 1);
        numPanel.setVisibility(sel == customIndex ? View.VISIBLE : View.GONE);
        Ui.fonts(numPanel);
    }

    private String label(int pos) {
        if (pos == customIndex) return "Custom value…";
        if (pos == colorIndex) return hadColor || colorPanel != null ? "Custom colour · " + com.hammad.redmagictweaks.ColorPick.label(pickedColor(), com.hammad.redmagictweaks.ColorPick.spec(item.key()).alpha) : "Custom colour…";
        return item.feature.choices[pos];
    }

    /** The picker starts from the current custom colour, else the chosen preset's colour, else white. */
    private int[] startFromPreset() {
        try {
            int[] c = com.hammad.redmagictweaks.ColorPick.preset(a, item.key(), Catalog.choice(a, item));
            if (c != null) return c;
        } catch (Throwable ignored) { }
        return new int[]{0xFFFFFFFF};
    }

    /** Values for the previews with the picked custom colour (and the nearest preset as the choice). */
    private Object[][] colorValues(int[] c) {
        String k = item.key();
        return new Object[][]{{k + com.hammad.redmagictweaks.ColorPick.ON, Boolean.TRUE}, {k + com.hammad.redmagictweaks.ColorPick.C1, c[0]},
                {k + com.hammad.redmagictweaks.ColorPick.GRAD, c.length > 1}, {k + com.hammad.redmagictweaks.ColorPick.C2, c.length > 1 ? c[1] : c[0]},
                {k + Features.CUSTOM, Boolean.FALSE}, {k, com.hammad.redmagictweaks.ColorPick.proxy(k, c[0], c.length > 1)}};
    }

    /** The colour picker under the list (shown while "Custom colour…" is selected). */
    private void addColorPanel() {
        ViewGroup parent = (ViewGroup) list.getParent();
        colorPanel = new ColorPanel(a, com.hammad.redmagictweaks.ColorPick.spec(item.key()), startColor, () -> {
            if (sel != colorIndex) select(colorIndex); else { drawPreview(); refreshColorRow(); }
        });
        colorPanel.setOnDone(this::apply);
        parent.addView(colorPanel, parent.indexOfChild(list) + 1);
        colorPanel.setVisibility(sel == colorIndex ? View.VISIBLE : View.GONE);
        Ui.fonts(colorPanel);
    }

    private void refreshColorRow() {
        RecyclerView.Adapter<?> ad = list.getAdapter();
        if (ad != null && colorIndex >= 0) ad.notifyItemChanged(posOf(colorIndex));
    }

    private int[] pickedColor() { return colorPanel != null ? colorPanel.value() : startColor; }

    /** Rows in the list: the offered options, then "Custom value…" / "Custom colour…". */
    private int rows() { return vis.length + (customIndex >= 0 || colorIndex >= 0 ? 1 : 0); }

    /** Option index of list row {@code pos}. */
    private int indexAt(int pos) { return pos >= 0 && pos < vis.length ? vis[pos] : customIndex >= 0 ? customIndex : colorIndex; }

    /** List row of option {@code index}. */
    private int posOf(int index) {
        if (index == customIndex && customIndex >= 0 || index == colorIndex && colorIndex >= 0) return vis.length;
        for (int p = 0; p < vis.length; p++) if (vis[p] == index) return p;
        return 0;
    }

    private int estimatedListHeight(boolean grid) {
        int n = rows();
        return grid ? ((n + 2) / 3) * Ui.dp(a, 150) : n * Ui.dp(a, 52) + Ui.dp(a, 8);
    }

    /** The big picture for the selected option. */
    private void drawPreview() {
        final int token = ++animToken;
        if (screenCategory != null) {
            Looks.V picked = sel == customIndex
                    ? Looks.with(Looks.live(a), new Object[][]{{item.key() + Features.CUSTOM, Boolean.TRUE}, {item.key() + Features.VALUE, numVal}})
                    : sel == colorIndex ? Looks.with(Looks.live(a), colorValues(pickedColor()))
                    : Looks.with(Looks.with(Looks.live(a), item.key(), sel), item.key() + Features.CUSTOM, Boolean.FALSE);
            String parent = Catalog.needs(item.key());
            final Looks.V with = parent == null ? picked : Looks.with(picked, parent, Boolean.TRUE);   // show the option, even while its switch is off
            String[] s = LookPreview.screensFor(screenCategory);
            int w = Math.max(240, a.getResources().getConfiguration().screenWidthDp - 48);
            preview.setImageDrawable(s != null && s.length == 1 && LookPreview.BAR.equals(s[0]) ? LookPreview.strip(a, with, w) : LookPreview.section(a, screenCategory, with, w));
            preview.setOnClickListener(x -> PreviewDialogs.showAll(a, sel == customIndex ? item.feature.num.format(numVal) : sel == colorIndex ? label(sel) : item.feature.choices[sel], with));
            preview.setVisibility(View.VISIBLE);
            return;
        }
        if (sel == colorIndex) {
            preview.setVisibility(View.VISIBLE);
            preview.setOnClickListener(null);
            setBig();
            preview.setImageDrawable(com.hammad.redmagictweaks.ColorPick.swatch(a, pickedColor(), 150));
            return;
        }
        if (provider == null) { preview.setVisibility(View.GONE); return; }
        preview.setVisibility(View.VISIBLE);
        preview.setOnClickListener(null);
        final int index = sel == customIndex ? Catalog.choice(a, item) : sel;
        Drawable still = cache.get(index);
        preview.setImageDrawable(still);
        setBig();
        pool.execute(() -> {
            Drawable dr = still;
            if (dr == null) { try { dr = provider.get(a.getApplicationContext(), index); } catch (Throwable ignored) { } }
            Drawable[] fr = null;
            if (frames != null) { try { fr = frames.get(a.getApplicationContext(), index); } catch (Throwable ignored) { } }
            final Drawable res = dr;
            final Drawable[] fs = fr;
            main.post(() -> {
                if (token != animToken) return;
                if (res != null) cache.put(index, res);
                if (fs != null && fs.length > 0) {
                    AnimationDrawable anim = new AnimationDrawable();
                    anim.setOneShot(false);
                    for (Drawable f : fs) if (f != null) anim.addFrame(f, 33);
                    preview.setImageDrawable(anim);
                    anim.start();
                } else preview.setImageDrawable(res);
            });
        });
    }

    /** Option artwork drawn large (its own size is the small picker card). */
    private boolean big;

    private void setBig() {
        big = true;
        ViewGroup.LayoutParams lp = preview.getLayoutParams();
        lp.height = Math.min(Ui.dp(a, 180), preview.getMaxHeight() > 0 ? preview.getMaxHeight() : Integer.MAX_VALUE);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        preview.setLayoutParams(lp);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    private void select(int i) {
        if (i == sel) return;
        int old = sel;
        sel = i;
        RecyclerView.Adapter<?> ad = list.getAdapter();
        if (ad != null) { ad.notifyItemChanged(posOf(old)); ad.notifyItemChanged(posOf(i)); }
        if (numPanel != null) numPanel.setVisibility(sel == customIndex ? View.VISIBLE : View.GONE);
        if (colorPanel != null) colorPanel.setVisibility(sel == colorIndex ? View.VISIBLE : View.GONE);
        listHeight();
        fitPreview();
        drawPreview();
    }

    private final class ListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return rows(); }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(a).inflate(R.layout.item_option, p, false);
            Ui.fonts(v);
            return new RecyclerView.ViewHolder(v) {};
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int row) {
            View v = h.itemView;
            final int pos = indexAt(row);
            TextView t = v.findViewById(R.id.opt_text);
            t.setText(label(pos));
            if (pos == colorIndex) {
                android.graphics.drawable.Drawable sw = com.hammad.redmagictweaks.ColorPick.swatch(a, pickedColor(), 18);
                sw.setBounds(0, 0, Ui.dp(a, 18), Ui.dp(a, 18));
                t.setCompoundDrawablesRelative(sw, null, null, null);
                t.setCompoundDrawablePadding(Ui.dp(a, 10));
            } else t.setCompoundDrawablesRelative(null, null, null, null);
            t.setTextColor(a.getColor(pos == sel ? R.color.accent : R.color.text));
            v.findViewById(R.id.opt_check).setVisibility(pos == sel ? View.VISIBLE : View.INVISIBLE);
            v.setOnClickListener(x -> select(indexAt(h.getBindingAdapterPosition())));
        }
    }

    private final class GridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return rows(); }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(a).inflate(R.layout.item_pick, p, false);
            Ui.fonts(v);
            return new RecyclerView.ViewHolder(v) {};
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int row) {
            View v = h.itemView;
            final int pos = indexAt(row);
            ((TextView) v.findViewById(R.id.label)).setText(label(pos));
            MaterialCardView card = v.findViewById(R.id.card);
            boolean on = pos == sel;
            card.setStrokeColor(a.getColor(on ? R.color.accent : R.color.outline));
            card.setStrokeWidth(on ? Ui.dp(a, 2) : Ui.dp(a, 1));
            v.findViewById(R.id.check).setVisibility(on ? View.VISIBLE : View.GONE);
            load(pos, v.findViewById(R.id.preview), v.findViewById(R.id.noart));
            card.setOnClickListener(x -> select(indexAt(h.getBindingAdapterPosition())));
        }
    }

    private void load(final int index, final ImageView target, final TextView fallback) {
        if (index == customIndex) { show(index, null, target, fallback); return; }
        if (index == colorIndex) { target.setTag(null); show(index, com.hammad.redmagictweaks.ColorPick.swatch(a, pickedColor(), 56), target, fallback); return; }
        Drawable d = cache.get(index);
        if (d != null || cache.containsKey(index)) { show(index, d, target, fallback); return; }
        target.setImageDrawable(null);
        fallback.setVisibility(View.GONE);
        target.setTag(index);
        pool.execute(() -> {
            Drawable r = null;
            try { r = provider.get(a.getApplicationContext(), index); } catch (Throwable ignored) { }
            final Drawable res = r;
            main.post(() -> { cache.put(index, res); if (Integer.valueOf(index).equals(target.getTag())) show(index, res, target, fallback); });
        });
    }

    private void show(int index, Drawable d, ImageView target, TextView fallback) {
        if (d != null) { target.setImageDrawable(d); fallback.setVisibility(View.GONE); }
        else {
            target.setImageDrawable(null);
            String label = label(index);
            String[] parts = label.replace("·", " ").trim().split("\\s+");
            StringBuilder b = new StringBuilder();
            for (String p : parts) { if (!p.isEmpty()) b.append(Character.toUpperCase(p.charAt(0))); if (b.length() == 2) break; }
            fallback.setText(b.length() == 0 ? "?" : b.toString());
            fallback.setVisibility(View.VISIBLE);
        }
    }
}
