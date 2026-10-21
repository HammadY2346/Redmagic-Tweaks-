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

    public static void append(Context c, String line) {
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

    public static String stamp() {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
