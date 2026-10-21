package com.hammad.redmagictweaks.ui;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hammad.redmagictweaks.Features;
import com.hammad.redmagictweaks.Prefs;
import com.hammad.redmagictweaks.Previews;
import com.hammad.redmagictweaks.R;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Picture grid for a CHOICE feature that registered a Previews provider: one card per option with its
 * artwork, the current choice ticked; tapping a card opens a large (animated when frames exist) preview
 * with "Use". Previews are rendered on a background thread and cached per index.
 */
public class PickerActivity extends AppCompatActivity {
    public static final String EXTRA_KEY = "key";
    /** Result extra: true when the selection changed, so the section page can offer the restart. */
    public static final String RESULT_CHANGED = "changed";

    private Features.Feature feature;
    private Previews.Provider provider;
    private int selected;
    private boolean changed;
    private final Map<Integer, Drawable> cache = new HashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private RecyclerView grid;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String key = getIntent().getStringExtra(EXTRA_KEY);
        feature = key == null ? null : Features.feature(key);
        provider = key == null ? null : Previews.of(key);
        if (feature == null || feature.choices == null) { finish(); return; }
        setContentView(R.layout.activity_picker);
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setTitle(feature.title);
        tb.setNavigationOnClickListener(v -> finish());
        TextView hint = findViewById(R.id.hint);
        String h = Previews.hint(key);
        hint.setText(h != null ? h : "Tap an option to preview it");
        selected = Prefs.choice(this, key);
        if (selected < 0 || selected >= feature.choices.length) selected = 0;
        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setAdapter(new Adapter());
        grid.scrollToPosition(Math.max(0, selected - 3));
    }

    @Override public void finish() {
        setResult(RESULT_OK, new android.content.Intent().putExtra(RESULT_CHANGED, changed).putExtra(EXTRA_KEY, feature == null ? null : feature.key));
        super.finish();
    }

    @Override protected void onDestroy() { super.onDestroy(); pool.shutdownNow(); }

    private void load(int index, ImageView target, TextView fallback) {
        Drawable d = cache.get(index);
        if (d != null || cache.containsKey(index)) { show(index, d, target, fallback); return; }
        target.setImageDrawable(null); fallback.setVisibility(View.GONE);
        target.setTag(index);
        final Context app = getApplicationContext();
        pool.execute(() -> {
            Drawable r = null;
            try { r = provider == null ? null : provider.get(app, index); } catch (Throwable ignored) { }
            final Drawable res = r;
            main.post(() -> { cache.put(index, res); if (Integer.valueOf(index).equals(target.getTag())) show(index, res, target, fallback); });
        });
    }

    private void show(int index, Drawable d, ImageView target, TextView fallback) {
        if (d != null) { target.setImageDrawable(d); fallback.setVisibility(View.GONE); }
        else { target.setImageDrawable(null); fallback.setText(initials(feature.choices[index])); fallback.setVisibility(View.VISIBLE); }
    }

    private static String initials(String label) {
        if (label == null || label.isEmpty()) return "?";
        String[] parts = label.replace("·", " ").trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String p : parts) { if (!p.isEmpty()) b.append(Character.toUpperCase(p.charAt(0))); if (b.length() == 2) break; }
        return b.toString();
    }

    private void preview(final int index) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_preview, null);
        final ImageView img = v.findViewById(R.id.preview);
        TextView text = v.findViewById(R.id.text);
        text.setText(feature.desc == null ? "" : feature.desc);
        Drawable still = cache.get(index);
        if (still != null) img.setImageDrawable(still);
        final Previews.FrameProvider fp = Previews.frames(feature.key);
        final Context app = getApplicationContext();
        if (fp != null) {
            pool.execute(() -> {
                Drawable[] frames = null;
                try { frames = fp.get(app, index); } catch (Throwable ignored) { }
                if (frames == null || frames.length == 0) return;
                final AnimationDrawable anim = new AnimationDrawable();
                anim.setOneShot(false);
                for (Drawable f : frames) if (f != null) anim.addFrame(f, 33);
                main.post(() -> { img.setImageDrawable(anim); anim.start(); });
            });
        } else if (still == null && provider != null) {
            pool.execute(() -> {
                Drawable d = null;
                try { d = provider.get(app, index); } catch (Throwable ignored) { }
                final Drawable res = d;
                main.post(() -> { cache.put(index, res); if (res != null) img.setImageDrawable(res); });
            });
        }
        View content = v;
        String sectionId = null;
        for (Features.Section sec : Features.SECTIONS) for (Features.Feature f : sec.features) if (f.key.equals(feature.key) && sectionId == null) sectionId = sec.id;
        if (sectionId != null && com.hammad.redmagictweaks.LookPreview.screensFor(sectionId) != null && com.hammad.redmagictweaks.LookPreview.uses(feature.key)) {
            // the whole screen with this option, before it is applied
            final com.hammad.redmagictweaks.Looks.V with = com.hammad.redmagictweaks.Looks.with(com.hammad.redmagictweaks.Looks.live(this), feature.key, index);
            android.widget.LinearLayout box = new android.widget.LinearLayout(this);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            box.addView(v);
            TextView cap = new TextView(this);
            cap.setText("On your phone (tap for every screen):");
            cap.setTextColor(getColor(R.color.text_dim));
            cap.setTextSize(13);
            int pad = PreviewDialogs.dp(this, 24);
            cap.setPadding(pad, PreviewDialogs.dp(this, 8), pad, PreviewDialogs.dp(this, 6));
            box.addView(cap);
            ImageView whole = new ImageView(this);
            whole.setImageDrawable(com.hammad.redmagictweaks.LookPreview.section(this, sectionId, with, PreviewDialogs.dialogWidthDp(this)));
            whole.setOnClickListener(x -> PreviewDialogs.showAll(this, feature.choices[index], with));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = PreviewDialogs.dp(this, 8);
            box.addView(whole, lp);
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.addView(box);
            content = sv;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(feature.choices[index])
                .setView(content)
                .setNegativeButton("Back", null)
                .setPositiveButton(index == selected ? "In use" : "Use", (d, w) -> select(index))
                .show();
    }

    private void select(int index) {
        if (index == selected) return;
        int old = selected;
        selected = index;
        changed = true;
        Prefs.set(this, feature.key, index);
        RecyclerView.Adapter<?> a = grid.getAdapter();
        if (a != null) { a.notifyItemChanged(old); a.notifyItemChanged(index); }
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return feature.choices.length; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pick, parent, false)) {};
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            View v = h.itemView;
            ((TextView) v.findViewById(R.id.label)).setText(feature.choices[pos]);
            MaterialCardView card = v.findViewById(R.id.card);
            boolean sel = pos == selected;
            card.setStrokeColor(getColor(sel ? R.color.accent : R.color.outline));
            card.setStrokeWidth(sel ? 4 : 1);
            v.findViewById(R.id.check).setVisibility(sel ? View.VISIBLE : View.GONE);
            load(pos, v.findViewById(R.id.preview), v.findViewById(R.id.noart));
            card.setOnClickListener(x -> preview(h.getBindingAdapterPosition()));
            card.setOnLongClickListener(x -> { select(h.getBindingAdapterPosition()); return true; });
        }
    }
}
