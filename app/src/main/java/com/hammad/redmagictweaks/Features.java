package com.hammad.redmagictweaks;

import java.util.ArrayList;
import java.util.List;

/** Single source of truth for every tweak: used by the settings UI and read by the hooks. */
public final class Features {
    private Features() {}

    public static final String PREFS = "tweaks";
    public static final String PKG = BuildConfig.APPLICATION_ID;

    public enum Type { SWITCH, CHOICE, ACTION }

    /** Suffixes of a choice's typed value: "<key>_c" = custom value in use, "<key>_v" = the value (in Num.unit). */
    public static final String CUSTOM = "_c", VALUE = "_v";

    /** A choice that also takes any number in a range (slider or typed), besides its presets. */
    public static final class Num {
        public final int min, max, step;
        public final String unit;
        public Num(int min, int max, int step, String unit) {
            this.min = min; this.step = Math.max(1, step); this.unit = unit;
            this.max = min + Math.max(1, (max - min) / this.step) * this.step;   // the slider needs whole steps
        }
        public String format(int v) { return v + (unit == null || unit.isEmpty() ? "" : unit.equals("%") || unit.equals("°") ? unit : " " + unit); }
    }

    public static final class Feature {
        public final String key, title, desc;
        public final Type type;
        public final String[] choices;   // CHOICE only: labels; stored value = index
        public final int def;            // CHOICE default index; SWITCH: 1 = on by default
        public Num num;                  // CHOICE: also a custom value in this range (see CUSTOM / VALUE)
        public Feature num(int min, int max, int step, String unit) { this.num = new Num(min, max, step, unit); return this; }
        /** Day it was added or got something new ("2026-10-22"), for the "NEW" badge; else WhatsNew's list decides. */
        public String since;
        public Feature since(String day) { this.since = day; return this; }
        /** A colour option: its sheet also takes any colour (ColorPick). */
        public boolean isColor() { return type == Type.CHOICE && num == null && ColorPick.is(key); }
        public Feature(String key, String title, String desc) { this(key, title, desc, Type.SWITCH, null, 0); }
        public Feature(String key, String title, String desc, Type type, String[] choices, int def) {
            // the build flavour may rename options and trim descriptions (the public build: no One UI layouts)
            this.key = key; this.title = title; this.desc = FlavorBits.desc(key, desc); this.type = type;
            this.choices = FlavorBits.labels(key, choices); this.def = def;
        }

        /** Option {@code i} is offered in this build (the public build hides the One UI looks). */
        public boolean offered(int i) {
            return choices == null || i < 0 || i >= choices.length || !FlavorBits.hiddenChoice(key, i, choices[i]);
        }
    }

    public static final class Section {
        public final String id, title, subtitle;
        public final int icon;
        public final String[] packages;
        public final Feature[] features;
        public Section(String id, String title, String subtitle, int icon, String[] packages, Feature... features) {
            this.id = id; this.title = title; this.subtitle = subtitle; this.icon = icon; this.packages = packages;
            Feature[] extra = FlavorBits.extraFeatures(id);
            if (extra == null || extra.length == 0) this.features = features;
            else {
                this.features = java.util.Arrays.copyOf(features, features.length + extra.length);
                System.arraycopy(extra, 0, this.features, features.length, extra.length);
            }
        }
    }

