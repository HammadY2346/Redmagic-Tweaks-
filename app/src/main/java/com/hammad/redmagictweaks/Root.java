package com.hammad.redmagictweaks;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Minimal root shell helper (SukiSU / KernelSU / Magisk `su`). */
public final class Root {
    private Root() {}

    public static final class Result {
        public final int code; public final String out;
        Result(int code, String out) { this.code = code; this.out = out; }
        public boolean ok() { return code == 0; }
    }

    /** Tests: never start su. */
    static volatile boolean offline;

    public static Result run(String... commands) {
        if (offline) return "id".equals(commands[0]) ? new Result(0, "uid=0(root)") : new Result(-1, "no root in tests");
        if ("robolectric".equals(android.os.Build.FINGERPRINT)) return new Result(-1, "no root in tests");
        try {
            Process p = Runtime.getRuntime().exec("su");
            try (OutputStream os = p.getOutputStream()) {
                for (String c : commands) os.write((c + "\n").getBytes(StandardCharsets.UTF_8));
                os.write("exit\n".getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String l; while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            int code = p.waitFor();
            return new Result(code, sb.toString().trim());
        } catch (Exception e) {
            return new Result(-1, String.valueOf(e));
        }
    }

    /**
     * Restarts every package. {@code am force-stop} alone is not enough: Android skips persistent processes (System UI,
     * the phone process, NFC) on force-stop, so "Restart System UI" used to leave System UI running with the old settings.
     * Every process of the package (also ":screenshot" and the other sub-processes) is killed as well; persistent ones
     * are started again by Android within a second or two.
     */
    public static Result restartApps(String... packages) {
        java.util.List<String> cmds = new java.util.ArrayList<>();
        for (String pkg : packages) {
            if (pkg == null || pkg.isEmpty() || "android".equals(pkg)) continue;
            cmds.add("am force-stop " + pkg + " 2>/dev/null");
            cmds.add(killCmd(pkg));
        }
        cmds.add("true");
        return run(cmds.toArray(new String[0]));
    }

    private static String killCmd(String pkg) {
        String re = pkg.replace(".", "\\.");
        return "ps -A -o PID=,NAME= | grep -E ' " + re + "(:.*)?$' | while read pid name; do kill -9 $pid; done";
    }

    /** Process ids of the package's main process ("" when it isn't running). */
    public static String pid(String pkg) {
        Result r = run("pidof " + pkg + " || true");
        return r.out == null ? "" : r.out.trim();
    }

    /**
     * Restarts System UI and checks that it really restarted: a new process id within 6 seconds. The result's code is
     * 0 when it did, 2 when System UI kept its old process (then a soft reboot is the way).
     */
    public static Result restartSystemUi() {
        String before = pid("com.android.systemui");
        Result r = restartApps("com.android.systemui");
        if (!r.ok()) return r;
        for (int i = 0; i < 30; i++) {
            try { Thread.sleep(200); } catch (InterruptedException e) { break; }
            String now = pid("com.android.systemui");
            if (!now.isEmpty() && !now.equals(before)) return new Result(0, "System UI restarted (" + (before.isEmpty() ? "-" : before) + " -> " + now + ")");
        }
        return new Result(2, "System UI did not restart (still " + before + ")");
    }

    /** Safe mode property every hook checks first (also settable from ADB / Termux). */
    public static final String SAFE_PROP = "persist.redmagictweaks.safe";
    public static Result setSafeProp(boolean on) { return run("setprop " + SAFE_PROP + " " + (on ? "1" : "0")); }

    // ---- fail-safe guards (xposed/Guard) ----
    private static final String PAUSED = "/data/user_de/*/*/.rmt_paused /data/user/*/*/.rmt_paused /data/data/*/.rmt_paused";
    private static final String CRASHES = "/data/user_de/*/*/.rmt_crashes /data/user/*/*/.rmt_crashes /data/data/*/.rmt_crashes";
    public static final String BOOT_GUARD = "/data/system/rmt_boot_guard";

    /** Packages whose tweaks the crash guard paused, plus "android" when the boot-loop guard stopped the framework tweaks. */
    public static java.util.List<String> pausedByGuards() {
        Result r = run("for f in " + PAUSED + "; do [ -f \"$f\" ] && basename \"$(dirname \"$f\")\"; done",
                "n=$(cat " + BOOT_GUARD + " 2>/dev/null); [ \"${n:-0}\" -ge 2 ] 2>/dev/null && echo android", "true");
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (r.out != null) for (String l : r.out.split("\n")) { l = l.trim(); if (l.matches("[A-Za-z0-9_.]+")) out.add(l); }
        return new java.util.ArrayList<>(out);
    }

    /** Turns the tweaks back on everywhere the guards paused them (the apps load them at their next start). */
    public static Result resumeGuards() {
        return run("rm -f " + PAUSED + " " + CRASHES + " 2>/dev/null", "echo 0 > " + BOOT_GUARD + " 2>/dev/null", "true");
    }

    public static Result softReboot() { return run("setprop ctl.restart zygote"); }
    public static Result reboot() { return run("svc power reboot || reboot"); }
}
