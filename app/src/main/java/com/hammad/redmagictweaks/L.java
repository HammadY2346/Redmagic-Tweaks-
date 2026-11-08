package com.hammad.redmagictweaks;

import android.content.Context;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * App language. English stays in the code (option names and titles are compared in English). When Russian is selected,
 * each whole string is replaced by its entry in assets/i18n/ru.json before the view is drawn. A string is translated
 * only as a complete sentence, or as a sentence with format placeholders (%s, %d). Pieces of a sentence are never
 * swapped on their own.
 */
public final class L {
    private L() {}

    private static volatile boolean loaded;
    private static Map<String, String> map;
    private static final List<Pattern> patterns = new ArrayList<>();
    private static final List<String> patternValues = new ArrayList<>();

    /** Language selected in the app ("en" / "ru"). Until picked, follows the phone language. */
    public static String language(Context c) { return UiPrefs.language(c); }

    /** Phone language only: "ru" when the system locale is Russian, otherwise "en". */
    public static String language() {
        return "ru".equals(Locale.getDefault().getLanguage()) ? "ru" : "en";
    }

    /** Change the app language and clear the cached translation before the activity is recreated. */
    public static synchronized void setLanguage(Context c, String language) {
        UiPrefs.setLanguage(c, language);
        loaded = false;
        map = null;
        patterns.clear();
        patternValues.clear();
    }

    public static boolean active(Context c) { load(c); return map != null; }

    private static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        if (c == null) return;
        String lang = language(c);
        if (!"ru".equals(lang)) return;   // English is the source text in code; no en.json
        try (InputStream in = c.getApplicationContext().getAssets().open("i18n/" + lang + ".json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            JSONObject o = new JSONObject(b.toString("UTF-8"));
            Map<String, String> m = new HashMap<>(o.length() * 2);
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next(), v = o.getString(k);
                m.put(k, v);
                if (k.matches("(?s).*%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?[sdf].*")) addPattern(k, v);
            }
            map = m;
        } catch (Exception ignored) { map = null; }
    }

    private static void addPattern(String k, String v) {
        Matcher m = Pattern.compile("%(\\d+\\$)?[-#+ 0,(]*\\d*(\\.\\d+)?([sdf])").matcher(k);
        StringBuilder re = new StringBuilder();
        int last = 0;
        while (m.find()) {
            re.append(Pattern.quote(k.substring(last, m.start())));
            re.append("s".equals(m.group(3)) ? "(.+?)" : "(-?[0-9][0-9.,]*)");
            last = m.end();
        }
        re.append(Pattern.quote(k.substring(last)));
        try { patterns.add(Pattern.compile(re.toString(), Pattern.DOTALL)); patternValues.add(v); } catch (Exception ignored) { }
    }

    /** The translation of a shown text, or the text itself. */
    public static String t(Context c, String s) {
        if (s == null || s.isEmpty()) return s;
        load(c);
        Map<String, String> m = map;
        if (m == null || s.length() > 4000) return s;
        String r = m.get(s);
        if (r != null) return r;
        for (int i = 0; i < patterns.size(); i++) {
            Matcher x = patterns.get(i).matcher(s);
            if (!x.matches()) continue;
            String v = patternValues.get(i);
            Matcher ph = Pattern.compile("%(\\d+)?\\$?[-#+ 0,(]*\\d*(\\.\\d+)?([sdf])").matcher(v);
            StringBuffer out = new StringBuffer();
            int n = 0;
            while (ph.find()) {
                int idx = ph.group(1) != null && v.charAt(ph.start() + ph.group(1).length() + 1) == '$' ? Integer.parseInt(ph.group(1)) : ++n;
                String arg = idx >= 1 && idx <= x.groupCount() ? x.group(idx) : "";
                ph.appendReplacement(out, Matcher.quoteReplacement(arg));
            }
            ph.appendTail(out);
            return out.toString();
        }
        return s;
    }

    public static CharSequence t(Context c, CharSequence s) {
        if (s == null || s instanceof Spanned && ((Spanned) s).getSpans(0, s.length(), Object.class).length > 0) return s;
        return t(c, s.toString());
    }

    // ---- views ----

    private static final int TAG = 0x7f0b0001 ^ 0x5a5a;   // any id not used as a view tag elsewhere

    /** Translates a TextView now and every time its text is set again. */
    public static void attach(TextView tv) {
        if (tv == null || tv instanceof EditText || !active(tv.getContext()) || tv.getTag(TAG) != null) return;
        tv.setTag(TAG, Boolean.TRUE);
        CharSequence cur = tv.getText();
        CharSequence tr = t(tv.getContext(), cur);
        if (tr != cur && !tr.toString().contentEquals(cur)) tv.setText(tr);
        CharSequence hint = tv.getHint();
        if (hint != null) { CharSequence th = t(tv.getContext(), hint); if (!th.toString().contentEquals(hint)) tv.setHint(th); }
        tv.addTextChangedListener(new TextWatcher() {
            boolean busy;
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable s) {
                if (busy) return;
                if (s.getSpans(0, s.length(), Object.class).length > 0 && !(s.getSpans(0, s.length(), Object.class).length == countWatchers(s))) return;
                String now = s.toString(), r = t(tv.getContext(), now);
                if (r.equals(now)) return;
                busy = true;
                try { s.replace(0, s.length(), r); } finally { busy = false; }
            }
        });
    }

    /** Spans an Editable carries for its own text watchers (not styling). */
    private static int countWatchers(Editable s) {
        int n = 0;
        for (Object o : s.getSpans(0, s.length(), Object.class)) {
            String cn = o.getClass().getName();
            if (o instanceof TextWatcher || cn.contains("ChangeWatcher") || cn.contains("Selection") || cn.contains("SpanWatcher")) n++;
        }
        return n;
    }

    /** Every TextView under a view. */
    public static void attachAll(View v) {
        if (v == null || !active(v.getContext())) return;
        if (v instanceof TextView) attach((TextView) v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) attachAll(g.getChildAt(i));
        }
    }
}