    // ---- keys (kept as constants so hooks never typo them) ----
    /** Fail-safe: no hook is installed anywhere while this is on (also: setprop persist.redmagictweaks.safe 1). */
    public static final String SAFE_MODE = "safe_mode";
    public static final String GAME_UNFREEZE = "game_unfreeze";
    public static final String GAME_MACRO_ANY = "game_macro_any";
    public static final String GAME_COMBO_SPEED = "game_combo_speed";
    public static final String GAME_TGK_ALL = "game_tgk_link_all";
    public static final String GAME_SR_COEXIST = "game_sr_coexist";
    public static final String GAME_SR_BALANCE = "game_sr_balance";
    public static final String GAME_NO_DIALOGS = "game_no_dialogs";
    public static final String GAME_KEEP_RMTIME = "game_keep_redmagic_time";
    public static final String GAME_AI_SPEED = "game_ai_speed";
    public static final String GAME_RECORD_FREE = "game_record_free";
    public static final String GAME_ACTIVE_KEEP = "game_active_mode_keep";
    /** Frame interpolation (Qualcomm GPP "GFRC") and super resolution for every Game Space game (system_server). */
    public static final String GAME_GFRC_ALL = "game_gfrc_all";
    /** Panel refresh rate while frame interpolation is on (system_server, ScreenRefreshRateController.getInterpFps). */
    public static final String GAME_GFRC_TARGET = "game_gfrc_target";
    public static final String[] GFRC_TARGETS = {"Stock (Super 120 Hz, Ultra the panel's top rate)", "90 Hz", "120 Hz", "144 Hz", "The panel's top rate (165 Hz with the DTBO mod)",
            "Always the maximum: Super and Ultra at the panel's top rate"};
    /** Hz per GFRC_TARGETS choice; -1 and -2 = the panel's highest rate (-2 was "Super run as Ultra", dropped: Ultra only takes 40..57 fps games). */
    public static final int[] GFRC_TARGET_HZ = {0, 90, 120, 144, -1, -2};
    /** Game Space games without a ratio picked in Game Space run full screen (system_server, no max aspect ratio). */
    public static final String GAME_FULLSCREEN = "game_fullscreen";
    /**
     * How games fill the screen (system_server, xposed/GameViewHooks): stock, full screen at the phone's own shape, or a
     * narrower shape (4:3 "iPad view", 3:2, 16:10, 16:9, 2:1) that the game renders and the panel stretches to full screen.
     * GAME_VIEW is for every Game Space game; GAME_VIEW_PKGS ("pkg=index;...") holds per-game choices (index 0 = stock).
     */
    public static final String GAME_VIEW = "game_view";
    public static final String GAME_VIEW_GAMES = "game_view_games";
    public static final String GAME_VIEW_PKGS = "game_view_pkgs";
    /** Stretched views scale only the game's own picture (its window), not the whole display. */
    public static final String GAME_VIEW_ONLY = "game_view_only_game";
    /** A ratio picked in Game Space (its own letterbox) is stretched to the full screen like GAME_VIEW_ONLY. */
    public static final String GAME_VIEW_GS = "game_view_gs_stretch";
    /** A "Custom" ratio for Game Space's Free display: preset, or typed as width / height x 100 (170 = 17:10). */
    public static final String GAME_VIEW_GS_CUSTOM = "game_view_gs_custom";
    public static final String[] GS_CUSTOM_RATIOS = {"Off", "5:4", "17:10", "9:5", "19:10"};
    public static final float[] GS_CUSTOM_RATIO = {0f, 1.25f, 1.70f, 1.80f, 1.90f};
    /** How far a stretched game window is widened (game-only stretch and Game Space ratio): full, or less with side bars. */
    public static final String GAME_VIEW_STRENGTH = "game_view_strength";
    public static final String[] VIEW_STRENGTHS = {"Full: fills the screen", "As much as 16:10 (small side bars)", "As much as 16:9 (small side bars)",
            "Half way", "None: black bars, no stretch"};
    public static final String[] GAME_VIEWS = {"Stock (Game Space's ratio if you picked one)", "Full screen, no bars",
            "iPad view: 4:3, stretched to full screen", "3:2, stretched to full screen", "16:10, stretched to full screen",
            "16:9, stretched to full screen", "2:1, stretched to full screen"};
    /** Width / height of the shape the game renders, per GAME_VIEWS index; 0 = no stretching. */
    public static final float[] GAME_VIEW_RATIO = {0f, 0f, 4f / 3f, 3f / 2f, 16f / 10f, 16f / 9f, 2f};
    /**
     * Experimental: an extra launch boost for Game Space games (system_server). GAME_LAUNCH_BOOST_PKGS ("pkg;pkg") limits
     * it to the games picked with GAME_LAUNCH_BOOST_GAMES; empty = every Game Space game.
     */
    public static final String GAME_LAUNCH_BOOST = "game_launch_boost";
    public static final String GAME_LAUNCH_BOOST_GAMES = "game_launch_boost_games";
    public static final String GAME_LAUNCH_BOOST_PKGS = "game_launch_boost_pkgs";
    public static final String[] LAUNCH_BOOSTS = {"Off", "3 seconds", "5 seconds", "8 seconds", "12 seconds", "15 seconds", "20 seconds"};
    public static final int[] LAUNCH_BOOST_MS = {0, 3000, 5000, 8000, 12000, 15000, 20000};
    /** Experimental: Game Assist's heavy onGameStart listeners start a few seconds after the game (cn.nubia.gameassist). */
    public static final String GAME_ASSIST_DEFER = "game_assist_defer";

    public static final String LAUNCHER_PAGED = "launcher_paged_drawer";
    public static final String LAUNCHER_PAGED_ROWS = "launcher_paged_rows";
    public static final String LAUNCHER_CUSTOM_ORDER = "launcher_drawer_custom_order";
    public static final String LAUNCHER_DRAWER_DOTS = "launcher_drawer_dots";
    public static final String LAUNCHER_ROTATION = "launcher_rotation";
    public static final String LAUNCHER_COLS = "launcher_cols";
    public static final String LAUNCHER_ROWS = "launcher_rows";
    public static final String LAUNCHER_HOTSEAT = "launcher_hotseat";
    public static final String LAUNCHER_HOTSEAT_TEXT = "launcher_hotseat_text";
    public static final String LAUNCHER_TWO_LINE = "launcher_two_line";
    public static final String LAUNCHER_CIRCLE = "launcher_circle_scroll";
    public static final String LAUNCHER_NO_FASTSCROLL = "launcher_hide_fastscroller";
    /** Text setting: package name of the icon pack (ADW / Nova format), absent = stock icons. Row: ACTION (ui/IconPackPicker). */
    public static final String LAUNCHER_ICON_PACK = "launcher_icon_pack";
    public static final String LAUNCHER_ICON_SHAPE = "launcher_icon_shape";
    public static final String LAUNCHER_LABEL_SIZE = "launcher_label_size";
    public static final String LAUNCHER_HIDE_LABELS = "launcher_hide_labels";
    public static final String LAUNCHER_FOLDER_COLS = "launcher_folder_cols";
    public static final String LAUNCHER_FOLDER_ROWS = "launcher_folder_rows";

