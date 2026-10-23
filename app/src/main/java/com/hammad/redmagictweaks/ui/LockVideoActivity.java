package com.hammad.redmagictweaks.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.RangeSlider;
import com.hammad.redmagictweaks.LockVideo;
import com.hammad.redmagictweaks.R;

import java.util.List;
import java.util.Locale;

/**
 * Lock screen video: pick a video (or get one from a gallery's Set as… / Share), choose up to 20 s of it with the range
 * slider (preview loops the part), and save it as the lock screen clip (LockVideo.importClip). Without a new video it
 * shows the current clip with Pick another / Remove.
 */
public class LockVideoActivity extends BaseActivity {
    @Override protected void onSettingsChanged() { }

    private static final int TENTHS_MAX = LockVideo.MAX_MS / 100, TENTHS_MIN = LockVideo.MIN_MS / 100;

    private LinearLayout content;
    private VideoView video;
    private TextView range;
    private Uri source;
    private int durationMs;
    private int startMs, endMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean adjusting;
    private float lastStart;

    private final ActivityResultLauncher<String> pick = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        if (uri != null) load(uri);
    });

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            try {
                if (video != null && video.isPlaying() && video.getCurrentPosition() >= endMs) video.seekTo(startMs);
            } catch (Throwable ignored) { }
            handler.postDelayed(this, 100);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        ((CollapsingToolbarLayout) findViewById(R.id.collapsing)).setTitle("Lock screen video");
        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> finish());
        content = findViewById(R.id.content);
        Uri in = incoming(getIntent());
        if (in != null) load(in); else showCurrent();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri in = incoming(intent);
        if (in != null) load(in);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(loop);
        handler.post(loop);
        if (video != null) video.start();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(loop);
        if (video != null) video.pause();
        super.onPause();
    }

    /** The video a gallery handed over: ATTACH_DATA (Set as…) carries it as data, SEND (Share) as EXTRA_STREAM. */
    private static Uri incoming(Intent i) {
        if (i == null) return null;
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            if (u == null && i.getClipData() != null && i.getClipData().getItemCount() > 0) u = i.getClipData().getItemAt(0).getUri();
            return u;
        }
        if (Intent.ACTION_ATTACH_DATA.equals(i.getAction()) || Intent.ACTION_VIEW.equals(i.getAction())) return i.getData();
        return null;
    }

    // ------------------------------------------------------------------ screens

    /** No new video: the current clip (looping its part) with Pick another / Remove, or Pick a video. */
    private void showCurrent() {
        source = null;
        content.removeAllViews();
        LockVideo.Meta m = LockVideo.meta(this);
        if (m != null) {
            startMs = m.startMs;
            endMs = m.endMs;
            content.addView(preview(Uri.fromFile(LockVideo.clip(this)), m.width, m.height));
            content.addView(text(String.format(Locale.US, "Current clip · %.1f s", (m.endMs - m.startMs) / 1000f), 15, R.color.text, 600, 14));
        } else {
            content.addView(text("No lock screen video yet.", 15, R.color.text, 600, 14));
        }
        content.addView(text("Pick a video and choose up to 20 seconds of it. It plays when the screen turns on while the phone is locked, "
                + "behind the clock and notifications; the home screen keeps its wallpaper. A gallery's Set as… or Share › Lock screen video "
                + "opens this screen too.", 13, R.color.text_dim, 400, 6));
        LinearLayout buttons = row();
        buttons.addView(button(m == null ? "Pick a video" : "Pick another video", true, v -> pick.launch("video/*")));
        if (m != null) buttons.addView(button("Remove", false, v -> confirmRemove()));
        content.addView(buttons);
        content.addView(note());
        Ui.fonts(content);
    }

    private void load(Uri uri) {
        content.removeAllViews();
        ProgressBar pb = new ProgressBar(this);
        content.addView(pb, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 120)));
        new Thread(() -> {
            try {
                int[] p = LockVideo.probe(this, uri);
                runOnUiThread(() -> showTrim(uri, p));
            } catch (Exception e) {
                runOnUiThread(() -> { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); showCurrent(); });
            }
        }, "RMTweaks-lockvideo-probe").start();
    }

    /** A new video: preview + range slider (at most 20 s, at least 1 s) + Use this part. */
    private void showTrim(Uri uri, int[] probe) {
        source = uri;
        durationMs = probe[0];
        content.removeAllViews();
        int total = Math.max(0, durationMs / 100);
        if (total < TENTHS_MIN) {
            Toast.makeText(this, "This video is shorter than a second.", Toast.LENGTH_LONG).show();
            showCurrent();
            return;
        }
        startMs = 0;
        endMs = Math.min(durationMs, LockVideo.MAX_MS);
        content.addView(preview(uri, probe[1], probe[2]));
        content.addView(text("Choose up to 20 seconds", 15, R.color.text, 600, 14));
        range = text("", 13, R.color.text_dim, 400, 2);
        content.addView(range);
        RangeSlider rs = new RangeSlider(this);
        rs.setValueFrom(0f);
        rs.setValueTo(total);
        rs.setStepSize(1f);
        rs.setMinSeparationValue(TENTHS_MIN);
        rs.setValues(0f, (float) Math.min(total, TENTHS_MAX));
        rs.setLabelFormatter(v -> time((int) (v * 100)));
        lastStart = 0f;
        rs.addOnChangeListener((slider, value, fromUser) -> {
            if (adjusting) return;
            List<Float> v = slider.getValues();
            float a = v.get(0), b = v.get(1);
            if (b - a > TENTHS_MAX) {
                adjusting = true;
                if (Math.abs(a - lastStart) > 0.01f) b = a + TENTHS_MAX; else a = b - TENTHS_MAX;
                slider.setValues(a, b);
                adjusting = false;
            }
            boolean startMoved = Math.abs(a - lastStart) > 0.01f;
            lastStart = a;
            startMs = Math.round(a * 100);
            endMs = Math.min(durationMs, Math.round(b * 100));
            updateRange();
            if (video != null) video.seekTo(startMoved ? startMs : Math.max(startMs, endMs - 1500));
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        content.addView(rs, lp);
        updateRange();
        LinearLayout buttons = row();
        buttons.addView(button("Use this part", true, v -> save()));
        buttons.addView(button("Pick another", false, v -> pick.launch("video/*")));
        content.addView(buttons);
        content.addView(note());
        Ui.fonts(content);
    }

    private void updateRange() {
        if (range != null) range.setText(String.format(Locale.US, "%s – %s · %.1f s", time(startMs), time(endMs), (endMs - startMs) / 1000f));
    }

    private void save() {
        if (source == null) return;
        final Uri uri = source;
        final int s = startMs, e = endMs;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 24);
        box.setPadding(p, p, p, p);
        box.addView(new ProgressBar(this));
        TextView t = text("Cutting the clip…", 15, R.color.text, 500, 0);
        t.setPadding(Ui.dp(this, 16), 0, 0, 0);
        box.addView(t);
        AlertDialog d = new MaterialAlertDialogBuilder(this).setView(box).setCancelable(false).show();
        new Thread(() -> {
            try {
                LockVideo.Meta m = LockVideo.importClip(this, uri, s, e);
                runOnUiThread(() -> {
                    d.dismiss();
                    Toast.makeText(this, String.format(Locale.US, "Lock screen video set (%.1f s). Lock the phone to see it.", (m.endMs - m.startMs) / 1000f), Toast.LENGTH_LONG).show();
                    showCurrent();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    d.dismiss();
                    new MaterialAlertDialogBuilder(this).setTitle("Couldn't use this video")
                            .setMessage(ex.getMessage() == null ? ex.toString() : ex.getMessage())
                            .setPositiveButton(android.R.string.ok, null).show();
                });
            }
        }, "RMTweaks-lockvideo-import").start();
    }

    private void confirmRemove() {
        new MaterialAlertDialogBuilder(this).setTitle("Remove the lock screen video?")
                .setMessage("The lock screen shows its wallpaper again.")
                .setPositiveButton("Remove", (d, w) -> { LockVideo.remove(this); showCurrent(); })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    // ------------------------------------------------------------------ views

    /** A muted, looping preview of [startMs, endMs] at the video's own shape (portrait box). */
    private View preview(Uri uri, int w, int h) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundResource(R.drawable.card_bg);
        frame.setClipToOutline(true);
        video = new VideoView(this);
        FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        frame.addView(video, vlp);
        video.setOnPreparedListener(mp -> {
            mp.setVolume(0f, 0f);
            mp.setLooping(false);
            video.seekTo(startMs);
            video.start();
        });
        video.setOnCompletionListener(mp -> { video.seekTo(startMs); video.start(); });
        video.setOnErrorListener((mp, what, extra) -> { Toast.makeText(this, "The preview can't play this video (" + what + ")", Toast.LENGTH_SHORT).show(); return true; });
        video.setVideoURI(uri);
        int height = Ui.dp(this, h > w ? 420 : 240);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
        lp.topMargin = Ui.dp(this, 8);
        frame.setLayoutParams(lp);
        return frame;
    }

    private View note() {
        return text("When it plays, looping, sound and fit are under Lock screen & AOD › Video wallpaper. After installing this RM Tweaks update, "
                + "restart System UI once.", 12.5f, R.color.text_dim, 400, 16);
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, Ui.dp(this, 14), 0, 0);
        return l;
    }

    private MaterialButton button(String label, boolean primary, View.OnClickListener click) {
        MaterialButton b = primary ? new MaterialButton(this) : new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(Ui.dp(this, 8));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String s, float sp, int color, int weight, int topDp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(getColor(color));
        t.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        t.setPadding(0, Ui.dp(this, topDp), 0, 0);
        return t;
    }

    private static String time(int ms) {
        ms = Math.max(0, ms);
        return String.format(Locale.US, "%d:%02d.%d", ms / 60000, (ms / 1000) % 60, (ms / 100) % 10);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (video != null) video.stopPlayback();
        super.onDestroy();
    }
}
