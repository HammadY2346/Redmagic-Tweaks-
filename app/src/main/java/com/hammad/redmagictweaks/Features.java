package com.hammad.redmagictweaks;

import java.util.ArrayList;
import java.util.List;

/** Single source of truth for every tweak: used by the settings UI and read by the hooks. */
public final class Features {
    private Features() {}

    public static final String PREFS = "tweaks";
    public static final String PKG = BuildConfig.APPLICATION_ID;

    public enum Type { SWITCH, CHOICE, ACTION }

    public static final class Feature {
        public final String key, title, desc;
        public final Type type;
        public final String[] choices;   // CHOICE only: labels; stored value = index
        public final int def;            // CHOICE default index; SWITCH: 1 = on by default
        public Feature(String key, String title, String desc) { this(key, title, desc, Type.SWITCH, null, 0); }
        public Feature(String key, String title, String desc, Type type, String[] choices, int def) {
            this.key = key; this.title = title; this.desc = desc; this.type = type; this.choices = choices; this.def = def;
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
        new Feature(GAME_AI_SPEED, "AI trigger reaction speed", "Shortens template-scan, click and cooldown delays of Game Lab AI triggers.", Type.CHOICE, AI_SPEED, 0),
        new Feature(GAME_RECORD_FREE, "Record Red Magic Moments anywhere", "Removes the abnormal-state abort so recording works in any mode."),
        new Feature(GAME_ACTIVE_KEEP, "Remember Active Mode per game", "Game Assist turns Active Mode (background-active / freeform) off every time a game is launched for the first time after boot. With this on, only your own tap on the tile changes it and the state is kept per game. Restart Game Assist.")
    );