    public static final String THEME_VARIANT = "theme_variant";
    public static final String FP_UNLOCK_ALL = "fp_unlock_all";
    public static final String FP_CHISA = "fp_chisa";
    public static final String FP_FIGHTING = "fp_fighting";
    public static final String FP_WW = "fp_wuthering_waves";
    public static final String FP_LTY = "fp_lty";
    public static final String FP_SAGA = "fp_golden_saga";

    public static final String INST_SKIP_SCAN = "inst_skip_scan";
    public static final String INST_NO_STORE_NAG = "inst_no_store_nag";
    public static final String INST_NO_RISK = "inst_no_risk";
    public static final String INST_HIDE_PURE = "inst_hide_pure";

    public static final String CHARGE_SET = "charge_set";
    public static final String CHARGE_IMPORT = "charge_import";
    public static final String CHARGE_EVERY_LOCK = "charge_every_lock";
    /** {label, kind, arg}. kind: stock | flag | table | file | asset | custom. */
    public static final String[][] CHARGE_SET_DEFS = {
        {"Stock", "stock", ""},
        {"Black Myth: Wukong collab (3-stage)", "flag", "IS_IP_MK_CN"},
        {"RedMagic wired", "table", "RedMagic_Drawable"},
        {"RedMagic wireless", "table", "RedMagic_Wireless_Drawable"},
        {"Classic Mifavor", "table", "Drawable"},
        {"Classic Mifavor wireless", "table", "WireLessDrawable"},
        {"Game-mode bottom glow", "table", "Nubia_Game_Bottom_Drawable"},
        {"Luo Tianyi collab (if shipped)", "file", "ip_lty_charge_0%s.webp|ip_lty_fast_charge_0%s.webp"},
        {"CAMELLYA / Wuthering Waves collab (if shipped)", "file", "redmagic_charge_0%s.webp|redmagic_fast_charge_0%s.webp"},
        {"GOLDEN SAGA collab (if shipped)", "file", "redmagic_pb_charge_0%s.webp|redmagic_pb_fast_charge_0%s.webp"},
        {"GOLDEN SAGA wireless (if shipped)", "file", "redmagic_wireless_pb_charge_0%s.webp|redmagic_wireless_pb_fast_charge_0%s.webp"},
        {(com.hammad.redmagictweaks.BuildConfig.APP_NAME + " · Neon Ring"), "asset", "neon_ring"},
        {(com.hammad.redmagictweaks.BuildConfig.APP_NAME + " · Lightning Core"), "asset", "lightning_core"},
        {(com.hammad.redmagictweaks.BuildConfig.APP_NAME + " · Plasma Wave"), "asset", "plasma_wave"},
        {(com.hammad.redmagictweaks.BuildConfig.APP_NAME + " · Ember Pulse"), "asset", "ember_pulse"},
        {"Custom frames (imported below)", "custom", "custom"},
    };
    public static final String[] CHARGE_SETS = labels(CHARGE_SET_DEFS);

