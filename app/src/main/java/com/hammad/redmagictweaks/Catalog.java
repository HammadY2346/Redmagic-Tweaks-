package com.hammad.redmagictweaks;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the app shows: 13 categories plus Advanced, by what you see on the phone rather than by the app a hook patches.
 * The settings themselves (keys, titles, choices, descriptions) stay in the *Features sections, so saved layouts and the
 * hooks are unchanged; this class only arranges them into categories and groups, marks fine-tuning that Simple mode
 * hides, merges the two search-button switches into one choice, and says what each setting needs before it shows up
 * (nothing, a System UI restart, an app restart or a reboot).
 *
 * Every feature of every section appears exactly once: one not placed below lands in a "More" group of the category
 * of its section (so a new setting, or one only the private build has, is never lost).
 */
public final class Catalog {
    private Catalog() {}

    /** What a changed setting needs before it shows up. */
    public enum Restart { NONE, LIVE, SYSTEM_UI, APP, REBOOT }

    public static final class Item {
        public final Features.Feature feature;
        /** The section the feature comes from (its packages are what restarts). */
        public final Features.Section origin;
        /** Fine-tuning (nudges, offsets, spacing, rotation, the stock lock clock): hidden in Simple mode. */
        public final boolean fine;
        public final Restart restart;
        Item(Features.Feature feature, Features.Section origin, boolean fine) {
            this.feature = feature; this.origin = origin; this.fine = fine; this.restart = restartOf(feature, origin);
        }
        public String key() { return feature.key; }
        /** Apps to restart for an APP setting (never "android"). */
        public String[] packages() {
            List<String> l = new ArrayList<>();
            for (String p : origin.packages) if (!"android".equals(p)) l.add(p);
            return l.toArray(new String[0]);
        }
    }

    public static final class Group {
        public final String title;
        public final List<Item> items = new ArrayList<>();
        Group(String title) { this.title = title; }
    }

    public static final class Category {
        public final String id, title, subtitle;
        public final int icon;
        /** Shown only in Advanced mode, with a warning when opened. */
        public final boolean advanced;
        public final List<Group> groups = new ArrayList<>();
        Category(String id, String title, String subtitle, int icon, boolean advanced) {
            this.id = id; this.title = title; this.subtitle = subtitle; this.icon = icon; this.advanced = advanced;
        }
        public List<Item> items() {
            List<Item> l = new ArrayList<>();
            for (Group g : groups) l.addAll(g.items);
            return l;
        }
    }

    // ============================================================================================ virtual settings

    /** "Search button" as one choice; stores the two switches it replaces (sb_qs_search, qs_cc_hide_search). */
    public static final String SEARCH_BUTTON = "ui_qs_search_button";
    private static final String SEARCH_SHOW = "sb_qs_search", SEARCH_HIDE = "qs_cc_hide_search";
    public static final String[] SEARCH_CHOICES = {"Stock", "Show it in Quick Settings", "Hide it in Control Center"};
    /** Opens the saved layouts (Looks page). */
    public static final String SAVED_LAYOUTS = "ui_saved_layouts";

    private static final Features.Section VIRTUAL = new Features.Section("ui", "", "", R.drawable.ic_settings, new String[]{"com.android.systemui"},
            new Features.Feature(SEARCH_BUTTON, "Search button", "The magnifier button at the top of the panel: stock, also shown in the classic Quick Settings, or removed from Control Center. Restart System UI.",
                    Features.Type.CHOICE, SEARCH_CHOICES, 0),
            new Features.Feature(SAVED_LAYOUTS, "Saved layouts…", "Save everything you have set up as a named layout, switch between layouts, export or import them as files.",
                    Features.Type.ACTION, null, 0));

    public static boolean isVirtual(String key) { return key.startsWith("ui_"); }

    // ============================================================================================ settings that need a switch

    /** Settings that only do something while another switch is on: setting -> the switch. */
    private static final Map<String, String> NEEDS = new HashMap<>();
    static {
        for (String k : new String[]{"pwr_oneui_size", "pwr_oneui_shape", "pwr_oneui_colors", "pwr_oneui_labels"}) NEEDS.put(k, "pwr_oneui8");
        for (String k : new String[]{"lock_oneui_face", "lock_oneui_size", "lock_oneui_position"}) NEEDS.put(k, "lock_oneui_clock");
    }

