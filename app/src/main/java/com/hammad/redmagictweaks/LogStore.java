package com.hammad.redmagictweaks;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Append-only log file in the module's private storage, fed by hooked processes through LogProvider. */
public final class LogStore {
    private LogStore() {}
    private static final long MAX_BYTES = 768 * 1024;
    private static final long KEEP_BYTES = 384 * 1024;
    private static final Object LOCK = new Object();

    public static File file(Context c) { return new File(c.getFilesDir(), "hooks.log"); }

    /** Logs page switch (key in the module settings, read by hooked apps through Cfg): true = nothing is recorded. */
    public static final String OFF_KEY = "logs_off";

    public static boolean enabled(Context c) {
        try { return !Prefs.get(c).getBoolean(OFF_KEY, false); } catch (Throwable t) { return true; }
    }

    public static void setEnabled(Context c, boolean on) {
        if (!on) note(c, stamp() + " [app] logging turned off");
        Prefs.set(c, OFF_KEY, !on);
        if (on) note(c, stamp() + " [app] logging turned on");
    }

    /** A line from a hooked app or from this app; dropped while logging is off. */
    public static void append(Context c, String line) {
        if (!enabled(c)) return;
        note(c, line);
    }

    /** Written whatever the switch says (the switch's own on / off lines). */
    private static void note(Context c, String line) {
        synchronized (LOCK) {
            try {
                File f = file(c);
                if (f.length() > MAX_BYTES) trim(f);
                try (FileOutputStream out = new FileOutputStream(f, true)) {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) { }
        }
    }

    private static void trim(File f) throws IOException {
        byte[] tail;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            long start = Math.max(0, len - KEEP_BYTES);
            raf.seek(start);
            tail = new byte[(int) (len - start)];
            raf.readFully(tail);
        }
        int nl = 0;
        while (nl < tail.length && tail[nl] != '\n') nl++;
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            out.write(tail, Math.min(nl + 1, tail.length), Math.max(0, tail.length - nl - 1));
        }
    }

    public static List<String> read(Context c) {
        synchronized (LOCK) {
            List<String> lines = new ArrayList<>();
            File f = file(c);
            if (!f.exists()) return lines;
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                byte[] all = new byte[(int) raf.length()];
                raf.readFully(all);
                for (String s : new String(all, StandardCharsets.UTF_8).split("\n")) if (!s.isEmpty()) lines.add(s);
            } catch (IOException ignored) { }
            return lines;
        }
    }

    public static void clear(Context c) {
        synchronized (LOCK) { //noinspection ResultOfMethodCallIgnored
            file(c).delete(); }
    }

    /**
     * The lines as a .txt file in cache/logs (shared through FileProvider), with a header that says which phone and build
     * they come from. Older exports are removed.
     */
    public static File exportTxt(Context c, List<String> lines, String what) throws IOException {
        File dir = new File(c.getCacheDir(), "logs");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null) for (File o : old) //noinspection ResultOfMethodCallIgnored
            o.delete();
        File f = new File(dir, txtName());
        try (FileOutputStream out = new FileOutputStream(f, false)) {
            out.write(txt(c, lines, what).getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    public static String txtName() {
        return BuildConfig.APP_NAME.replace(" ", "") + "-log-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
    }

    public static String txt(Context c, List<String> lines, String what) {
        StringBuilder sb = new StringBuilder();
        String ver = "?";
        try { ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName + " (" + BuildConfig.VERSION_CODE + ")"; } catch (Throwable ignored) { }
        sb.append(BuildConfig.APP_NAME).append(" log, ").append(what).append(", ").append(lines.size()).append(" lines\n")
          .append("saved: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n')
          .append("app: ").append(ver).append('\n')
          .append("phone: ").append(android.os.Build.MODEL).append(" (").append(android.os.Build.DEVICE).append("), Android ")
          .append(android.os.Build.VERSION.RELEASE).append('\n')
          .append("build: ").append(android.os.Build.DISPLAY).append('\n')
          .append("fingerprint: ").append(android.os.Build.FINGERPRINT).append('\n')
          .append("logging: ").append(enabled(c) ? "on" : "off").append("\n\n");
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }

    public static String stamp() {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