    static {
        com.hammad.redmagictweaks.xposed.DrawerArt.registerPreviews();
        Previews.register(GAME_VIEW, GameViewArt::preview, "Estimate for PUBG-style games · applies the next time the game opens, no reboot needed");
        // Picture previews for the charging sets: a mid-animation frame of System UI's own tables / firmware
        // files, the first frame of our asset sets, the first imported frame for Custom.
        Previews.register(CHARGE_SET, (ctx, i) -> {
            if (i <= 0 || i >= CHARGE_SET_DEFS.length) return null;
            String kind = CHARGE_SET_DEFS[i][1], arg = CHARGE_SET_DEFS[i][2];
            try {
                switch (kind) {
                    case "asset": return Previews.asset(ctx, Frames.assetPath(ctx, arg, Math.min(12, Math.max(0, Frames.assetCount(ctx, arg) - 1))));
                    case "custom": return Previews.file(Frames.frame(ctx, Math.min(12, Math.max(0, Frames.count(ctx) - 1))));
                    case "table": {
                        android.content.res.Resources r = ctx.getPackageManager().getResourcesForApplication("com.android.systemui");
                        int[] ids = ChargeTables.ids(arg);
                        if (ids == null || ids.length == 0) return null;
                        return r.getDrawable(ids[Math.min(24, ids.length - 1)], r.newTheme());
                    }
                    case "file": {
                        String pat = arg.split("\\|")[0];
                        java.io.File f = new java.io.File("/system/etc/custom_config/app/systemUI/charge/" + String.format(pat, "24"));
                        if (!f.isFile()) f = new java.io.File("/system/etc/custom_config/app/systemUI/charge/" + String.format(pat, "12"));
                        return Previews.file(f);
                    }
                    case "flag": {
                        android.content.res.Resources r = ctx.getPackageManager().getResourcesForApplication("com.android.systemui");
                        int[] ids = ChargeTables.ids("IP_MX_Part_Two_Drawable");
                        return ids == null || ids.length == 0 ? null : r.getDrawable(ids[Math.min(12, ids.length - 1)], r.newTheme());
                    }
                    default: return null;
                }
            } catch (Throwable t) { return null; }
        }, "Tap a set to preview it · plays on the lock screen when charging");
        Previews.registerFrames(CHARGE_SET, (ctx, i) -> {
            if (i <= 0 || i >= CHARGE_SET_DEFS.length) return null;
            String kind = CHARGE_SET_DEFS[i][1], arg = CHARGE_SET_DEFS[i][2];
            try {
                java.util.List<android.graphics.drawable.Drawable> out = new java.util.ArrayList<>();
                if ("asset".equals(kind)) { int n = Frames.assetCount(ctx, arg); for (int k = 0; k < n; k++) out.add(Previews.asset(ctx, Frames.assetPath(ctx, arg, k))); }
                else if ("custom".equals(kind)) { int n = Math.min(120, Frames.count(ctx)); for (int k = 0; k < n; k++) out.add(Previews.file(Frames.frame(ctx, k))); }
                else if ("table".equals(kind) || "flag".equals(kind)) {
                    android.content.res.Resources r = ctx.getPackageManager().getResourcesForApplication("com.android.systemui");
                    int[] ids = ChargeTables.ids("flag".equals(kind) ? "IP_MX_Part_Two_Drawable" : arg);
                    if (ids != null) for (int id : ids) out.add(r.getDrawable(id, r.newTheme()));
                } else if ("file".equals(kind)) {
                    String pat = arg.split("\\|")[0];
                    for (int k = 1; k <= 120; k++) { java.io.File f = new java.io.File("/system/etc/custom_config/app/systemUI/charge/" + String.format(pat, k < 10 ? "0" + k : String.valueOf(k))); if (!f.isFile()) break; out.add(Previews.file(f)); }
                }
                return out.isEmpty() ? null : out.toArray(new android.graphics.drawable.Drawable[0]);
            } catch (Throwable t) { return null; }
        });
    }

    /** Theme variants: {label, theme-resource-feature, variant-id}. Index 0 = off. */
    public static final String[][] THEME_VARIANTS = {
        {"Off (stock)", "", ""},
        {"Phantom · Mora Pink (10S Pro)", "nx789j_ip_mp_cn", "IP_MP_CN"},
        {"CAMELLYA · Wuthering Waves (10S Pro)", "nx789s_ip_mc_cn", "IP_MC_CN"},
        {"Black Myth: Wukong (10S Pro)", "nx789s_w_ip_mk_cn", "IP_MK_CN"},
        {"GOLDEN SAGA (10S Pro)", "nx789s_w_ip_pb_cn", "IP_PB_CN"},
        {"Chisa · Wuthering Waves (11 Pro)", "nx809j_w_ip_mc_cn", "IP_MC_W_CN"},
        {"GOLDEN SAGA (11 Pro)", "nx809j_w_saga", "IP_PB_CN"},
        {"Bumblebee (9S Pro)", "nx769j_ip_tf_cn", "IP_TF_CN"},
        {"Bumblebee (8S Pro)", "nx729j_ip_tf_cn", "IP_TF_CN"},
    };
    public static final String[] AI_SPEED = {"Stock", "2× faster", "3× faster", "5× faster", "10× faster"};
    public static final int[] AI_SPEED_DIV = {1, 2, 3, 5, 10};
    public static final String[] GRID_COLS = {"Stock", "4", "5", "6"};
    public static final String[] GRID_ROWS = {"Stock", "5", "6", "7", "8"};
    public static final String[] HOTSEAT = {"Stock", "4", "5", "6", "7"};
    public static final String[] PAGED_ROWS = {"Auto", "4", "5", "6", "7", "8"};
    /** Index = xposed.LauncherIcons.SHAPE_PATHS. */
    public static final String[] ICON_SHAPES = {"Stock (theme shape)", "Circle", "Squircle", "Rounded square", "Square", "Teardrop", "One UI", "Diamond"};
    /** Index = xposed.LauncherIcons.LABEL_SCALE. */
    public static final String[] LABEL_SIZES = {"Stock", "80 %", "90 %", "110 %", "120 %", "130 %"};
    public static final String[] FOLDER_COLS = {"Stock", "3", "4", "5"};
    public static final String[] FOLDER_ROWS = {"Stock", "3", "4", "5", "6"};

    private static String[] labels(String[][] v) { String[] l = new String[v.length]; for (int i = 0; i < v.length; i++) l[i] = v[i][0]; return l; }