    /** The switch a setting needs, or null. */
    public static String needs(String key) { return NEEDS.get(key); }

    /** The switch this setting needs while that switch is off; null when the setting works now. */
    public static Item blockedBy(Context c, Item it) {
        String p = NEEDS.get(it.key());
        return p != null && !Prefs.on(c, p) ? item(p) : null;
    }

    /** True when other settings need this switch (its row redraws them when it flips). */
    public static boolean isParent(String key) { return NEEDS.containsValue(key); }

    public static int choice(Context c, Item it) {
        if (SEARCH_BUTTON.equals(it.key())) return Prefs.on(c, SEARCH_SHOW) ? 1 : Prefs.on(c, SEARCH_HIDE) ? 2 : 0;
        int v = Prefs.choice(c, it.key());
        return v < 0 || it.feature.choices == null || v >= it.feature.choices.length ? 0 : v;
    }

    public static void setChoice(Context c, Item it, int v) {
        if (SEARCH_BUTTON.equals(it.key())) { Prefs.set(c, SEARCH_SHOW, v == 1); Prefs.set(c, SEARCH_HIDE, v == 2); return; }
        Prefs.set(c, it.key(), v);
    }

    /** True when the setting differs from stock. */
    public static boolean changed(Context c, Item it) {
        switch (it.feature.type) {
            case SWITCH: return Prefs.on(c, it.key());
            case CHOICE: return choice(c, it) != 0;
            default: return false;
        }
    }

    public static void reset(Context c, Item it) {
        if (it.feature.type == Features.Type.SWITCH) Prefs.set(c, it.key(), false);
        else if (it.feature.type == Features.Type.CHOICE) setChoice(c, it, 0);
    }

    // ============================================================================================ the arrangement

    private static Category[] all;
    private static final Map<String, Item> BY_KEY = new HashMap<>();

    /** Category of a section's features that are not placed explicitly. */
    private static String home(Features.Section s) {
        switch (s.id) {
            case "presets": return "looks";
            case "statusbar": case "sbclock": case "sbicons": case "cosmetics": return "statusbar";
            case "qs": return "quickpanel";
            case "surface": return "notifications";
            case "fpcustom": return "fingerprint";
            case "lockscreen": case "theme": return "lockscreen";
            case "systemui": return "charging";
            case "launcher": case "recents": return "home";
            case "game": return "gaming";
            case "bootanim": case "themestore": return "fonts";
            default: return "system";
        }
    }

