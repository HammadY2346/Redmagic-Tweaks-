package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Handler;
import android.os.HandlerThread;

import com.hammad.redmagictweaks.FrameworkFeatures;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XposedHelpers;

/**
 * Volume boost past the hardware maximum (system_server). Once a second, while a boost level is chosen
 * and the media stream sits at its maximum, every active media / game playback session gets a
 * LoudnessEnhancer with the chosen gain (RedMagic Helper does the same, keyed to extra volume-up presses;
 * here it is a fixed level so it is predictable). Sessions that stop playing release their effect.
 * Gains: +25 % = 194 mB, +50 % = 352 mB, +100 % = 602 mB, +150 % = 796 mB (20·log10 of the ratio).
 */
final class VolumeBoostHooks {
    private VolumeBoostHooks() {}

    private static final int[] GAIN_MB = {0, 194, 352, 602, 796};
    private static final Map<Integer, LoudnessEnhancer> effects = new HashMap<>();
    private static Handler handler;
    private static String lastError = "";

    static void start(final Context sys) {
        if (sys == null || handler != null) return;
        HandlerThread t = new HandlerThread("RMTweaks-volume-boost", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        t.start();
        handler = new Handler(t.getLooper());
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                try { tick(sys); } catch (Throwable e) { report("tick", e); }
                handler.postDelayed(this, effects.isEmpty() ? 1500 : 800);
            }
        }, 5000);
        Lx.i("VolumeBoost: poller started");
    }

    private static void tick(Context sys) {
        int level = Cfg.choice(FrameworkFeatures.VOLUME_BOOST);
        if (level < 0 || level >= GAIN_MB.length) level = 0;
        // a typed boost (Features.Num, 0-100 %): gain in millibels = 2000 * log10(1 + p / 100), capped at +100 %
        int typedPct = Cfg.hasNum(FrameworkFeatures.VOLUME_BOOST) ? Math.max(0, Math.min(100, Cfg.num(FrameworkFeatures.VOLUME_BOOST, 0))) : -1;
        if (typedPct >= 0) level = typedPct > 0 ? 1 : 0;
        AudioManager am = (AudioManager) sys.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        boolean atMax = am.getStreamVolume(AudioManager.STREAM_MUSIC) >= am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        Set<Integer> keep = new HashSet<>();
        if (level > 0 && atMax) {
            int gain = typedPct >= 0 ? (int) Math.round(2000 * Math.log10(1 + typedPct / 100.0)) : GAIN_MB[level];
            List<?> configs = am.getActivePlaybackConfigurations();
            for (Object c : configs) {
                try {
                    if (!Boolean.TRUE.equals(XposedHelpers.callMethod(c, "isActive"))) continue;
                    AudioAttributes aa = (AudioAttributes) XposedHelpers.callMethod(c, "getAudioAttributes");
                    if (aa == null) continue;
                    int usage = aa.getUsage();
                    if (usage != AudioAttributes.USAGE_MEDIA && usage != AudioAttributes.USAGE_GAME && usage != AudioAttributes.USAGE_UNKNOWN) continue;
                    Object sid = XposedHelpers.callMethod(c, "getSessionId");
                    if (!(sid instanceof Integer) || (Integer) sid <= 0) continue;
                    int session = (Integer) sid;
                    LoudnessEnhancer le = effects.get(session);
                    if (le == null) {
                        le = new LoudnessEnhancer(session);
                        effects.put(session, le);
                        Lx.i("VolumeBoost: +" + gain + " mB on session " + session + " (" + XposedHelpers.callMethod(c, "getClientUid") + ")");
                    }
                    if (!le.hasControl()) { le.release(); effects.remove(session); continue; }
                    if (Math.abs(le.getTargetGain() - gain) > 1f) le.setTargetGain(gain);
                    if (!le.getEnabled()) le.setEnabled(true);
                    keep.add(session);
                } catch (Throwable e) { report("session", e); }
            }
        }
        Iterator<Map.Entry<Integer, LoudnessEnhancer>> it = effects.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, LoudnessEnhancer> e = it.next();
            if (keep.contains(e.getKey())) continue;
            try { e.getValue().release(); } catch (Throwable ignored) { }
            it.remove();
        }
    }

    private static void report(String where, Throwable e) {
        String s = where + ": " + e.getClass().getSimpleName() + ": " + e.getMessage();
        if (!s.equals(lastError)) { lastError = s; Lx.w("VolumeBoost " + s); }
    }
}