    public static final Section GAME = new Section("game", "Game Space",
        "Unlocks for Game Assist, macros, triggers and performance modes",
        R.drawable.ic_game,
        new String[]{"cn.nubia.gameassist", "cn.nubia.gamelauncher", "cn.nubia.gamehelpmodule", "cn.nubia.gamelab", "cn.nubia.gamehighlights"},
        new Feature(GAME_UNFREEZE, "Game Assist in every app", "Shoulder keys, plugins and the in-game panel work in any app, not just whitelisted games. Restart Game Assist after changing."),
        new Feature(GAME_MACRO_ANY, "Macros and combos in any game", "Removes the macro / one-key combo blacklist."),
        new Feature(GAME_COMBO_SPEED, "Combo playback speed control", "Enables the hidden playback-speed option for recorded combos."),
        new Feature(GAME_TGK_ALL, "Trigger-key linkage everywhere", "Shoulder-trigger key mapping is never disabled per game."),
        new Feature(GAME_SR_BALANCE, "Superior picture quality in Balance mode", "Lets Super Resolution / superior picture quality be enabled while in Balance or Power-saving mode instead of demanding Rise mode."),
        new Feature(GAME_SR_COEXIST, "Super Resolution with Diablo mode", "Lets Super Resolution stay on while Diablo (extreme) performance mode is active, and stops auto-disable."),
        new Feature(GAME_NO_DIALOGS, "Skip Diablo confirmation dialogs", "Toggling Diablo mode applies immediately without the warning pop-up."),
        new Feature(GAME_KEEP_RMTIME, "RedMagic Time in Diablo mode", "Game Space never turns RedMagic Time off by itself, its controls stay usable in Diablo mode (stock greys them out), and your per-game choice is remembered when the panel reopens. Restart Game Space."),
        new Feature(GAME_AI_SPEED, "AI trigger reaction speed", "Shortens template-scan, click and cooldown delays of Game Lab AI triggers. A typed value is how many times faster (1 = stock).", Type.CHOICE, AI_SPEED, 0).num(1, 10, 1, "×"),
        new Feature(GAME_RECORD_FREE, "Record Red Magic Moments anywhere", "Removes the abnormal-state abort so recording works in any mode."),
        new Feature(GAME_ACTIVE_KEEP, "Remember Active Mode per game", "Game Assist turns Active Mode (background-active / freeform) off every time a game is launched for the first time after boot. With this on, only your own tap on the tile changes it and the state is kept per game. Restart Game Assist."),
        new Feature(GAME_GFRC_ALL, "Frame interpolation for every game", "Game Space's Super Resolution panel offers frame interpolation (the Super and Ultra frame rates) and upscaling only for the games on Qualcomm's list; for any other game both stay at Original. This unlocks them for every game you added to Game Space. Games outside the list may show ghosting or small artifacts and run warmer. Applies the next time a game starts."),
        new Feature(GAME_GFRC_TARGET, "Refresh rate during frame interpolation", "How fast the screen runs while frame interpolation is on. Stock runs Super at 120 Hz, so a Super game never shows more than 120 fps, and Ultra at the panel's top rate. Qualcomm's interpolation (gppservice) adds one generated frame after every game frame, so the screen shows twice the game's own frame rate, up to the rate picked here: a game at 60 fps shows 120, a game at 82 fps or more fills 165 Hz. \"Always the maximum\" keeps the screen at its top rate for Super and Ultra. While interpolation is on, nothing else can drop the screen below the picked rate. The log shows an \"interpolation check\" line a few seconds after it turns on. If the screen runs faster than the frames the game and interpolation produce, motion can judder, so try it per game. Applies the next time interpolation turns on.",
                Type.CHOICE, GFRC_TARGETS, 0),
        new Feature(GAME_FULLSCREEN, "Full screen for every game", "Games made for 16:9 leave black bars at the sides. This asks the game to draw at the phone's own shape instead: no bars, and most games show a wider view. Games you picked a ratio for in Game Space keep that ratio (Game view below overrides it, and can also stretch a 4:3 or 16:9 picture to the full screen). Applies the next time the game starts (force-stop it first). Some older games draw their own bars or put buttons under the camera hole."),
        new Feature(GAME_VIEW, "Game view", "How Game Space games fill the screen. Full screen: the game draws at the phone's own shape, no bars, even when you picked a ratio in Game Space. iPad view and the other stretched views: the game draws a narrower picture (4:3 shows the most around you in games like PUBG) and the screen stretches it to the full width, so nothing is cut off and there are no bars, but everything looks wider. While a stretched game is in front the whole screen uses that shape (the Game Space sidebar and notifications too); it goes back the moment you leave the game, turn the screen off or the game closes, and never stays after a restart. Per-game choices below win over this one. Applies the next time the game opens (swipe it away from recents first); no reboot needed, only installing an RM Tweaks update needs one.",
                Type.CHOICE, GAME_VIEWS, 0),
        new Feature(GAME_VIEW_ONLY, "Stretch only the game (experimental)", "For the stretched views (iPad view and the others): instead of giving the whole screen the narrower shape, only the game gets it. The game draws its narrower picture in a window of that shape, and only that picture is widened to the full screen; Game Space's sidebar and panels, notifications, the keyboard and pop-ups stay at the normal shape. Touches and shoulder triggers follow the picture. Replaces Game Space's own ratio for that game while on. Experimental: if touches land in the wrong place or the game shows bars, turn it off. Applies the next time the game opens (swipe it away from recents first)."),
        new Feature(GAME_VIEW_GS, "Game Space ratio fills the screen", "When you pick a ratio for a game in Game Space (for example 16:10), the game is stretched to the full screen instead of getting black bars, the same way as \"Stretch only the game\": only the game's picture is widened, Game Space and notifications stay normal, touches follow the picture. Set the ratio in Game Space as usual (Free display gets 3:2, 16:10 and 2:1 too, and its move arrows stay hidden); games with their own Game view here keep that. Restart Game Space after turning it on. Applies the next time the game opens or the ratio changes."),
        new Feature(GAME_VIEW_GS_CUSTOM, "Custom Free display ratio", "Adds a \"Custom\" choice with this ratio to Game Space's Free display, next to 4:3, 16:10 and the others. Pick one here or type your own as width ÷ height × 100: 170 is 17:10, 125 is 5:4, 160 is 16:10. The lower the number, the more the game shows above and below and the more it is stretched sideways. Restart Game Space (or reboot) after changing it, then pick Custom in Free display.",
                Type.CHOICE, GS_CUSTOM_RATIOS, 0).num(105, 215, 1, "÷100"),
        new Feature(GAME_VIEW_STRENGTH, "Stretch strength", "For \"Stretch only the game\" and \"Game Space ratio fills the screen\": how far the game's picture is widened. Full fills the screen. \"As much as 16:10\" keeps everything a narrower ratio such as 4:3 shows (the view above and below) but widens it only as much as 16:10 would, so things look less stretched; thin black bars stay at the left and right. A typed value is the share of the full stretch (0 = none, 100 = full). Applies the next time the game opens.",
                Type.CHOICE, VIEW_STRENGTHS, 0).num(0, 100, 5, "%"),
        new Feature(GAME_VIEW_GAMES, "Game view per game…", "Pick a view for single games (for example iPad view only for PUBG). Games without their own choice use the Game view above. Each choice shows a picture of how the game will look. Applies the next time the game opens, no reboot needed.", Type.ACTION, null, 0),
        new Feature(GAME_LAUNCH_BOOST, "Extra boost while a game loads (experimental)", "Experimental, measure it before and after: keeps the CPU launch boost on for up to 20 seconds when a game starts, which can shorten loading. Uses more power and runs warmer while it loads. Applies to every Game Space game, or only to the games you pick below. Applies the next time a game starts; installing an RM Tweaks update needs a reboot.",
                Type.CHOICE, LAUNCH_BOOSTS, 0),
        new Feature(GAME_LAUNCH_BOOST_GAMES, "Boost only these games…", "Pick the games that get the extra boost (for example only PUBG). With none picked, every Game Space game gets it. Applies the next time the game starts, no reboot needed.", Type.ACTION, null, 0),
        new Feature(GAME_ASSIST_DEFER, "Lighter Game Assist at game start (experimental)", "Experimental, measure it before and after: Game Assist starts about 30 helpers the moment a game launches. This starts the heavy ones (AI detection, combat power, game prediction, card assist, counter, screen extraction and the performance monitor) 8 seconds later so they don't compete with loading; Super Resolution, the game ratio, the performance mode and the refresh rate still start at once. Those tiles may show their state a few seconds late. Restart Game Assist.")
    );