    public static synchronized Category[] all() {
        if (all != null) return all;
        Map<String, Features.Section> origin = new HashMap<>();
        Map<String, Features.Feature> feature = new HashMap<>();
        for (Features.Section s : Features.SECTIONS) {
            for (Features.Feature f : s.features) {
                if (feature.containsKey(f.key)) continue;     // the status bar page lists a few keys of other sections again
                feature.put(f.key, f); origin.put(f.key, s);
            }
        }
        for (Features.Feature f : VIRTUAL.features) { feature.put(f.key, f); origin.put(f.key, VIRTUAL); }
        // keys merged into a virtual setting are not shown by themselves
        Set<String> placed = new LinkedHashSet<>();
        placed.add(SEARCH_SHOW); placed.add(SEARCH_HIDE);

        List<Category> out = new ArrayList<>();
        Builder b = new Builder(out, feature, origin, placed);
        b.cat("looks", "Looks", "One UI 8 look, stock look and your saved layouts", R.drawable.ic_cat_looks, false)
            .group("Looks", "preset_one_ui", "preset_stock_look", SAVED_LAYOUTS)
            .group("Home screen", "preset_one_ui_launcher", "preset_stock_launcher");
        b.cat("statusbar", "Status bar", "Clock, battery, icons and network speed", R.drawable.ic_cat_statusbar, false)
            .group("Clock", "clk_font", "clk_bold", "clk_italic", "clk_color", "clk_size", "sb_clock_seconds", "sb_clock_format", "clk_date", "clk_date_pos",
                    "clk_date_sep", "clk_date_case", "clk_hide", "~clk_spacing", "~clk_offset_x", "~clk_offset_y")
            .group("Battery", "cos_battery_style", "sb_battery_style", "cos_battery_scale", "cos_battery_percent", "cos_battery_stock_readable",
                    "cos_battery_hide_charge", "sb_battery_info", "sb_battery_info_pos", "sb_battery_interval")
            .group("Icons", "sbi_icon_pack", "sbi_wifi_style", "sbi_signal_style", "sbi_icon_color", "sbi_icon_scale", "sbi_icon_opacity",
                    "~sbi_icon_offset_x", "~sbi_icon_offset_y")
            .group("Hide icons", "sbi_hide_system", "sb_hide_wifi", "sb_hide_mobile", "sb_hide_bluetooth", "sb_hide_hd", "sb_hide_vpn", "sb_hide_nfc",
                    "sb_hide_alarm", "sb_hide_vibrate", "sb_hide_headset", "sb_hide_hotspot", "sb_hide_location", "sb_hide_cast", "sb_hide_rotate",
                    "sb_hide_data_saver", "sb_hide_ethernet", "sb_hide_ime", "sb_hide_work", "sb_hide_privacy", "sbi_hide_fan", "sbi_hide_liquid", "sbi_hide_refresh")
            .group("Network speed", "sb_net_show", "sb_net_fast", "sb_net_style", "sb_net_size")
            .group("Notification icons", "notif_icon_limit", "notif_overflow_count", "sbi_notif_scale", "sbi_notif_opacity", "sbi_hide_notif")
            .group("Height and margins", "sb_height", "sb_side_padding");
        b.cat("quickpanel", "Quick panel", "Control Center, tiles and sliders", R.drawable.ic_cat_quickpanel, false)
            .group("Control Center", "qs_cc_enable", "qs_cc_layout", "qs_cc_cols", "qs_cc_tile_gap", "qs_cc_slider_pos", "qs_cc_slider_size", "qs_cc_volume_style")
            .group("One UI 8 layout", "qs_cc_oneui_big_clock", "qs_cc_oneui_no_header", "qs_cc_oneui_no_media", "qs_cc_oneui_no_shortcuts")
            .group("Header", SEARCH_BUTTON, "qs_cc_hide_edit", "qs_cc_hide_user", "qs_cc_hide_power", "qs_cc_hide_settings", "qs_cc_hide_date", "sb_qs_carrier")
            .group("Classic panel", "qs_cols", "qs_rows", "~qs_land_cols", "~qs_land_rows", "qs_top_layout", "qs_label_pos", "qs_label_lines")
            .group("Brightness", "qs_brightness_style", "qs_brightness_pos", "qs_brightness_no_sun")
            .group("Tile look", "qs_shape_small", "qs_shape_large", "qs_tile_style");
        b.cat("notifications", "Notifications", "Card look, heads-up and what stays after unlock", R.drawable.ic_cat_notifications, false)
            .group("Cards", "notif_style", "notif_opacity", "notif_corners")
            .group("Behaviour", "notif_no_auto_headsup", "lock_keep_notifs", "fw_quiet_when_unlocked", "sb_notif_native_icon");
        b.cat("lockscreen", "Lock screen & AOD", "One UI clock, widgets and always-on display", R.drawable.ic_cat_lockscreen, false)
            .group("One UI clock", "lock_oneui_clock", "lock_oneui_face", "lock_oneui_size", "lock_oneui_position", "lock_oneui_tiles", "lock_oneui_shortcuts")
            .group("Lock screen", "lock_hide_statusbar", "lock_hide_unlock_hint", "lock_hide_weather_widget", "lock_hide_alarm_widget", "lock_hide_battery_widget",
                    "lock_double_tap_sleep", "lock_double_tap_statusbar", "fw_no_72h_lock")
            .group("Always-on display", "lock_aod_seconds", "lock_aod_datetime")
            .group("Stock clock and date", "~lock_clock_hide", "~lock_clock_size", "~lock_clock_opacity", "~lock_clock_dx", "~lock_clock_dy", "~lock_clock_rot",
                    "~lock_date_hide", "~lock_date_size", "~lock_date_opacity", "~lock_date_dx", "~lock_date_dy", "~lock_date_rot");
        b.cat("fingerprint", "Fingerprint", "Unlock animation, icon and collab styles", R.drawable.ic_fingerprint, false)
            .group("Unlock animation", "fpc_anim", "fpc_anim_set", "fpc_anim_interval", "fpc_anim_size", "fp_import")
            .group("Fingerprint icon", "fpc_icon", "fpc_icon_style", "fpc_icon_color", "fpc_icon_size", "fpc_icon_hide")
            .group("Collaboration styles", "fp_chisa", "fp_fighting", "fp_wuthering_waves", "fp_lty", "fp_golden_saga")
            .group("Unlocking", "fp_unlock_all");
        b.cat("volume", "Volume & power", "Volume panel, power menu and volume limits", R.drawable.ic_cat_volume, false)
            .group("Volume panel", "vol_oneui8", "vol_oneui_width", "vol_oneui_height", "vol_oneui_shape", "vol_oneui_color", "vol_oneui_track",
                    "vol_oneui_icon", "vol_oneui_level", "vol_style")
            .group("Volume", "sb_volume_no_warning", "fw_no_safe_volume", "fw_volume_boost", "fw_dnd_volume", "sb_volume_no_vibrate")
            .group("One UI 8 power menu", "pwr_oneui8", "pwr_oneui_size", "pwr_oneui_shape", "pwr_oneui_colors", "pwr_oneui_labels")
            .group("Power", "pwr_style", "fw_power_assist");
        b.cat("charging", "Charging & battery", "Charging animation, readouts and battery options", R.drawable.ic_cat_charging, false)
            .group("Charging animation", "charge_set", "charge_import", "charge_every_lock")
            .group("Lock screen readout", "lock_charge_info", "lock_charge_interval")
            .group("Battery app", "bat_super_mode", "bat_refresh_rate", "bat_charge_separation", "bat_long_charge", "bat_time", "bat_5g_save");
        b.cat("display", "Display & performance", "Refresh rate, screen timeout and small windows", R.drawable.ic_cat_display, false)
            .group("Refresh rate", "fw_auto_rate_max", "fw_force_rate", "fw_force_rate_video60")
            .group("Screen", "set_timeout_long", "fw_screen_never_off")
            .group("Windows", "fw_freeform", "fw_freeform_nolimit", "fw_orientation_free");
        b.cat("home", "Home, drawer & recents", "Recent apps styles, paged drawer, folders and grid", R.drawable.ic_cat_home, false)
            .group("Recent apps", RecentsArtKeys.STYLE, RecentsArtKeys.HEADER, RecentsArtKeys.CLEAR, RecentsArtKeys.CORNERS, RecentsArtKeys.SIZE)
            .group("App drawer", "launcher_paged_drawer", "launcher_paged_rows", "launcher_drawer_dots", "launcher_drawer_custom_order", "launcher_drawer_fixed_slots",
                    "launcher_drawer_page_pad", "launcher_drawer_search_pos", "launcher_drawer_hide_search", "launcher_drawer_hide_menu",
                    "launcher_drawer_label_size", "launcher_hide_fastscroller")
            .group("Drawer folders", "launcher_folder_shape", "launcher_folder_color", "launcher_folder_opacity", "launcher_folder_grid", "launcher_folder_label",
                    "launcher_folder_open_style", "launcher_folder_view_bg", "launcher_folder_view_radius", "launcher_folder_view_cols", "launcher_folder_view_no_names")
            .group("Home screen", "launcher_cols", "launcher_rows", "launcher_hotseat", "launcher_hotseat_text", "launcher_two_line", "launcher_circle_scroll",
                    "launcher_rotation");
        b.cat("gaming", "Gaming", "Game Space, key mapping, fan light", R.drawable.ic_game, false)
            .group("Game Space", "game_unfreeze", "game_macro_any", "game_combo_speed", "game_tgk_link_all", "game_sr_balance", "game_sr_coexist",
                    "game_no_dialogs", "game_keep_redmagic_time", "game_ai_speed", "game_record_free", "game_active_mode_keep")
            .group("Controls and lights", "key_continuous", "key_xgravity", "light_fan", "light_new_protocol")
            .group("Magic Voice", "mv_calls");
        b.cat("fonts", "Fonts, themes & boot", "System UI font, boot animation, Theme Store", R.drawable.ic_cat_fonts, false)
            .group("Fonts", "cos_sysui_font", "cos_clock_font", "cos_font_import")
            .group("Boot animation", "bootanim_choice", "bootanim_import", "bootanim_status")
            .group("Themes", "theme_variant", "ts_all_collab_packs", "ts_pack_scope", "ts_preset_any_pack", "ts_no_login");
        b.cat("system", "System & apps", "Settings, gestures, connections and app extras", R.drawable.ic_settings, false)
            .group("Settings app", "set_dev_hidden", "set_usb_mode", "set_usb_no_popup", "set_adb_install_noauth", "set_net_mode", "set_any_launcher")
            .group("Gestures", "ges_double_tap_sleep", "ges_raise_wake", "ges_split_swipe", "ges_black_letters", "ges_range_shot", "ges_pocket",
                    "ges_shake_light", "ges_motion")
            .group("Connections", "fw_airplane_wifi", "fw_airplane_bt", "nfc_mute", "nfc_screen_off", "cast_high_fps", "cast_all_features")
            .group("Apps", "clone_any", "clone_no_limit", "hs_no_ads", "hs_landscape", "inst_skip_scan", "inst_no_store_nag", "inst_hide_pure")
            .group("Privacy and system", "fw_no_telemetry", "fw_no_alert_notif", "sb_clipboard_overlay", "sb_usb_debug_auto_allow", "sb_onehand_no_hint");
        b.cat("advanced", "Advanced", "Tweaks that weaken protections or push the hardware", R.drawable.ic_cat_advanced, true)
            .group("Use with care", "fw_secure_screenshot", "fw_untrusted_touch", "fw_no_thermal_limit", "fw_phantom_off", "inst_no_risk");

        // everything else: a "More" group in its section's category
        Map<String, Category> byId = new LinkedHashMap<>();
        for (Category c : out) byId.put(c.id, c);
        for (Features.Section s : Features.SECTIONS) {
            for (Features.Feature f : s.features) {
                if (placed.contains(f.key)) continue;
                placed.add(f.key);
                Category c = byId.get(home(s));
                Group more = null;
                for (Group g : c.groups) if (g.title.equals("More")) more = g;
                if (more == null) { more = new Group("More"); c.groups.add(more); }
                Item it = new Item(f, s, false);
                more.items.add(it);
                BY_KEY.put(f.key, it);
            }
        }
        all = out.toArray(new Category[0]);
        return all;
    }

