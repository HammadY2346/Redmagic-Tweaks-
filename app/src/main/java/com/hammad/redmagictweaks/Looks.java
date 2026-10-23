package com.hammad.redmagictweaks;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * A set of setting values for the previews: the live settings, the live settings with a preset or one changed choice
 * laid over them (what applying would give), or a saved layout. Nothing here writes anything.
 */
public final class Looks {
    private Looks() {}

    public interface V {
        boolean on(String key);
        int choice(String key);
    }

    /** The current settings. */
    public static V live(Context c) {
        final SharedPreferences sp = Prefs.get(c);
        return new V() {
            @Override public boolean on(String key) { try { return sp.getBoolean(key, false); } catch (Throwable t) { return false; } }
            @Override public int choice(String key) { try { return sp.getInt(key, 0); } catch (Throwable t) { return 0; } }
        };
    }

    /** {@code base} with every {key, value} of a preset (Presets.ONE_UI...) applied on top. */
    public static V with(V base, Object[][] preset) {
        Map<String, Object> m = new HashMap<>();
        for (Object[] e : preset) m.put((String) e[0], e[1]);
        presetsOnly(m);
        return over(base, m);
    }

    /** {@code base} with one value changed (a choice index or a switch); a choice index means its preset, not a custom value. */
    public static V with(V base, String key, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        presetsOnly(m);
        return over(base, m);
    }

    /** A choice given as an index shows that preset: its typed value / custom colour is off unless also given. */
    private static void presetsOnly(Map<String, Object> m) {
        java.util.List<String> keys = new java.util.ArrayList<>(m.keySet());
        for (String k : keys) {
            if (!(m.get(k) instanceof Number) || k.endsWith(Features.VALUE) || k.endsWith(ColorPick.C1) || k.endsWith(ColorPick.C2)) continue;
            if (!m.containsKey(k + Features.CUSTOM)) m.put(k + Features.CUSTOM, Boolean.FALSE);
            if (!m.containsKey(k + ColorPick.ON)) m.put(k + ColorPick.ON, Boolean.FALSE);
        }
    }

    /** The custom colour of a colour option in {@code v} ({c1} or {c1, c2}), or null. */
    public static int[] color(V v, String key) { return ColorPick.of(v, key); }

    private static V over(final V base, final Map<String, Object> m) {
        return new V() {
            @Override public boolean on(String key) { Object o = m.get(key); return o instanceof Boolean ? (Boolean) o : base.on(key); }
            @Override public int choice(String key) { Object o = m.get(key); return o instanceof Number ? ((Number) o).intValue() : base.choice(key); }
        };
    }

    /** The values of a saved layout (keys it doesn't have are stock, as when it is applied). */
    public static V of(final LayoutStore.Layout l) {
        final JSONObject v = l.values;
        return new V() {
            @Override public boolean on(String key) { JSONObject t = v.optJSONObject(key); return t != null && t.optBoolean("b", false); }
            @Override public int choice(String key) { JSONObject t = v.optJSONObject(key); return t == null ? 0 : t.optInt("i", 0); }
        };
    }
}