    public static final Section LAUNCHER = new Section("launcher", "Launcher",
        "Stock launcher drawer, grid and behaviour",
        R.drawable.ic_launcher_grid,
        new String[]{"com.zte.mifavor.launcher"},
        new Feature(LAUNCHER_PAGED, "Horizontal paged app drawer", "The app drawer scrolls sideways page by page instead of a vertical list. Turn on \"Hide A–Z fast scroller\" with it. Restart the launcher after changing."),
        new Feature(LAUNCHER_PAGED_ROWS, "Rows per drawer page", "Only used with the paged drawer; Auto fits as many rows as the screen allows. Restart the launcher after changing.", Type.CHOICE, PAGED_ROWS, 0).num(3, 10, 1, "rows"),
        new Feature(LAUNCHER_DRAWER_DOTS, "Page dots in the app drawer", "Shows small page-indicator dots under the paged drawer, the current page as the long bright one (One UI style). Tap the dots strip to show the Edit button. Only with the horizontal paged drawer; works with or without the custom layout. Reopen the drawer."),
        new Feature(LAUNCHER_CUSTOM_ORDER, "Custom drawer layout (One UI style)", "Arrange the app drawer yourself instead of A–Z. Tap the page-dots strip (or long-press empty drawer space) for Edit mode; hold an icon and drag it anywhere (also to other pages; hold at the screen edge to flip), drop it on the centre of another app to make a folder, or onto the bottom button to hide it. Tap a folder to open or rename it. Only works with the horizontal paged drawer. Restart the launcher after changing."),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_FIXED_SLOTS, "Fixed drawer slots (One UI)", "In the custom drawer layout icons keep their grid slot: moving an icon leaves a gap behind, empty slots are allowed anywhere, a page can hold a single app, and holding an icon at the right edge of the last page adds a new page. Off = icons are packed together (gaps close up). Reopen the drawer."),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_PAGE_PAD, "Gap between drawer pages", "Insets every page of the paged drawer on both sides so neighbouring pages are not cut off at the screen edges while swiping (One UI style). Only with the horizontal paged drawer. Restart the launcher after changing.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerKeys.PAGE_PADS, 0).num(0, 40, 1, "dp"),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_SEARCH_POS, "Drawer search bar position", "Top = stock. Bottom (One UI) = the search pill sits at the bottom just above the navigation bar, the page dots right above it and the ⋮ menu (Sort, Edit, Hidden apps) next to it, opening upwards; the apps start at the top. Tap it to search: while you type it is back at the top, like stock. Hidden = no search bar. Restart the launcher.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerKeys.SEARCH_POSITIONS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_HIDE_SEARCH, "Hide the drawer search bar", "Removes the search bar (and the ⋮ button in it) at the top of the app drawer; the apps move up into its space. Restart the launcher."),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_HIDE_MENU, "Hide the drawer ⋮ button", "Hides only the menu (⋮) button next to the drawer search bar. Restart the launcher."),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_LABEL_SIZE, "Drawer label size", "Smaller app names under the drawer icons (86 % / 74 % of stock). Restart the launcher.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerKeys.LABEL_SIZES, 0).num(60, 120, 1, "%"),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_SHAPE, "Drawer folder shape", "Shape of the folder tiles in the paged drawer: rounded square, circle, squircle, square, leaf or One UI (rounded square with rounded mini icons). Only our drawer folders (custom drawer layout). Reopen the drawer.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.SHAPES, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_COLOR, "Drawer folder colour", "Background colour of the folder tiles. \"Auto\" is the translucent white tile; \"Transparent\" shows only the mini icons. A single folder can override this: hold the folder > Folder colour…", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.COLORS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_OPACITY, "Drawer folder opacity", "How solid the folder tile colour is.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.OPACITIES, 0).num(0, 100, 5, "%"),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_GRID, "Mini icons inside drawer folders", "2 × 2 or 3 × 3 app icons on the tile, or one big icon of the first app with an app-count badge.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.GRIDS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_LABEL, "Drawer folder name", "Show the folder name under the tile in the app-label colour, in white or black, or hide it.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.LABELS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_FOLDER_OPEN, "Opened folder style", "Card = small popup card. One UI = full screen like Samsung: blurred background, big folder name (tap to rename), + to add apps and a colour button, rounded 4-column panel; opens out of the folder icon. Only with the custom drawer layout. Restart the launcher.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerKeys.FOLDER_OPEN_STYLES, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_BG, "Opened folder background", "Card colour of the opened drawer folder: dark, light, or translucent dark. Text colour follows.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.VIEW_BGS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_RADIUS, "Opened folder corners", "Corner roundness of the opened folder card.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.VIEW_RADII, 0).num(0, 64, 2, "dp"),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_COLS, "Opened folder columns", "Icons per row in the opened folder.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.VIEW_COLS, 0).num(3, 6, 1, "columns"),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_NO_NAMES, "Hide app names in opened folders", "Shows only the icons inside the opened folder."),
        new Feature(LAUNCHER_NO_FASTSCROLL, "Hide A–Z fast scroller", "Hides the alphabet scroller in the drawer (recommended with the paged drawer)."),
        new Feature(LAUNCHER_ROTATION, "Allow home screen rotation", "Home screen and drawer follow the phone orientation."),
        new Feature(LAUNCHER_COLS, "Home grid columns", "", Type.CHOICE, GRID_COLS, 0).num(3, 8, 1, "columns"),
        new Feature(LAUNCHER_ROWS, "Home grid rows", "", Type.CHOICE, GRID_ROWS, 0).num(4, 10, 1, "rows"),
        new Feature(LAUNCHER_HOTSEAT, "Dock icons", "", Type.CHOICE, HOTSEAT, 0).num(3, 8, 1, "icons"),
        new Feature(LAUNCHER_HOTSEAT_TEXT, "Show labels in dock", ""),
        new Feature(LAUNCHER_TWO_LINE, "Two-line app labels", ""),
        new Feature(LAUNCHER_CIRCLE, "Infinite home screen scrolling", "Last page wraps around to the first."),
        new Feature(LAUNCHER_ICON_PACK, "Icon pack", "Uses an installed icon pack (any ADW / Nova / Apex style pack from the Play Store) for the app icons on the home screen, in the app drawer and in folders. Apps the pack has no icon for are drawn on the pack's own background when it has one. The launcher restarts by itself after you pick one.", Type.ACTION, null, 0),
        new Feature(LAUNCHER_ICON_SHAPE, "App icon shape", "Shape of the app icons (circle, squircle, rounded square, square, teardrop, One UI, diamond). Works on apps with adaptive icons; a theme or icon pack with fixed icons keeps its own shape. Restart the launcher.", Type.CHOICE, ICON_SHAPES, 0),
        new Feature(LAUNCHER_LABEL_SIZE, "App label size", "Size of the app names under the icons on the home screen, in the dock and in folders. Restart the launcher.", Type.CHOICE, LABEL_SIZES, 0),
        new Feature(LAUNCHER_HIDE_LABELS, "Hide app labels", "Only icons on the home screen, no app names. Restart the launcher."),
        new Feature(LAUNCHER_FOLDER_COLS, "Home folder columns", "Apps per row in the opened folders of the home screen. Restart the launcher.", Type.CHOICE, FOLDER_COLS, 0),
        new Feature(LAUNCHER_FOLDER_ROWS, "Home folder rows", "Rows per page in the opened folders of the home screen. Restart the launcher.", Type.CHOICE, FOLDER_ROWS, 0)
    );

    public static final Section THEME = new Section("theme", "Themes & fingerprint",
        "Collaboration themes and fingerprint animations",
        R.drawable.ic_theme,
        new String[]{"com.zte.beautify", "com.zte.fingerprints", "com.fingerprint.sensorservice"},
        new Feature(THEME_VARIANT, "Collaboration theme pack", "Makes the Theme Store believe the phone is the chosen collaboration edition so its preset theme, AOD and wallpapers can be applied.", Type.CHOICE, labels(THEME_VARIANTS), 0),
        new Feature(FP_UNLOCK_ALL, "Unlock all fingerprint animations", "Adds every animation shipped in the firmware to the picker (Chisa, Luo Tianyi, Fighting Buddha, Wuthering Waves, Force Matrix…)."),
        new Feature(FP_CHISA, "Chisa edition", "Unlocks the Chisa collaboration fingerprint style and its rendering."),
        new Feature(FP_FIGHTING, "Fighting Buddha edition", "Unlocks the Black Myth: Wukong fingerprint style."),
        new Feature(FP_WW, "Wuthering Waves edition", "Unlocks the Wuthering Waves style (replaces Force Matrix with the collab version)."),
        new Feature(FP_LTY, "Luo Tianyi edition", "Unlocks the Luo Tianyi fingerprint style."),
        new Feature(FP_SAGA, "GOLDEN SAGA edition", "Unlocks the GOLDEN SAGA reskins.")
    );

    public static final Section SYSTEMUI = new Section("systemui", "System UI",
        "Charging animation and lock screen",
        R.drawable.ic_bolt,
        new String[]{"com.android.systemui"},
        new Feature(CHARGE_SET, "Charging animation", "Which frame set System UI plays when the charger is connected. Custom frames come from the folder you import below.", Type.CHOICE, CHARGE_SETS, 0),
        new Feature(CHARGE_IMPORT, "Import custom charging frames…", ("Pick a folder of PNG/WEBP/JPG frames (sorted by file name). They are copied into " + com.hammad.redmagictweaks.BuildConfig.APP_NAME + " and served to System UI."), Type.ACTION, null, 0),
        new Feature(CHARGE_EVERY_LOCK, "Play charging animation on every screen-on", "Shows the animation each time the lock screen appears while charging, not only when the charger is plugged in.")
    );

    public static final Section INSTALLER = new Section("installer", "Package installer",
        "Faster, quieter APK installs",
        R.drawable.ic_settings,
        new String[]{"com.android.packageinstaller"},
        new Feature(INST_SKIP_SCAN, "Skip the APK scan", "No 2-second \"scanning\" splash and no online ad/virus check before the install screen."),
        new Feature(INST_NO_STORE_NAG, "Hide \"install from the store\" prompt", "Removes the official-store recommendation when installing from a browser or file manager."),
        new Feature(INST_NO_RISK, "Hide risk / fraud warnings", "Treats every package as safe: no red warning title, no isolation, no risk checkbox."),
        new Feature(INST_HIDE_PURE, "Hide pure-mode switch", "Hides the pure-mode toggle on the install screen.")
    );

    public static final Section[] SECTIONS = {
        Presets.SECTION, GAME, LAUNCHER, RecentsFeatures.SECTION, THEME, ThemeStoreFeatures.SECTION, FingerprintCustomFeatures.SECTION,
        SYSTEMUI, LockFeatures.SECTION, AodFeatures.SECTION, StatusBarFeatures.SECTION, ClockFeatures.SECTION, IconPackFeatures.SECTION,
        QsFeatures.SECTION, SurfaceFeatures.SECTION, CosmeticsFeatures.SECTION, BootAnimFeatures.SECTION,
        FrameworkFeatures.SECTION, SettingsFeatures.SECTION, ExtrasFeatures.SECTION, INSTALLER
    };

    public static List<Feature> all() {
        List<Feature> l = new ArrayList<>();
        for (Section s : SECTIONS) for (Feature f : s.features) l.add(f);
        return l;
    }

    public static Feature feature(String key) {
        for (Section s : SECTIONS) for (Feature f : s.features) if (f.key.equals(key)) return f;
        return null;
    }

    public static Section section(String id) {
        for (Section s : SECTIONS) if (s.id.equals(id)) return s;
        return null;
    }
}