    /** Recent apps keys without loading the hook-side class in the app's static init order. */
    private static final class RecentsArtKeys {
        static final String STYLE = com.hammad.redmagictweaks.xposed.RecentsArt.KEY_STYLE, HEADER = com.hammad.redmagictweaks.xposed.RecentsArt.KEY_HEADER,
                CLEAR = com.hammad.redmagictweaks.xposed.RecentsArt.KEY_CLEAR, CORNERS = com.hammad.redmagictweaks.xposed.RecentsArt.KEY_CORNERS,
                SIZE = com.hammad.redmagictweaks.xposed.RecentsArt.KEY_SIZE;
    }

    private static final class Builder {
        final List<Category> out; final Map<String, Features.Feature> feature; final Map<String, Features.Section> origin; final Set<String> placed;
        Category cur;
        Builder(List<Category> out, Map<String, Features.Feature> feature, Map<String, Features.Section> origin, Set<String> placed) {
            this.out = out; this.feature = feature; this.origin = origin; this.placed = placed;
        }
        Builder cat(String id, String title, String subtitle, int icon, boolean advanced) {
            cur = new Category(id, title, subtitle, icon, advanced);
            out.add(cur);
            return this;
        }
        Builder group(String title, String... keys) {
            Group g = new Group(title);
            for (String k : keys) {
                boolean fine = k.startsWith("~");
                String key = fine ? k.substring(1) : k;
                Features.Feature f = feature.get(key);
                if (f == null || placed.contains(key)) continue;      // not in this build, or already shown
                placed.add(key);
                Item it = new Item(f, origin.get(key), fine);
                g.items.add(it);
                BY_KEY.put(key, it);
            }
            if (!g.items.isEmpty()) cur.groups.add(g);
            return this;
        }
    }

