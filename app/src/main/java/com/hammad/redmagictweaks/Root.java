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
     * Restarts every package: each of its processes is killed once, and Android starts the persistent ones (System UI,
     * NFC) again within a second or two.
     *
     * Wallpapers: Android clears the wallpaper (home and lock, back to the default) when the package that draws it is
     * force-stopped (WallpaperManagerService treats a force-stopped wallpaper package like an uninstalled one), and when
     * the wallpaper's process dies twice within 10 seconds. System UI draws every picture wallpaper (ImageWallpaper),
     * so it is never force-stopped, nor is the package of a live wallpaper in use; and System UI is never killed sooner
     * than 12 seconds after the last time this app killed it. Other apps get {@code am force-stop} first.
     */
    public static Result restartApps(String... packages) {
        java.util.List<String> cmds = new java.util.ArrayList<>();
        // packages that draw the home / lock wallpaper right now (ComponentInfo{pkg/...} in dumpsys wallpaper)
        cmds.add("WP=\" $(dumpsys wallpaper 2>/dev/null | grep -oE 'ComponentInfo\\{[^/}]+' | sed 's/ComponentInfo{//' | sort -u | tr '\\n' ' ') " + SYSUI + " \"");
        for (String pkg : packages) {
            if (pkg == null || pkg.isEmpty() || "android".equals(pkg)) continue;
            cmds.add(killCmd(pkg));
        }
        cmds.add("true");
        return run(cmds.toArray(new String[0]));
    }

    static final String SYSUI = "com.android.systemui";
    /** When this app last killed System UI (seconds since the epoch), to keep two kills 12 seconds apart. */
    private static final String SYSUI_STAMP = "/data/local/tmp/.rmt_sysui_kill";

    /** Shell for one package: its process ids first, force-stop unless it draws the wallpaper, then kill what is left once. */
    static String killCmd(String pkg) {
        String re = pkg.replace(".", "\\.");
        StringBuilder b = new StringBuilder();
        if (SYSUI.equals(pkg))
            b.append("L=$(cat ").append(SYSUI_STAMP).append(" 2>/dev/null || echo 0); N=$(date +%s); D=$((N - L)); ")
             .append("if [ $D -ge 0 ] && [ $D -lt 12 ]; then sleep $((12 - D)); fi; date +%s > ").append(SYSUI_STAMP).append("; ");
        b.append("P=$(ps -A -o PID=,NAME= | grep -E ' ").append(re).append("(:.*)?$' | while read pid name; do echo $pid; done); ");
        if (!SYSUI.equals(pkg))
            b.append("case \"$WP\" in *\" ").append(pkg).append(" \"*) ;; *) am force-stop ").append(pkg).append(" 2>/dev/null ;; esac; ");
        b.append("for p in $P; do [ -d /proc/$p ] && kill -9 $p; done");
        return b.toString();
    }

    /**
     * When each package's main process started (seconds since the epoch), for the ones that are running: boot time
     * plus the process start field of /proc/<pid>/stat (clock ticks since boot).
     */
    public static java.util.Map<String, Long> startTimes(String... packages) {
        StringBuilder b = new StringBuilder("B=$(sed -n 's/^btime //p' /proc/stat); T=$(getconf CLK_TCK 2>/dev/null || echo 100); for p in");
        for (String p : packages) if (p != null && p.matches("[A-Za-z0-9_.]+")) b.append(' ').append(p);
        b.append("; do for pid in $(pidof $p); do s=$(cut -d' ' -f22 /proc/$pid/stat 2>/dev/null); [ -n \"$s\" ] && echo \"$p $((B + s / T))\"; break; done; done");
        java.util.Map<String, Long> m = new java.util.HashMap<>();
        Result r = run(b.toString(), "true");
        if (!r.ok() || r.out == null) return m;
        for (String line : r.out.split("\n")) {
            String[] f = line.trim().split(" ");
            if (f.length == 2) { try { m.put(f[0], Long.parseLong(f[1])); } catch (NumberFormatException ignored) { } }
        }
        return m;
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
        String before = pid(SYSUI);
        Result r = restartApps(SYSUI);
        if (!r.ok()) return r;
        for (int i = 0; i < 30; i++) {
            try { Thread.sleep(200); } catch (InterruptedException e) { break; }
            String now = pid(SYSUI);
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
