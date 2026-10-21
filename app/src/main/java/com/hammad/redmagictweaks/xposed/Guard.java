package com.hammad.redmagictweaks.xposed;

import com.hammad.redmagictweaks.Features;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedHelpers;

/**
 * Fail-safe checks every hooked process runs before installing anything.
 * <ul>
 * <li>Safe mode: the module setting {@link Features#SAFE_MODE} or the system property {@link #SAFE_PROP} (settable from
 *     ADB / Termux as root) — no hook is installed at all.</li>
 * <li>Crash guard: each app records its own Java crashes in its data folder (.rmt_crashes, one time per line). Three
 *     within five minutes and the hooks pause in that app (.rmt_paused) until the RedMagic Tweaks app turns them back
 *     on (it deletes both files as root). Ten minutes without a crash clear the record.</li>
 * <li>Boot-loop guard (system_server): /data/system/rmt_boot_guard counts starts that never reached boot completed;
 *     at two the framework hooks are skipped until the app resets it.</li>
 * </ul>
 */
final class Guard {
    private Guard() {}

    static final String SAFE_PROP = "persist.redmagictweaks.safe";
    static final String CRASHES = ".rmt_crashes", PAUSED = ".rmt_paused";
    static final String BOOT_FILE = "/data/system/rmt_boot_guard";
    private static final long WINDOW = 5 * 60_000L, STABLE = 10 * 60_000L;

    static boolean safeMode() {
        try {
            Object v = XposedHelpers.callStaticMethod(Class.forName("android.os.SystemProperties"), "get", SAFE_PROP, "");
            if ("1".equals(v) || "true".equals(v)) return true;
        } catch (Throwable ignored) { }
        return Cfg.on(Features.SAFE_MODE);
    }

    /** True when this app's hooks are paused by the crash guard; otherwise starts recording crashes. */
    static boolean crashPaused(String pkg, String dataDir) {
        if (dataDir == null) return false;
        File dir = new File(dataDir);
        File paused = new File(dir, PAUSED), crashes = new File(dir, CRASHES);
        if (paused.exists()) return true;
        long now = System.currentTimeMillis();
        List<Long> recent = new ArrayList<>();
        try {
            if (crashes.exists())
                for (String l : new String(Files.readAllBytes(crashes.toPath()), StandardCharsets.UTF_8).split("\n")) {
                    try { long t = Long.parseLong(l.trim()); if (now - t < WINDOW) recent.add(t); } catch (NumberFormatException ignored) { }
                }
        } catch (Throwable ignored) { }
        if (recent.size() >= 3) {
            try (FileOutputStream o = new FileOutputStream(paused)) { o.write(String.valueOf(now).getBytes(StandardCharsets.UTF_8)); } catch (Throwable ignored) { }
            Lx.w("Crash guard: " + pkg + " crashed " + recent.size() + " times in 5 minutes with RedMagic Tweaks hooks; hooks paused here until you turn them back on in the app");
            return true;
        }
        watch(crashes);
        return false;
    }

    /** Records an uncaught exception's time before the app dies; clears the record after a stable run. */
    private static void watch(final File crashes) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try (FileOutputStream o = new FileOutputStream(crashes, true)) { o.write((System.currentTimeMillis() + "\n").getBytes(StandardCharsets.UTF_8)); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(t, e);
        });
        Thread clear = new Thread(() -> {
            try { Thread.sleep(STABLE); } catch (InterruptedException ignored) { return; }
            //noinspection ResultOfMethodCallIgnored
            crashes.delete();
        }, "rmt-crash-guard");
        clear.setDaemon(true);
        clear.start();
    }

    /** system_server: true when the framework hooks must be skipped (two starts in a row never finished booting). */
    static boolean bootLoop() {
        final File f = new File(BOOT_FILE);
        int n = 0;
        try { if (f.exists()) n = Integer.parseInt(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim()); } catch (Throwable ignored) { }
        if (n >= 2) { Lx.w("Boot-loop guard: the last " + n + " starts never finished booting; framework tweaks skipped until you turn them back on in the app"); return true; }
        write(f, String.valueOf(n + 1));
        Thread t = new Thread(() -> {
            for (int i = 0; i < 300; i++) {   // up to 10 minutes
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
                try {
                    Object v = XposedHelpers.callStaticMethod(Class.forName("android.os.SystemProperties"), "get", "sys.boot_completed", "");
                    if ("1".equals(v)) { write(f, "0"); return; }
                } catch (Throwable ignored) { }
            }
        }, "rmt-boot-guard");
        t.setDaemon(true);
        t.start();
        return false;
    }

    private static void write(File f, String s) {
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(s.getBytes(StandardCharsets.UTF_8)); } catch (Throwable ignored) { }
    }
}