    // ============================================================================================ lookups

    public static Category category(String id) {
        for (Category c : all()) if (c.id.equals(id)) return c;
        return null;
    }

    public static Item item(String key) {
        all();
        return BY_KEY.get(key);
    }

    /** Categories for the home screen: Advanced only in Advanced mode. */
    public static List<Category> visible(Context c) {
        List<Category> l = new ArrayList<>();
        boolean adv = UiPrefs.advanced(c);
        for (Category cat : all()) if (adv || !cat.advanced) l.add(cat);
        return l;
    }

    /** The groups of a category as shown: Simple mode leaves out fine-tuning (and a group that only had that). */
    public static List<Group> shown(Context c, Category cat) {
        boolean adv = UiPrefs.advanced(c);
        List<Group> l = new ArrayList<>();
        for (Group g : cat.groups) {
            Group v = new Group(g.title);
            for (Item it : g.items) if (adv || !it.fine) v.items.add(it);
            if (!v.items.isEmpty()) l.add(v);
        }
        return l;
    }

    /** What a category holds, by its group names ("Clock · Battery · Icons …"). */
    public static String contents(Category cat) {
        StringBuilder b = new StringBuilder();
        for (Group g : cat.groups) {
            if (g.title.equals("More")) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(g.title);
        }
        return b.toString();
    }

