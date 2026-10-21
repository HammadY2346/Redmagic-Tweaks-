package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Saved layouts: named snapshots of every RM Tweaks setting (the world-readable settings file the hooks read), kept in a
 * private preferences file so they never leak to the hooked apps. A snapshot can be applied back (the settings file is
 * replaced exactly), exported to / imported from a JSON file.
 * <pre>
 * {"format":"rmtweaks-layout","v":1,"name":"My One UI","saved":1760000000000,
 *  "values":{"qs_cc_cols":{"i":4},"qs_cc_enable":{"b":true},"some_text":{"s":"…"}}}
 * </pre>
 */
public final class LayoutStore {
    private LayoutStore() {}

    private static final String FILE = "rmtweaks_layouts";
    private static final String KEY = "layouts";
    public static final String FORMAT = "rmtweaks-layout";

    public static final class Layout {
        public final String name; public final long saved; public final JSONObject values;
        Layout(String name, long saved, JSONObject values) { this.name = name; this.saved = saved; this.values = values; }
        public int count() { return values.length(); }
        JSONObject toJson() throws JSONException {
            return new JSONObject().put("format", FORMAT).put("v", 1).put("name", name).put("saved", saved).put("values", values);
        }
    }

    private static SharedPreferences store(Context c) { return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    public static synchronized List<Layout> all(Context c) {
        List<Layout> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(store(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) { Layout l = parse(a.getJSONObject(i)); if (l != null) out.add(l); }
        } catch (JSONException ignored) { }
        return out;
    }

    private static void write(Context c, List<Layout> list) throws JSONException {
        JSONArray a = new JSONArray();
        for (Layout l : list) a.put(l.toJson());
        store(c).edit().putString(KEY, a.toString()).commit();
    }

    /** Snapshot of the current settings under {@code name} (replaces a layout with the same name). */
    public static synchronized Layout saveCurrent(Context c, String name) throws JSONException {
        Layout l = new Layout(name, System.currentTimeMillis(), snapshot(c));
        put(c, l);
        return l;
    }

    public static synchronized void put(Context c, Layout l) throws JSONException {
        List<Layout> list = all(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).name.equalsIgnoreCase(l.name)) list.remove(i);
        list.add(0, l);
        write(c, list);
    }

    public static synchronized void delete(Context c, String name) throws JSONException {
        List<Layout> list = all(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).name.equals(name)) list.remove(i);
        write(c, list);
    }

    public static synchronized void rename(Context c, String from, String to) throws JSONException {
        List<Layout> list = all(c), out = new ArrayList<>();
        for (Layout l : list) {
            if (l.name.equalsIgnoreCase(to) && !l.name.equals(from)) continue;            // the new name wins
            out.add(l.name.equals(from) ? new Layout(to, l.saved, l.values) : l);
        }
        write(c, out);
    }

    /** Every current setting as typed JSON values. */
    public static JSONObject snapshot(Context c) throws JSONException {
        JSONObject v = new JSONObject();
        for (Map.Entry<String, ?> e : Prefs.get(c).getAll().entrySet()) {
            if (Features.SAFE_MODE.equals(e.getKey())) continue;     // safe mode is not part of a look
            Object o = e.getValue();
            if (o instanceof Boolean) v.put(e.getKey(), new JSONObject().put("b", o));
            else if (o instanceof Integer) v.put(e.getKey(), new JSONObject().put("i", o));
            else if (o instanceof Long) v.put(e.getKey(), new JSONObject().put("l", o));
            else if (o instanceof Float) v.put(e.getKey(), new JSONObject().put("f", ((Float) o).doubleValue()));
            else if (o instanceof String) v.put(e.getKey(), new JSONObject().put("s", o));
            else if (o instanceof Set) { JSONArray a = new JSONArray(); for (Object x : (Set<?>) o) a.put(String.valueOf(x)); v.put(e.getKey(), new JSONObject().put("ss", a)); }
        }
        return v;
    }

    /** Replaces the settings file with the layout's values (keys the layout doesn't have go back to their defaults). */
    public static void apply(Context c, Layout l) throws JSONException {
        boolean safe = Prefs.on(c, Features.SAFE_MODE);
        SharedPreferences.Editor e = Prefs.get(c).edit().clear();
        e.putBoolean(Features.SAFE_MODE, safe);
        Iterator<String> it = l.values.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (Features.SAFE_MODE.equals(k)) continue;
            JSONObject t = l.values.getJSONObject(k);
            if (t.has("b")) e.putBoolean(k, t.getBoolean("b"));
            else if (t.has("i")) e.putInt(k, t.getInt("i"));
            else if (t.has("l")) e.putLong(k, t.getLong("l"));
            else if (t.has("f")) e.putFloat(k, (float) t.getDouble("f"));
            else if (t.has("s")) e.putString(k, t.getString("s"));
            else if (t.has("ss")) { JSONArray a = t.getJSONArray("ss"); Set<String> set = new HashSet<>(); for (int i = 0; i < a.length(); i++) set.add(a.getString(i)); e.putStringSet(k, set); }
        }
        e.commit();
        Prefs.migrate(c);
    }

    public static String export(Layout l) throws JSONException { return l.toJson().toString(2); }

    /** Parses an exported file; null when it isn't an RM Tweaks layout. */
    public static Layout parse(String text) {
        try { return parse(new JSONObject(text)); } catch (JSONException e) { return null; }
    }

    private static Layout parse(JSONObject o) {
        if (o == null || !FORMAT.equals(o.optString("format")) || !(o.opt("values") instanceof JSONObject)) return null;
        String name = o.optString("name", "").trim();
        return new Layout(name.isEmpty() ? "Imported layout" : name, o.optLong("saved", System.currentTimeMillis()), o.optJSONObject("values"));
    }
}
