package com.hammad.redmagictweaks;

/** Theme Store (com.zte.beautify): every collaboration pack at once, paid packs free (hooked by xposed/ThemeStoreHooks). */
public final class ThemeStoreFeatures {
    private ThemeStoreFeatures() {}

    public static final String TS_ALL_PACKS = "ts_all_collab_packs";
    public static final String TS_PACK_SCOPE = "ts_pack_scope";
    public static final String TS_NO_LOGIN = "ts_no_login";
    public static final String TS_PRESET_ANY = "ts_preset_any_pack";

    /** Which firmware collaboration configs are merged into the local list (index 0 = this model only). */
    public static final String[] PACK_SCOPE = {
        "RedMagic 10 / 10S Pro packs only",
        "Every RedMagic pack in the firmware (11 Pro, 9S Pro, 8S Pro too)",
    };

    public static final Features.Section SECTION = new Features.Section("themestore", "Theme Store",
        "Collaboration packs, paid content and download gates of the Theme Store",
        R.drawable.ic_theme,
        new String[]{"com.zte.beautify"},
        new Features.Feature(TS_ALL_PACKS, "Show all collaboration packs", "Adds the theme and lock-screen lists of every collaboration edition (Mora Pink, CAMELLYA, Black Myth: Wukong, GOLDEN SAGA…) to the local lists, next to the edition chosen under Theme. Only packs whose files exist on this phone (/system/etc/custom_config/themes) or that ZTE's server offers for download are added; a pack that is neither is skipped and listed in Logs. Force-stop the Theme Store after changing."),
        new Features.Feature(TS_PACK_SCOPE, "Collaboration packs to merge", "Packs of other RedMagic models usually have no files on this phone and are then skipped automatically.", Features.Type.CHOICE, PACK_SCOPE, 0),
        new Features.Feature(TS_PRESET_ANY, "Fetch other models' pack files from the server", "When a preset pack is marked \"download from server\" in the firmware, the store asks ZTE's server with the pack's own model name (e.g. NX789S for a Wukong pack) instead of this phone's. Works only if the server offers that pack; the answer is written to Logs (\"server url for …\")."),
        new Features.Feature(TS_NO_LOGIN, "Download without ZTE / nubia account", "Skips the account login that the store demands before any online download (theme, AOD, wallpaper, font).")
    );
}