    /** Settings that can be changed (switches and choices; not buttons such as imports and presets). */
    public static boolean countable(Item it) { return it.feature.type != Features.Type.ACTION; }

    public static int total(Category cat) {
        int n = 0;
        for (Item it : cat.items()) if (countable(it)) n++;
        return n;
    }

    /** Every setting in the app, Advanced included. */
    public static int total(Context c) {
        int n = 0;
        for (Category cat : all()) n += total(cat);
        return n;
    }

    public static int changedCount(Context c, Category cat) {
        int n = 0;
        for (Item it : cat.items()) if (changed(c, it)) n++;
        return n;
    }

    /** Every setting whose title, description, group or category matches. */
    public static Category search(Context c, String q) {
        String needle = q.toLowerCase(Locale.ROOT).trim();
        Category res = new Category("search", "Search", "", R.drawable.ic_search, false);
        boolean adv = UiPrefs.advanced(c);
        for (Category cat : all()) {
            if (cat.advanced && !adv) continue;
            for (Group g : cat.groups) {
                Group hit = new Group(cat.title + " · " + g.title);
                for (Item it : g.items) {
                    String hay = (it.feature.title + " " + (it.feature.desc == null ? "" : it.feature.desc) + " " + g.title + " " + cat.title).toLowerCase(Locale.ROOT);
                    if (hay.contains(needle) && (adv || !it.fine)) hit.items.add(it);
                }
                if (!hit.items.isEmpty()) res.groups.add(hit);
            }
        }
        return res;
    }

    // ============================================================================================ restarts

    private static Restart restartOf(Features.Feature f, Features.Section s) {
        if (f.type == Features.Type.ACTION || s == null) return Restart.NONE;
        if (s.id.equals("presets") || s.id.equals("bootanim")) return Restart.NONE;
        String d = f.desc == null ? "" : f.desc;
        boolean framework = s.packages.length == 1 && "android".equals(s.packages[0]);
        if (framework) return Restart.REBOOT;
        if (d.contains("Applies immediately") || d.contains("Applies the next time") || d.contains("Applies on the next") || d.contains("Reopen")
                || d.contains("on the next redraw") || d.contains("Applies within"))
            return Restart.LIVE;
        for (String p : s.packages) if ("com.android.systemui".equals(p)) return Restart.SYSTEM_UI;
        return Restart.APP;
    }

    /** Short label for the badge on a row, or null when nothing has to be restarted. */
    public static String badge(Item it) {
        switch (it.restart) {
            case SYSTEM_UI: return "System UI";
            case REBOOT: return "Reboot";
            case APP: return "App restart";
            default: return null;
        }
    }

    /** Every package of every section (the hooked apps), without the framework. */
    public static String[] allPackages() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (Features.Section s : Features.SECTIONS) for (String p : s.packages) if (!"android".equals(p)) set.add(p);
        return set.toArray(new String[0]);
    }
}