    public static final Section LAUNCHER = new Section("launcher", "Launcher",
        "Stock launcher drawer, grid and behaviour",
        R.drawable.ic_launcher_grid,
        new String[]{"com.zte.mifavor.launcher"},
        new Feature(LAUNCHER_PAGED, "Horizontal paged app drawer", "The app drawer scrolls sideways page by page instead of a vertical list. Turn on \"Hide A–Z fast scroller\" with it. Restart the launcher after changing."),
        new Feature(LAUNCHER_PAGED_ROWS, "Rows per drawer page", "Only used with the paged drawer; Auto fits as many rows as the screen allows. Restart the launcher after changing.", Type.CHOICE, PAGED_ROWS, 0),
        new Feature(LAUNCHER_DRAWER_DOTS, "Page dots in the app drawer", "Shows small page-indicator dots under the paged drawer, the current page as the long bright one (One UI style). Tap the dots strip to show the Edit button. Only with the horizontal paged drawer; works with or without the custom layout. Reopen the drawer."),
        new Feature(LAUNCHER_CUSTOM_ORDER, "Custom drawer layout (One UI style)", "Arrange the app drawer yourself instead of A–Z. Tap the page-dots strip (or long-press empty drawer space) for Edit mode; hold an icon and drag it anywhere (also to other pages; hold at the screen edge to flip), drop it on the centre of another app to make a folder, or onto the bottom button to hide it. Tap a folder to open or rename it. Only works with the horizontal paged drawer. Restart the launcher after changing."),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_FIXED_SLOTS, "Fixed drawer slots (One UI)", "In the custom drawer layout icons keep their grid slot: moving an icon leaves a gap behind, empty slots are allowed anywhere, a page can hold a single app, and holding an icon at the right edge of the last page adds a new page. Off = icons are packed together (gaps close up). Reopen the drawer."),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_PAGE_PAD, "Gap between drawer pages", "Insets every page of the paged drawer on both sides so neighbouring pages are not cut off at the screen edges while swiping (One UI style). Only with the horizontal paged drawer. Restart the launcher after changing.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerKeys.PAGE_PADS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_SEARCH_POS, "Drawer search bar position", "Top = stock. Bottom (One UI) = the search pill sits at the bottom just above the navigation bar, the page dots right above it and the ⋮ menu (Sort, Edit, Hidden apps) next to it, opening upwards; the apps start at the top. Tap it to search: while you type it is back at the top, like stock. Hidden = no search bar. Restart the launcher.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerKeys.SEARCH_POSITIONS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_HIDE_SEARCH, "Hide the drawer search bar", "Removes the search bar (and the ⋮ button in it) at the top of the app drawer; the apps move up into its space. Restart the launcher."),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_HIDE_MENU, "Hide the drawer ⋮ button", "Hides only the menu (⋮) button next to the drawer search bar. Restart the launcher."),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_LABEL_SIZE, "Drawer label size", "Smaller app names under the drawer icons (86 % / 74 % of stock). Restart the launcher.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerKeys.LABEL_SIZES, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_SHAPE, "Drawer folder shape", "Shape of the folder tiles in the paged drawer: rounded square, circle, squircle, square, leaf or One UI (rounded square with rounded mini icons). Only our drawer folders (custom drawer layout). Reopen the drawer.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.SHAPES, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_COLOR, "Drawer folder colour", "Background colour of the folder tiles. \"Auto\" is the translucent white tile; \"Transparent\" shows only the mini icons. A single folder can override this: hold the folder > Folder colour…", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.COLORS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_OPACITY, "Drawer folder opacity", "How solid the folder tile colour is.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.OPACITIES, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_GRID, "Mini icons inside drawer folders", "2 × 2 or 3 × 3 app icons on the tile, or one big icon of the first app with an app-count badge.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.GRIDS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_LABEL, "Drawer folder name", "Show the folder name under the tile in the app-label colour, in white or black, or hide it.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.LABELS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerKeys.KEY_FOLDER_OPEN, "Opened folder style", "Card = small popup card. One UI = full screen like Samsung: blurred background, big folder name (tap to rename), + to add apps and a colour button, rounded 4-column panel; opens out of the folder icon. Only with the custom drawer layout. Restart the launcher.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerKeys.FOLDER_OPEN_STYLES, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_BG, "Opened folder background", "Card colour of the opened drawer folder: dark, light, or translucent dark. Text colour follows.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.VIEW_BGS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_RADIUS, "Opened folder corners", "Corner roundness of the opened folder card.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.VIEW_RADII, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_COLS, "Opened folder columns", "Icons per row in the opened folder.", Type.CHOICE, com.hammad.redmagictweaks.xposed.DrawerArt.VIEW_COLS, 0),
        new Feature(com.hammad.redmagictweaks.xposed.DrawerArt.KEY_VIEW_NO_NAMES, "Hide app names in opened folders", "Shows only the icons inside the opened folder."),
        new Feature(LAUNCHER_NO_FASTSCROLL, "Hide A–Z fast scroller", "Hides the alphabet scroller in the drawer (recommended with the paged drawer)."),
        new Feature(LAUNCHER_ROTATION, "Allow home screen rotation", "Home screen and drawer follow the phone orientation."),
        new Feature(LAUNCHER_COLS, "Home grid columns", "", Type.CHOICE, GRID_COLS, 0),
        new Feature(LAUNCHER_ROWS, "Home grid rows", "", Type.CHOICE, GRID_ROWS, 0),
        new Feature(LAUNCHER_HOTSEAT, "Dock icons", "", Type.CHOICE, HOTSEAT, 0),
        new Feature(LAUNCHER_HOTSEAT_TEXT, "Show labels in dock", ""),
        new Feature(LAUNCHER_TWO_LINE, "Two-line app labels", ""),
        new Feature(LAUNCHER_CIRCLE, "Infinite home screen scrolling", "Last page wraps around to the first.")
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
        SYSTEMUI, LockFeatures.SECTION, StatusBarFeatures.SECTION, ClockFeatures.SECTION, IconPackFeatures.SECTION,
        QsFeatures.SECTION, SurfaceFeatures.SECTION, CosmeticsFeatures.SECTION, BootAnimFeatures.SECTION,
        FrameworkFeatures.SECTION, SettingsFeatures.SECTION, ExtrasFeatures.SECTION, INSTALLER
    };

    // ------------------------------------------------------------------ "By topic" layout (UiPrefs.byTopic)
    // The sections above follow the app each tweak patches. By topic, everything about the battery / charging and
    // everything shown on the lock screen is gathered in one place each; the other sections keep the rest. Same
    // features and keys, only the grouping differs. Topic section ids start with "t:".
    public static final String TOPIC_PREFIX = "t:";
    private static Section[] topics;

    private static boolean isBattery(Section s, Feature f) {
        if (s == Presets.SECTION) return false;
        String t = f.title.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("charg")) return true;                                   // charging animation / readout / symbol / separation
        return t.contains("battery") && s != LockFeatures.SECTION;              // the lock screen battery widget stays with the lock screen
    }
    private static final java.util.Set<String> LOCK_EXTRA = new java.util.HashSet<>(java.util.Arrays.asList(
        CosmeticsFeatures.COS_CLOCK_FONT, CosmeticsFeatures.COS_FONT_IMPORT,
        FP_UNLOCK_ALL, FP_CHISA, FP_FIGHTING, FP_WW, FP_LTY, FP_SAGA, FrameworkFeatures.NO_72H));
    private static boolean isLock(Section s, Feature f) {
        if (s == Presets.SECTION) return false;
        return s == LockFeatures.SECTION || s == FingerprintCustomFeatures.SECTION || LOCK_EXTRA.contains(f.key);
    }

    public static synchronized Section[] topicSections() {
        if (topics != null) return topics;
        List<Feature> bat = new ArrayList<>(), lock = new ArrayList<>();
        java.util.LinkedHashSet<String> batPk = new java.util.LinkedHashSet<>(), lockPk = new java.util.LinkedHashSet<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Map<Section, List<Feature>> rest = new java.util.LinkedHashMap<>();
        for (Section s : SECTIONS) {
            List<Feature> left = new ArrayList<>();
            for (Feature f : s.features) {
                if (isBattery(s, f)) { if (seen.add(f.key)) { bat.add(f); batPk.addAll(java.util.Arrays.asList(s.packages)); } }
                else if (isLock(s, f)) { if (seen.add(f.key)) { lock.add(f); lockPk.addAll(java.util.Arrays.asList(s.packages)); } }
                else left.add(f);
            }
            rest.put(s, left);
        }
        List<Section> out = new ArrayList<>();
        for (Section s : SECTIONS) {
            if (s == Presets.SECTION) {
                out.add(copy(s, rest.get(s)));
                out.add(new Section(TOPIC_PREFIX + "battery", "Battery & charging",
                        "Battery icon and readouts, charging animation, power saving and charging switches",
                        SYSTEMUI.icon, batPk.toArray(new String[0]), bat.toArray(new Feature[0])));
                out.add(new Section(TOPIC_PREFIX + "lock", "Lock screen",
                        "Clock, widgets, notifications, AOD, fingerprint icon and animations",
                        LockFeatures.SECTION.icon, lockPk.toArray(new String[0]), lock.toArray(new Feature[0])));
                continue;
            }
            List<Feature> left = rest.get(s);
            if (left != null && !left.isEmpty()) out.add(copy(s, left));
        }
        topics = out.toArray(new Section[0]);
        return topics;
    }
    private static Section copy(Section s, List<Feature> f) {
        return new Section(TOPIC_PREFIX + s.id, s.title, s.subtitle, s.icon, s.packages, f.toArray(new Feature[0]));
    }
    /** The sections the home screen shows: by app (default) or by topic. */
    public static Section[] visible(android.content.Context c) { return UiPrefs.byTopic(c) ? topicSections() : SECTIONS; }

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
        if (id != null && id.startsWith(TOPIC_PREFIX)) { for (Section s : topicSections()) if (s.id.equals(id)) return s; return null; }
        for (Section s : SECTIONS) if (s.id.equals(id)) return s;
        return null;
    }
}
