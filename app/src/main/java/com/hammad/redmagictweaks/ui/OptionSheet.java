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
    private final String screenCategory;
    private final Result result;
    private final int current;
    private int sel;
    private final Previews.Provider provider;
    private final Previews.FrameProvider frames;
    private final Map<Integer, Drawable> cache = new HashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private ImageView preview;
    private RecyclerView list;
    private int animToken;

    /** @param screenCategory category whose screens to draw with the option applied, or null for the option's own artwork */
    OptionSheet(Activity a, Catalog.Item item, String screenCategory, Result result) {
        this.a = a; this.item = item; this.screenCategory = screenCategory; this.result = result;
        current = Catalog.choice(a, item);
        sel = current;
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
        list.scrollToPosition(Math.max(0, sel - (grid ? 3 : 2)));
        v.findViewById(R.id.sheet_cancel).setOnClickListener(x -> d.dismiss());
        v.findViewById(R.id.sheet_apply).setOnClickListener(x -> { d.dismiss(); result.done(sel, sel != current); });
        Ui.fonts(v);
        d.setContentView(v);
        d.setOnDismissListener(x -> { animToken++; pool.shutdownNow(); });
        BottomSheetBehavior<?> b = d.getBehavior();
        b.setSkipCollapsed(true);
        b.setState(BottomSheetBehavior.STATE_EXPANDED);
        // the list scrolls inside a sheet that may take most of the screen
        int maxList = Math.round(a.getResources().getDisplayMetrics().heightPixels * (screenCategory != null || provider != null ? 0.34f : 0.5f));
        list.getLayoutParams().height = Math.min(maxList, estimatedListHeight(grid));
        drawPreview();
        d.show();
    }

    private int estimatedListHeight(boolean grid) {
        int n = item.feature.choices.length;
        return grid ? ((n + 2) / 3) * Ui.dp(a, 150) : n * Ui.dp(a, 52) + Ui.dp(a, 8);
    }

    /** The big picture for the selected option. */
    private void drawPreview() {
        final int token = ++animToken;
        if (screenCategory != null) {
            Looks.V picked = Looks.with(Looks.live(a), item.key(), sel);
            String parent = Catalog.needs(item.key());
            final Looks.V with = parent == null ? picked : Looks.with(picked, parent, Boolean.TRUE);   // show the option, even while its switch is off
            String[] s = LookPreview.screensFor(screenCategory);
            int w = Math.max(240, a.getResources().getConfiguration().screenWidthDp - 48);
            preview.setImageDrawable(s != null && s.length == 1 && LookPreview.BAR.equals(s[0]) ? LookPreview.strip(a, with, w) : LookPreview.section(a, screenCategory, with, w));
            preview.setOnClickListener(x -> PreviewDialogs.showAll(a, item.feature.choices[sel], with));
            preview.setVisibility(View.VISIBLE);
            return;
        }
        if (provider == null) { preview.setVisibility(View.GONE); return; }
        preview.setVisibility(View.VISIBLE);
        preview.setOnClickListener(null);
        final int index = sel;
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
    private void setBig() {
        ViewGroup.LayoutParams lp = preview.getLayoutParams();
        lp.height = Ui.dp(a, 180);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        preview.setLayoutParams(lp);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    private void select(int i) {
        if (i == sel) return;
        int old = sel;
        sel = i;
        RecyclerView.Adapter<?> ad = list.getAdapter();
        if (ad != null) { ad.notifyItemChanged(old); ad.notifyItemChanged(i); }
        drawPreview();
    }

    private final class ListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return item.feature.choices.length; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(a).inflate(R.layout.item_option, p, false);
            Ui.fonts(v);
            return new RecyclerView.ViewHolder(v) {};
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            View v = h.itemView;
            TextView t = v.findViewById(R.id.opt_text);
            t.setText(item.feature.choices[pos]);
            t.setTextColor(a.getColor(pos == sel ? R.color.accent : R.color.text));
            v.findViewById(R.id.opt_check).setVisibility(pos == sel ? View.VISIBLE : View.INVISIBLE);
            v.setOnClickListener(x -> select(h.getBindingAdapterPosition()));
        }
    }

    private final class GridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return item.feature.choices.length; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
            View v = LayoutInflater.from(a).inflate(R.layout.item_pick, p, false);
            Ui.fonts(v);
            return new RecyclerView.ViewHolder(v) {};
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            View v = h.itemView;
            ((TextView) v.findViewById(R.id.label)).setText(item.feature.choices[pos]);
            MaterialCardView card = v.findViewById(R.id.card);
            boolean on = pos == sel;
            card.setStrokeColor(a.getColor(on ? R.color.accent : R.color.outline));
            card.setStrokeWidth(on ? Ui.dp(a, 2) : Ui.dp(a, 1));
            v.findViewById(R.id.check).setVisibility(on ? View.VISIBLE : View.GONE);
            load(pos, v.findViewById(R.id.preview), v.findViewById(R.id.noart));
            card.setOnClickListener(x -> select(h.getBindingAdapterPosition()));
        }
    }

    private void load(final int index, final ImageView target, final TextView fallback) {
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
            String label = item.feature.choices[index];
            String[] parts = label.replace("·", " ").trim().split("\\s+");
            StringBuilder b = new StringBuilder();
            for (String p : parts) { if (!p.isEmpty()) b.append(Character.toUpperCase(p.charAt(0))); if (b.length() == 2) break; }
            fallback.setText(b.length() == 0 ? "?" : b.toString());
            fallback.setVisibility(View.VISIBLE);
        }
    }
}
