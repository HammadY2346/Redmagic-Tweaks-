package com.hammad.redmagictweaks;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * "NEW" badges: settings added (or given something new: a typed value, a custom colour) recently, with the day.
 *
 * A badge shows (UiPrefs.newBadges):
 *  - until seen once (default): until the row has been on screen in its category; it stays for that visit;
 *  - 3 / 7 / 30 days: for that long after the row was first on screen (never-seen rows keep it);
 *  - off.
 * Whatever the mode, a badge is gone once the day it was added is more than {@link #MAX_AGE_DAYS} days ago, so old
 * tags expire on their own. A Feature can also carry its own day (Feature.since), which wins over this list.
 */
public final class WhatsNew {
    private WhatsNew() {}

    public static final int MAX_AGE_DAYS = 45;
    public static final int NEW_SETTING = 0, TYPED_VALUE = 1, CUSTOM_COLOUR = 2;
    public static final String[] MODES = {"Until I've seen them once", "For 3 days after I first see them", "For 7 days after I first see them",
            "For 30 days after I first see them", "Don't show NEW badges"};
    private static final int[] MODE_DAYS = {0, 3, 7, 30, -1};

    private static final Map<String, String> DAY = new HashMap<>();
    private static final Map<String, Integer> WHAT = new HashMap<>();
    private static void put(String key, String day, int what) { DAY.put(key, day); WHAT.put(key, what); }
    static {
        put("game_view", "2026-10-22", NEW_SETTING);
        put("game_launch_boost", "2026-10-23", NEW_SETTING);
        put("game_launch_boost_games", "2026-10-23", NEW_SETTING);
        put("game_gfrc_target", "2026-10-23", NEW_SETTING);
        put("vol_side", "2026-10-22", NEW_SETTING);
        put("vol_margin", "2026-10-22", NEW_SETTING);
        put("game_view_games", "2026-10-22", NEW_SETTING);
        put("lock_video", "2026-10-22", NEW_SETTING);
        put("lock_video_on", "2026-10-22", NEW_SETTING);
        put("lock_video_when", "2026-10-22", NEW_SETTING);
        put("lock_video_end", "2026-10-22", NEW_SETTING);
        put("lock_video_sound", "2026-10-22", NEW_SETTING);
        put("lock_video_fit", "2026-10-22", NEW_SETTING);
        put("aod_battery", "2026-10-21", NEW_SETTING);
        put("aod_brightness", "2026-10-21", NEW_SETTING);
        put("aod_burn_in", "2026-10-21", NEW_SETTING);
        put("aod_clock_color", "2026-10-22", NEW_SETTING);
        put("aod_clock_position", "2026-10-22", NEW_SETTING);
        put("aod_clock_size", "2026-10-21", NEW_SETTING);
        put("aod_fade", "2026-10-21", NEW_SETTING);
        put("aod_low_battery", "2026-10-21", NEW_SETTING);
        put("aod_lunar", "2026-10-21", NEW_SETTING);
        put("aod_notif_app_icons", "2026-10-21", NEW_SETTING);
        put("aod_notif_count", "2026-10-21", NEW_SETTING);
        put("aod_notif_size", "2026-10-21", NEW_SETTING);
        put("aod_notifications", "2026-10-21", NEW_SETTING);
        put("aod_oneui_clock", "2026-10-22", NEW_SETTING);
        put("aod_oneui_face", "2026-10-22", NEW_SETTING);
        put("aod_oneui_size", "2026-10-22", NEW_SETTING);
        put("aod_power_saving", "2026-10-21", NEW_SETTING);
        put("aod_tap_time", "2026-10-21", NEW_SETTING);
        put("bat_health", "2026-10-22", NEW_SETTING);
        put("bat_separation_100", "2026-10-23", NEW_SETTING);
        put("fw_google_intl", "2026-10-23", NEW_SETTING);
        put("game_view_only_game", "2026-10-23", NEW_SETTING);
        put("game_view_gs_stretch", "2026-10-23", NEW_SETTING);
        put("game_view_gs_custom", "2026-10-23", NEW_SETTING);
        put("game_view_strength", "2026-10-23", NEW_SETTING);
        put("clk_color", "2026-10-22", CUSTOM_COLOUR);
        put("clk_offset_x", "2026-10-22", TYPED_VALUE);
        put("clk_offset_y", "2026-10-22", TYPED_VALUE);
        put("clk_size", "2026-10-22", TYPED_VALUE);
        put("clk_spacing", "2026-10-22", TYPED_VALUE);
        put("cos_battery_scale", "2026-10-22", TYPED_VALUE);
        put("fpc_anim_interval", "2026-10-22", TYPED_VALUE);
        put("fpc_anim_size", "2026-10-22", TYPED_VALUE);
        put("fpc_icon_color", "2026-10-22", CUSTOM_COLOUR);
        put("fpc_icon_dx", "2026-10-22", NEW_SETTING);
        put("fpc_icon_dy", "2026-10-22", NEW_SETTING);
        put("fpc_icon_opacity", "2026-10-22", NEW_SETTING);
        put("fpc_icon_size", "2026-10-22", TYPED_VALUE);
        put("fw_volume_boost", "2026-10-22", TYPED_VALUE);
        put("game_ai_speed", "2026-10-22", TYPED_VALUE);
        put("game_assist_defer", "2026-10-22", NEW_SETTING);
        put("game_fullscreen", "2026-10-22", NEW_SETTING);
        put("game_gfrc_all", "2026-10-22", NEW_SETTING);
        put("game_gfrc_target", "2026-10-22", NEW_SETTING);
        put("game_launch_boost", "2026-10-22", NEW_SETTING);
        put("launcher_cols", "2026-10-22", TYPED_VALUE);
        put("launcher_drawer_label_size", "2026-10-22", TYPED_VALUE);
        put("launcher_drawer_page_pad", "2026-10-22", TYPED_VALUE);
        put("launcher_folder_color", "2026-10-22", CUSTOM_COLOUR);
        put("launcher_folder_cols", "2026-10-22", NEW_SETTING);
        put("launcher_folder_opacity", "2026-10-22", TYPED_VALUE);
        put("launcher_folder_rows", "2026-10-22", NEW_SETTING);
        put("launcher_folder_view_cols", "2026-10-22", TYPED_VALUE);
        put("launcher_folder_view_radius", "2026-10-22", TYPED_VALUE);
        put("launcher_hide_labels", "2026-10-22", NEW_SETTING);
        put("launcher_hotseat", "2026-10-22", TYPED_VALUE);
        put("launcher_icon_pack", "2026-10-22", NEW_SETTING);
        put("launcher_icon_shape", "2026-10-22", NEW_SETTING);
        put("launcher_label_size", "2026-10-22", NEW_SETTING);
        put("launcher_paged_rows", "2026-10-22", TYPED_VALUE);
        put("launcher_rows", "2026-10-22", TYPED_VALUE);
        put("lock_charge_interval", "2026-10-22", TYPED_VALUE);
        put("lock_clock_color", "2026-10-22", NEW_SETTING);
        put("lock_clock_dx", "2026-10-22", TYPED_VALUE);
        put("lock_clock_dy", "2026-10-22", TYPED_VALUE);
        put("lock_clock_opacity", "2026-10-22", TYPED_VALUE);
        put("lock_clock_rot", "2026-10-22", TYPED_VALUE);
        put("lock_clock_size", "2026-10-22", TYPED_VALUE);
        put("lock_date_color", "2026-10-22", NEW_SETTING);
        put("lock_date_dx", "2026-10-22", TYPED_VALUE);
        put("lock_date_dy", "2026-10-22", TYPED_VALUE);
        put("lock_date_opacity", "2026-10-22", TYPED_VALUE);
        put("lock_date_rot", "2026-10-22", TYPED_VALUE);
        put("lock_date_size", "2026-10-22", TYPED_VALUE);
        put("lock_oneui_size", "2026-10-22", TYPED_VALUE);
        put("menu_oneui_home", "2026-10-22", NEW_SETTING);
        put("notif_corners", "2026-10-22", TYPED_VALUE);
        put("notif_gap", "2026-10-22", NEW_SETTING);
        put("notif_icon_limit", "2026-10-22", TYPED_VALUE);
        put("notif_opacity", "2026-10-22", TYPED_VALUE);
        put("notif_side", "2026-10-22", NEW_SETTING);
        put("notif_tint", "2026-10-22", NEW_SETTING);
        put("pwr_dim", "2026-10-22", NEW_SETTING);
        put("pwr_layout", "2026-10-22", NEW_SETTING);
        put("pwr_oneui_colors", "2026-10-22", CUSTOM_COLOUR);
        put("pwr_position", "2026-10-22", NEW_SETTING);
        put("qs_cc_cols", "2026-10-22", TYPED_VALUE);
        put("qs_cc_slider_size", "2026-10-22", TYPED_VALUE);
        put("qs_cc_tile_gap", "2026-10-22", TYPED_VALUE);
        put("qs_cols", "2026-10-22", TYPED_VALUE);
        put("qs_land_cols", "2026-10-22", TYPED_VALUE);
        put("qs_land_rows", "2026-10-22", TYPED_VALUE);
        put("qs_rows", "2026-10-22", TYPED_VALUE);
        put("qs_slider_color", "2026-10-22", NEW_SETTING);
        put("qs_tile_color", "2026-10-22", NEW_SETTING);
        put("recents_card_size", "2026-10-22", TYPED_VALUE);
        put("recents_corners", "2026-10-22", TYPED_VALUE);
        put("sb_battery_interval", "2026-10-22", TYPED_VALUE);
        put("sb_height", "2026-10-22", TYPED_VALUE);
        put("sb_net_color", "2026-10-22", NEW_SETTING);
        put("sb_net_dx", "2026-10-22", NEW_SETTING);
        put("sb_net_dy", "2026-10-22", NEW_SETTING);
        put("sb_net_size", "2026-10-22", TYPED_VALUE);
        put("sb_side_padding", "2026-10-22", TYPED_VALUE);
        put("sbi_icon_color", "2026-10-22", CUSTOM_COLOUR);
        put("sbi_icon_offset_x", "2026-10-22", TYPED_VALUE);
        put("sbi_icon_offset_y", "2026-10-22", TYPED_VALUE);
        put("sbi_icon_opacity", "2026-10-22", TYPED_VALUE);
        put("sbi_icon_scale", "2026-10-22", TYPED_VALUE);
        put("sbi_notif_opacity", "2026-10-22", TYPED_VALUE);
        put("sbi_notif_scale", "2026-10-22", TYPED_VALUE);
        put("ui_qs_search_button", "2026-10-21", NEW_SETTING);
        put("ui_saved_layouts", "2026-10-21", NEW_SETTING);
        put("vol_oneui_color", "2026-10-22", CUSTOM_COLOUR);
    }

    /** The day a setting was added or got something new, or null. */
    public static String since(Features.Feature f) { return f == null ? null : f.since != null ? f.since : DAY.get(f.key); }
    public static String since(String key) { return DAY.get(key); }

    /** What is new about it (shown in its explanation). */
    public static String what(String key) {
        Integer w = WHAT.get(key);
        if (w == null) return null;
        switch (w) {
            case TYPED_VALUE: return "New: besides the presets it takes any value (slider or typed).";
            case CUSTOM_COLOUR: return "New: besides the presets it takes any colour (Custom colour…).";
            default: return "New setting.";
        }
    }

    public static boolean isNew(Context c, Features.Feature f) { return f != null && isNew(c, f.key, since(f)); }

    /** True while the badge shows (see the class comment); doesn't mark anything as seen. */
    public static boolean isNew(Context c, String key, String day) {
        if (day == null) return false;
        int mode = UiPrefs.newBadges(c);
        if (mode < 0 || mode >= MODE_DAYS.length || MODE_DAYS[mode] < 0) return false;
        long added = dayMillis(day);
        long now = System.currentTimeMillis();
        if (added <= 0 || now - added > MAX_AGE_DAYS * 86400000L) return false;
        long seen = UiPrefs.firstSeen(c, key);
        if (seen == 0L) return true;
        int days = MODE_DAYS[mode];
        return days > 0 && now - seen < days * 86400000L;
    }

    /** All keys with a day (for "mark all as seen"). */
    public static java.util.Set<String> keys() { return DAY.keySet(); }

    private static final Map<String, Long> PARSED = new HashMap<>();

    private static synchronized long dayMillis(String day) {
        Long cached = PARSED.get(day);
        if (cached != null) return cached;
        long v = parseDay(day);
        PARSED.put(day, v);
        return v;
    }

    private static long parseDay(String day) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date d = f.parse(day);
            return d == null ? 0L : d.getTime();
        } catch (Throwable t) { return 0L; }
    }

    /** A small "NEW" pill after {@code text} (rows, category cards, menu items). */
    public static CharSequence withPill(Context c, CharSequence text, String pill) {
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(text);
        b.append("  ");
        int start = b.length();
        b.append(pill);
        b.setSpan(new Pill(c), start, b.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    /** Draws its text small and bold, white on the accent colour, in a rounded pill. */
    static final class Pill extends android.text.style.ReplacementSpan {
        private final int bg, fg; private final float density;
        Pill(Context c) {
            int a;
            try { a = c.getColor(R.color.accent); } catch (Throwable t) { a = 0xFFE8343B; }
            bg = a; fg = 0xFFFFFFFF; density = c.getResources().getDisplayMetrics().density;
        }
        private float textSize(android.graphics.Paint p) { return p.getTextSize() * 0.66f; }
        @Override public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end, android.graphics.Paint.FontMetricsInt fm) {
            android.graphics.Paint p = new android.graphics.Paint(paint);
            p.setTextSize(textSize(paint));
            p.setFakeBoldText(true);
            return Math.round(p.measureText(text, start, end) + 12 * density);
        }
        @Override public void draw(android.graphics.Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, android.graphics.Paint paint) {
            android.graphics.Paint p = new android.graphics.Paint(paint);
            p.setTextSize(textSize(paint));
            p.setFakeBoldText(true);
            p.setLetterSpacing(0.06f);
            float w = p.measureText(text, start, end) + 12 * density;
            android.graphics.Paint.FontMetrics m = p.getFontMetrics();
            float th = m.descent - m.ascent;
            float h = th + 4 * density;
            float cy = (top + bottom) / 2f;
            android.graphics.RectF r = new android.graphics.RectF(x, cy - h / 2f, x + w, cy + h / 2f);
            android.graphics.Paint bp = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            bp.setColor(bg);
            canvas.drawRoundRect(r, h / 2f, h / 2f, bp);
            p.setColor(fg);
            p.setAntiAlias(true);
            canvas.drawText(text, start, end, x + 6 * density, cy - (m.ascent + m.descent) / 2f, p);
        }
    }
}
