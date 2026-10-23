package com.hammad.redmagictweaks.xposed;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.ArrayDeque;

import de.robv.android.xposed.XposedBridge;

/**
 * Logging from inside hooked processes. Every line goes to the LSPosed log and, once the host
 * app has a Context, to the module's own log (shown in the app's Logs page).
 */
public final class Lx {
    private Lx() {}
    private static final Uri LOG_URI = Uri.parse("content://" + com.hammad.redmagictweaks.LogProvider.AUTHORITY);
    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    private static volatile Context ctx;
    private static Handler io;
    private static String tag = "?";
    private static int retries;
    private static final long[] BACKOFF = {2000, 5000, 10000, 20000, 30000, 60000, 60000, 120000, 120000, 300000, 300000, 600000};

    static void init(String packageName) { tag = packageName; }

    /** Host application context once Application.attach has run; null before that. */
    static Context context() { return ctx; }

    static void attach(Context c) {
        if (c == null) return;
        ctx = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        synchronized (queue) {
            if (io == null) {
                HandlerThread t = new HandlerThread("rmtweaks-log");
                t.start();
                io = new Handler(t.getLooper());
            }
        }
        flush();
    }

    public static void i(String msg) { log("I", msg); }
    public static void w(String msg) { log("W", msg); }
    public static void e(String msg, Throwable t) { log("E", msg + (t == null ? "" : " :: " + t)); }

    private static void log(String level, String msg) {
        // Logs page switch off: nothing is sent to the module's log; errors still go to LSPosed's own log
        boolean off = Cfg.on(com.hammad.redmagictweaks.LogStore.OFF_KEY);
        if (off && !"E".equals(level)) return;
        String line = level + " " + msg;
        try { XposedBridge.log("RMTweaks[" + tag + "] " + line); } catch (Throwable ignored) { }
        if (off) return;
        synchronized (queue) {
            if (queue.size() > 400) queue.pollFirst();
            queue.addLast(line);
        }
        if (ctx != null) flush();
    }

    private static void flush() {
        final Context c = ctx;
        final Handler h = io;
        if (c == null || h == null) return;
        h.post(() -> {
            String line;
            while (true) {
                synchronized (queue) { line = queue.peekFirst(); }
                if (line == null) { retries = 0; break; }
                try {
                    c.getContentResolver().call(LOG_URI, "log", line, null);
                    synchronized (queue) { queue.pollFirst(); }
                } catch (Throwable t) {
                    // module app not reachable yet (early boot / user locked) — keep the line, retry later
                    if (retries < BACKOFF.length) h.postDelayed(Lx::flush, BACKOFF[retries++]);
                    break;
                }
            }
        });
    }
}
