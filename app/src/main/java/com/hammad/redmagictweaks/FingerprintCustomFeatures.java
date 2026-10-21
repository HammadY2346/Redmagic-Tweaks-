package com.hammad.redmagictweaks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom under-display fingerprint animation and idle icon, ported from nubia-ly (FingerprintStyles /
 * FingerprintAnimations / SensorAnimationBackend / SensorFingerprintBackend / FingerprintBackend) and extended with
 * RM Tweaks' own frame sets and folder import. Hooked in com.fingerprint.sensorservice by xposed/SensorFingerprintHooks
 * and, for the PIN-screen symbol, in com.android.systemui by xposed/FingerprintUiHooks.
 *
 * <p>Choice indexes are append-only: the first entries of every list are the ones RM Tweaks shipped before the port.
 */
public final class FingerprintCustomFeatures {
    private FingerprintCustomFeatures() {}

    public static final String FP_ANIM = "fpc_anim";
    public static final String FP_ANIM_SET = "fpc_anim_set";
    public static final String FP_ANIM_INTERVAL = "fpc_anim_interval";
    public static final String FP_ANIM_SIZE = "fpc_anim_size";
    public static final String FP_ICON = "fpc_icon";
    public static final String FP_ICON_STYLE = "fpc_icon_style";
    public static final String FP_ICON_COLOR = "fpc_icon_color";
    public static final String FP_ICON_SIZE = "fpc_icon_size";
    /** nubia-ly "Hide fingerprint icon": no idle symbol on the lock screen and the PIN screen (the sensor still works). */
    public static final String FP_ICON_HIDE = "fpc_icon_hide";
    /** ACTION: the UI wires this to a folder picker and calls Frames.fpImportFolder(ctx, treeUri). */
    public static final String FP_IMPORT = "fp_import";

    /** Prefix of set ids that name a nubia-ly / RisingOS atlas: "nl:&lt;id&gt;" → drawable rising_anim_atlas_&lt;id&gt;. */
    public static final String ATLAS_PREFIX = "nl:";

    /**
     * {label, set-id}. set-id is the asset folder under assets/fingerprint/, "custom" for imported frames, or
     * "nl:&lt;id&gt;" for a RisingOS atlas (nubia-ly FingerprintAnimations ids 1..40; 1 = None).
     */
    public static final String[][] ANIM_SET_DEFS = {
        {(com.hammad.redmagictweaks.BuildConfig.APP_NAME + " · Pulse Ring"), "pulse_ring"},
        {(com.hammad.redmagictweaks.BuildConfig.APP_NAME + " · Scan Lines"), "scan_lines"},
        {"Imported (folder picked below)", Frames.CUSTOM},
        {"None (hide the stock effect)", "nl:1"},
        {"Bloom", "nl:2"}, {"Cyber", "nl:3"}, {"Electronic", "nl:4"}, {"Fireworks", "nl:5"}, {"Keqings", "nl:6"},
        {"Magic", "nl:7"}, {"Pacman", "nl:8"}, {"Scan", "nl:9"}, {"Simple", "nl:10"}, {"Smoke", "nl:11"},
        {"Warp", "nl:12"}, {"Worm Hole", "nl:13"}, {"MIUI Normal", "nl:14"}, {"MIUI AOD", "nl:15"},
        {"MIUI Aurora", "nl:16"}, {"MIUI Aurora CAS", "nl:17"}, {"MIUI Light", "nl:18"}, {"MIUI Pop", "nl:19"},
        {"MIUI Pulse", "nl:20"}, {"MIUI Pulse White", "nl:21"}, {"MIUI Rhythm", "nl:22"}, {"MIUI Star CAS", "nl:23"},
        {"OnePlus Energy", "nl:24"}, {"OnePlus Mclaren", "nl:25"}, {"OnePlus Ripple", "nl:26"}, {"OnePlus Scanning", "nl:27"},
        {"OnePlus Stripe", "nl:28"}, {"OnePlus Wave", "nl:29"}, {"PureView DNA", "nl:30"}, {"PureView Future", "nl:31"},
        {"PureView Halo Ring", "nl:32"}, {"PureView Molecular", "nl:33"}, {"ROG Fusion", "nl:34"}, {"ROG Pulsar", "nl:35"},
        {"ROG Supernova", "nl:36"}, {"Genshin Fire", "nl:37"}, {"Bubble", "nl:38"}, {"Stardust", "nl:39"}, {"Swirl", "nl:40"},
    };
    public static final String[] ANIM_SETS = labels(ANIM_SET_DEFS);
    /** First ANIM_SET_DEFS index that is an atlas set. */
    public static final int ANIM_FIRST_ATLAS = 3;

    /** nubia-ly atlas id of a set id, or 0 when the set is a frame store (assets / imported). */
    public static int atlasId(String setId) {
        if (setId == null || !setId.startsWith(ATLAS_PREFIX)) return 0;
        try { int id = Integer.parseInt(setId.substring(ATLAS_PREFIX.length())); return FpArtwork.atlasFrames(id) > 0 ? id : 0; }
        catch (NumberFormatException e) { return 0; }
    }

    /**
     * Frame timing for frame-store sets (assets / imported). Index 0 is the default; index 3 stretches the frames
     * over the phone's own scan animation like the RisingOS sets always do. Atlas sets ignore this setting.
     */
    public static final String[] INTERVALS = {"33 ms per frame (30 fps)", "16 ms per frame (60 fps)", "50 ms per frame (20 fps)", "Match the stock animation length"};
    /** Milliseconds per frame; 0 = stretch over the stock animation. */
    public static final int[] INTERVAL_MS = {33, 16, 50, 0};

    /** Fraction of the stock animation area the frames are drawn into; index 0 fills it like stock. */
    public static final String[] SIZES = {"Stock (fill the animation area)", "Large (85 %)", "Medium (70 %)", "Small (55 %)", "Extra large (115 %, edges cropped)"};
    public static final int[] SIZE_PERCENT = {100, 85, 70, 55, 115};

    /**
     * Idle-icon styles. 0..4 are drawn by code (nubia-ly Rings / Scan frame / Hexagon / Diamond + Dot) in the colour
     * below, 5 and 6 take the picture from the selected frame set, 7+ are the RisingOS UDFPS icons shipped with
     * nubia-ly (FingerprintStyles 5..188; the "Rising · " labels are kept).
     */
    public static final String[][] ICON_STYLE_DEFS = {
        {"Rings", ""}, {"Scan frame", ""}, {"Hexagon", ""}, {"Diamond", ""}, {"Dot", ""},
        {"Icon shipped with the animation set (icon.webp)", ""},
        {"First frame of the animation set", ""},
        {"Rising · Default", "rising_fp_udfps_icon_default"},
        {"Rising · Rising", "rising_fp_udfps_icon_rising"},
        {"Rising · Miui", "rising_fp_udfps_icon_miui"},
        {"Rising · Oneplus Fp", "rising_fp_oneplus_fp"},
        {"Rising · Op", "rising_fp_udfps_icon_op"},
        {"Rising · Op 2077", "rising_fp_udfps_icon_op_2077"},
        {"Rising · Op Rainbow", "rising_fp_udfps_icon_op_rainbow"},
        {"Rising · Default 1", "rising_fp_udfps_icon_default_1"},
        {"Rising · Default 2", "rising_fp_udfps_icon_default_2"},
        {"Rising · Default 3", "rising_fp_udfps_icon_default_3"},
        {"Rising · Default 4", "rising_fp_udfps_icon_default_4"},
        {"Rising · Default 5", "rising_fp_udfps_icon_default_5"},
        {"Rising · Huawei", "rising_fp_udfps_icon_huawei"},
        {"Rising · Madness V2", "rising_fp_udfps_icon_madness_v2"},
        {"Rising · Unfunnyguy", "rising_fp_udfps_icon_unfunnyguy"},
        {"Rising · Yield", "rising_fp_udfps_icon_yield"},
        {"Rising · Misc1", "rising_fp_udfps_icon_misc1"},
        {"Rising · Misc2", "rising_fp_udfps_icon_misc2"},
        {"Rising · Misc3", "rising_fp_udfps_icon_misc3"},
        {"Rising · Misc4", "rising_fp_udfps_icon_misc4"},
        {"Rising · Misc5", "rising_fp_udfps_icon_misc5"},
        {"Rising · Misc 1", "rising_fp_udfps_misc_1"},
        {"Rising · Misc 2", "rising_fp_udfps_misc_2"},
        {"Rising · Misc 3", "rising_fp_udfps_misc_3"},
        {"Rising · Misc 4", "rising_fp_udfps_misc_4"},
        {"Rising · Misc 5", "rising_fp_udfps_misc_5"},
        {"Rising · Misc 6", "rising_fp_udfps_misc_6"},
        {"Rising · Misc 7", "rising_fp_udfps_misc_7"},
        {"Rising · Arc Reactor", "rising_fp_udfps_icon_arc_reactor"},
        {"Rising · Batman", "rising_fp_udfps_icon_batman"},
        {"Rising · Batman Flat", "rising_fp_udfps_icon_batman_flat"},
        {"Rising · Cpt America Flat", "rising_fp_udfps_icon_cpt_america_flat"},
        {"Rising · Cpt America Flat Gray", "rising_fp_udfps_icon_cpt_america_flat_gray"},
        {"Rising · Dragon Black Flat", "rising_fp_udfps_icon_dragon_black_flat"},
        {"Rising · Fire Ice Ouroboros", "rising_fp_udfps_icon_fire_ice_ouroboros"},
        {"Rising · Keqings", "rising_fp_udfps_icon_keqings"},
        {"Rising · Mclaren", "rising_fp_udfps_icon_mclaren"},
        {"Rising · Mi", "rising_fp_udfps_icon_mi"},
        {"Rising · Neon Arc", "rising_fp_udfps_icon_neon_arc"},
        {"Rising · Neon Arc Gray", "rising_fp_udfps_icon_neon_arc_gray"},
        {"Rising · Neon Triangle", "rising_fp_udfps_icon_neon_triangle"},
        {"Rising · Pacman", "rising_fp_udfps_icon_pacman"},
        {"Rising · Pokeball", "rising_fp_udfps_icon_pokeball"},
        {"Rising · Pokeball 1", "rising_fp_udfps_icon_pokeball_1"},
        {"Rising · Pokeball 2", "rising_fp_udfps_icon_pokeball_2"},
        {"Rising · Rainbow Horn", "rising_fp_udfps_icon_rainbow_horn"},
        {"Rising · Scratch Pink Blue", "rising_fp_udfps_icon_scratch_pink_blue"},
        {"Rising · Scratch Red Blue", "rising_fp_udfps_icon_scratch_red_blue"},
        {"Rising · Shooky", "rising_fp_udfps_icon_shooky"},
        {"Rising · Shouganai", "rising_fp_udfps_icon_shouganai"},
        {"Rising · Spiderman", "rising_fp_udfps_icon_spiderman"},
        {"Rising · Spiral Blue", "rising_fp_udfps_icon_spiral_blue"},
        {"Rising · Sun Metro", "rising_fp_udfps_icon_sun_metro"},
        {"Rising · Genshin Fire", "rising_fp_udfps_icon_genshin_fire"},
        {"Rising · Coreflame Death", "rising_fp_icon_coreflame_death"},
        {"Rising · Coreflame Earth", "rising_fp_icon_coreflame_earth"},
        {"Rising · Coreflame Genesis", "rising_fp_icon_coreflame_genesis"},
        {"Rising · Coreflame Law", "rising_fp_icon_coreflame_law"},
        {"Rising · Coreflame Ocean", "rising_fp_icon_coreflame_ocean"},
        {"Rising · Coreflame Passage", "rising_fp_icon_coreflame_passage"},
        {"Rising · Coreflame Reason", "rising_fp_icon_coreflame_reason"},
        {"Rising · Coreflame Romance", "rising_fp_icon_coreflame_romance"},
        {"Rising · Coreflame Sky", "rising_fp_icon_coreflame_sky"},
        {"Rising · Coreflame Strife", "rising_fp_icon_coreflame_strife"},
        {"Rising · Coreflame Time", "rising_fp_icon_coreflame_time"},
        {"Rising · Coreflame Trickery", "rising_fp_icon_coreflame_trickery"},
        {"Rising · Coreflame Worldbearing", "rising_fp_icon_coreflame_worldbearing"},
        {"Rising · Path Abundance", "rising_fp_path_abundance"},
        {"Rising · Path Destruction", "rising_fp_path_destruction"},
        {"Rising · Path Elation", "rising_fp_path_elation"},
        {"Rising · Path Enigmata", "rising_fp_path_enigmata"},
        {"Rising · Path Equilibrium", "rising_fp_path_equilibrium"},
        {"Rising · Path Erudition", "rising_fp_path_erudition"},
        {"Rising · Path Harmony", "rising_fp_path_harmony"},
        {"Rising · Path Nihility", "rising_fp_path_nihility"},
        {"Rising · Path Order", "rising_fp_path_order"},
        {"Rising · Path Preservation", "rising_fp_path_preservation"},
        {"Rising · Path Propagation", "rising_fp_path_propagation"},
        {"Rising · Path Remembrance", "rising_fp_path_remembrance"},
        {"Rising · Path The Hunt", "rising_fp_path_the_hunt"},
        {"Rising · Path Trailblaze", "rising_fp_path_trailblaze"},
        {"Rising · Path Voracity", "rising_fp_path_voracity"},
        {"Rising · Type Fire", "rising_fp_type_fire"},
        {"Rising · Type Ice", "rising_fp_type_ice"},
        {"Rising · Type Imaginary", "rising_fp_type_imaginary"},
        {"Rising · Type Lightning", "rising_fp_type_lightning"},
        {"Rising · Type Physical", "rising_fp_type_physical"},
        {"Rising · Type Quantum", "rising_fp_type_quantum"},
        {"Rising · Type Wind", "rising_fp_type_wind"},
        {"Rising · Acheron Ambush", "rising_fp_profile_picture_acheron_ambush"},
        {"Rising · Aglaea Fashion", "rising_fp_profile_picture_aglaea_fashion"},
        {"Rising · Anaxa Ingenuity", "rising_fp_profile_picture_anaxa_ingenuity"},
        {"Rising · Aventurine Bullseye", "rising_fp_profile_picture_aventurine_bullseye"},
        {"Rising · Awooawoo", "rising_fp_profile_picture_awooawoo"},
        {"Rising · Bailu Medical Practice", "rising_fp_profile_picture_bailu_medical_practice"},
        {"Rising · Ball Bat By Name", "rising_fp_profile_picture_ball_bat_by_name"},
        {"Rising · Bang Bang Bump", "rising_fp_profile_picture_bang_bang_bump"},
        {"Rising · Black Swan Recollection", "rising_fp_profile_picture_black_swan_recollection"},
        {"Rising · Blade Dinner Party", "rising_fp_profile_picture_blade_dinner_party"},
        {"Rising · Boothill Ambush", "rising_fp_profile_picture_boothill_ambush"},
        {"Rising · Bronya Celebration", "rising_fp_profile_picture_bronya_celebration"},
        {"Rising · Brother Hanu", "rising_fp_profile_picture_brother_hanu"},
        {"Rising · Caelus Star Rail", "rising_fp_profile_picture_caelus_star_rail"},
        {"Rising · Caelus The Writer", "rising_fp_profile_picture_caelus_the_writer"},
        {"Rising · Caelus Welcome", "rising_fp_profile_picture_caelus_welcome"},
        {"Rising · Castorice Ingenuity", "rising_fp_profile_picture_castorice_ingenuity"},
        {"Rising · Cerydra Conquest", "rising_fp_profile_picture_cerydra_conquest"},
        {"Rising · Chef Pom Pom", "rising_fp_profile_picture_chef_pom_pom"},
        {"Rising · Cipher Sweet Slumber", "rising_fp_profile_picture_cipher_sweet_slumber"},
        {"Rising · Clockie", "rising_fp_profile_picture_clockie"},
        {"Rising · Cyrene Keeping Promise", "rising_fp_profile_picture_cyrene_keeping_promise"},
        {"Rising · Dan Heng Permansor Terrae Lurking", "rising_fp_profile_picture_dan_heng_permansor_terrae_lurking"},
        {"Rising · Dan Heng Welcome", "rising_fp_profile_picture_dan_heng_welcome"},
        {"Rising · Diting", "rising_fp_profile_picture_diting"},
        {"Rising · Double Prosperity", "rising_fp_profile_picture_double_prosperity"},
        {"Rising · Dr Ratio Academia", "rising_fp_profile_picture_dr_ratio_academia"},
        {"Rising · Dream Ticker", "rising_fp_profile_picture_dream_ticker"},
        {"Rising · Duomension City Mobilization", "rising_fp_profile_picture_duomension_city_mobilization"},
        {"Rising · Evernight Lurking", "rising_fp_profile_picture_evernight_lurking"},
        {"Rising · Fast And Furynuts", "rising_fp_profile_picture_fast_and_furynuts"},
        {"Rising · Feixiao Fitness", "rising_fp_profile_picture_feixiao_fitness"},
        {"Rising · Final Victor", "rising_fp_profile_picture_final_victor"},
        {"Rising · Firefly Travel", "rising_fp_profile_picture_firefly_travel"},
        {"Rising · Firefly Vision", "rising_fp_profile_picture_firefly_vision"},
        {"Rising · Fu Xuan In Leisure", "rising_fp_profile_picture_fu_xuan_in_leisure"},
        {"Rising · Gepard Celebration", "rising_fp_profile_picture_gepard_celebration"},
        {"Rising · Hamster Ball Knight", "rising_fp_profile_picture_hamster_ball_knight"},
        {"Rising · Herta Tea Break", "rising_fp_profile_picture_herta_tea_break"},
        {"Rising · Hi Come For A Test", "rising_fp_profile_picture_hi_come_for_a_test"},
        {"Rising · Himeko Welcome", "rising_fp_profile_picture_himeko_welcome"},
        {"Rising · Hyacine Sweet Slumber", "rising_fp_profile_picture_hyacine_sweet_slumber"},
        {"Rising · Hysilens Conquest", "rising_fp_profile_picture_hysilens_conquest"},
        {"Rising · Janus Steed", "rising_fp_profile_picture_janus_steed"},
        {"Rising · Jiaoqiu Medical Practice", "rising_fp_profile_picture_jiaoqiu_medical_practice"},
        {"Rising · Jingliu Odyssey", "rising_fp_profile_picture_jingliu_odyssey"},
        {"Rising · Jing Yuan In Leisure", "rising_fp_profile_picture_jing_yuan_in_leisure"},
        {"Rising · Junjun", "rising_fp_profile_picture_junjun"},
        {"Rising · Kafka Dinner Party", "rising_fp_profile_picture_kafka_dinner_party"},
        {"Rising · Kakavasha", "rising_fp_profile_picture_kakavasha"},
        {"Rising · Listen Up", "rising_fp_profile_picture_listen_up"},
        {"Rising · March 7Th Festival", "rising_fp_profile_picture_march_7th_festival"},
        {"Rising · March 7Th Welcome", "rising_fp_profile_picture_march_7th_welcome"},
        {"Rising · Mem", "rising_fp_profile_picture_mem"},
        {"Rising · Moze Fitness", "rising_fp_profile_picture_moze_fitness"},
        {"Rising · Mydei Delicacies", "rising_fp_profile_picture_mydei_delicacies"},
        {"Rising · One Year Anniversary Commemoration", "rising_fp_profile_picture_one_year_anniversary_commemoration"},
        {"Rising · On The Mural", "rising_fp_profile_picture_on_the_mural"},
        {"Rising · Origami Bird", "rising_fp_profile_picture_origami_bird"},
        {"Rising · Owlbert Bath", "rising_fp_profile_picture_owlbert_bath"},
        {"Rising · Peppy", "rising_fp_profile_picture_peppy"},
        {"Rising · Phainon Fashion", "rising_fp_profile_picture_phainon_fashion"},
        {"Rising · Polyxia", "rising_fp_profile_picture_polyxia"},
        {"Rising · Rappa Opening", "rising_fp_profile_picture_rappa_opening"},
        {"Rising · Ripples Of Past Reverie Keeping Promise", "rising_fp_profile_picture_ripples_of_past_reverie_keeping_promise"},
        {"Rising · Ruan Mei Academia", "rising_fp_profile_picture_ruan_mei_academia"},
        {"Rising · Ruan Mei Moments Delight", "rising_fp_profile_picture_ruan_mei_moments_delight"},
        {"Rising · Sampo Illusion", "rising_fp_profile_picture_sampo_illusion"},
        {"Rising · Sam Vision", "rising_fp_profile_picture_sam_vision"},
        {"Rising · Silver Wolf Opening", "rising_fp_profile_picture_silver_wolf_opening"},
        {"Rising · Snack Life", "rising_fp_profile_picture_snack_life"},
        {"Rising · Sparkle Doll", "rising_fp_profile_picture_sparkle_doll"},
        {"Rising · Sparkle Illusion", "rising_fp_profile_picture_sparkle_illusion"},
        {"Rising · Sparxies Dance", "rising_fp_profile_picture_sparxies_dance"},
        {"Rising · Spirithief", "rising_fp_profile_picture_spirithief"},
        {"Rising · Stelle Star Rail", "rising_fp_profile_picture_stelle_star_rail"},
        {"Rising · Stelle The Writer", "rising_fp_profile_picture_stelle_the_writer"},
        {"Rising · Stelle Welcome", "rising_fp_profile_picture_stelle_welcome"},
        {"Rising · Sunday Brilliant Youth", "rising_fp_profile_picture_sunday_brilliant_youth"},
        {"Rising · The Golden Nymph", "rising_fp_profile_picture_the_golden_nymph"},
        {"Rising · The Herta Tea Break", "rising_fp_profile_picture_the_herta_tea_break"},
        {"Rising · The Mini Dromas Is Growing Up", "rising_fp_profile_picture_the_mini_dromas_is_growing_up"},
        {"Rising · Topaz Bullseye", "rising_fp_profile_picture_topaz_bullseye"},
        {"Rising · Trailblazing In Progress", "rising_fp_profile_picture_trailblazing_in_progress"},
        {"Rising · Trash Can", "rising_fp_profile_picture_trash_can"},
        {"Rising · Trianne Doll", "rising_fp_profile_picture_trianne_doll"},
        {"Rising · Tribbie Delicacies", "rising_fp_profile_picture_tribbie_delicacies"},
        {"Rising · Wanted Poster", "rising_fp_profile_picture_wanted_poster"},
        {"Rising · Welt Welcome", "rising_fp_profile_picture_welt_welcome"},
        {"Rising · Wubbaboo", "rising_fp_profile_picture_wubbaboo"},
        {"Rising · Yanqing Dreamland", "rising_fp_profile_picture_yanqing_dreamland"},
        {"Rising · Yanqing Odyssey", "rising_fp_profile_picture_yanqing_odyssey"},
        {"Rising · Yao Guang Ethereal Dance", "rising_fp_profile_picture_yao_guang_ethereal_dance"},
        {"Rising · You Cant See Me", "rising_fp_profile_picture_you_cant_see_me"},
        {"Rising · Transparent", "rising_fp_udfps_icon_transparent"},
    };
    public static final String[] ICON_STYLES = labels(ICON_STYLE_DEFS);
    public static final int ICON_SET_ICON = 5, ICON_SET_FRAME = 6, ICON_FIRST_ART = 7;

    /** Drawable resource name of an artwork icon style, or null for the code-drawn / frame-set styles. */
    public static String iconResource(int style) {
        if (style < ICON_FIRST_ART || style >= ICON_STYLE_DEFS.length) return null;
        return ICON_STYLE_DEFS[style][1];
    }

    /** Colour of the code-drawn icon styles. Index 0 follows the stock keyguard colour (white / black). */
    public static final String[] ICON_COLORS = {"Follow lock screen (stock)", "White", "RedMagic red", "Orange", "Gold", "Cyan", "Black"};
    public static final int[] ICON_COLOR_ARGB = {0, 0xffffffff, 0xffff3030, 0xffff8c1e, 0xffffc850, 0xff38e0ff, 0xff000000};

    public static final String[] ICON_SIZES = {"Stock", "80 %", "120 %", "140 %"};
    public static final int[] ICON_SIZE_PERCENT = {100, 80, 120, 140};

    private static String[] labels(String[][] v) { String[] l = new String[v.length]; for (int i = 0; i < v.length; i++) l[i] = v[i][0]; return l; }

    public static final Features.Section SECTION = new Features.Section("fpcustom", "Fingerprint icon & animation",
        "RisingOS icons and scan effects from nubia-ly, your own frames, hide the icon",
        R.drawable.ic_fingerprint,
        new String[]{"com.fingerprint.sensorservice", "com.android.systemui"},
        new Features.Feature(FP_ANIM, "Custom fingerprint animation", "Replaces the picture of the stock effect while your finger rests on the sensor with the set chosen below. Position and scan duration follow the phone's own animation, so keep any stock animation style selected in Settings (not \"none\"). Applies on the next press."),
        new Features.Feature(FP_ANIM_SET, "Animation set", ("RisingOS effects (MIUI, OnePlus, PureView, ROG…) ported from nubia-ly, two " + com.hammad.redmagictweaks.BuildConfig.APP_NAME + " sets, or the folder you import below. \"None\" plays nothing while the stock effect would."), Features.Type.CHOICE, ANIM_SETS, 0),
        new Features.Feature(FP_ANIM_INTERVAL, ("Frame timing (" + com.hammad.redmagictweaks.BuildConfig.APP_NAME + " / imported sets)"), "How fast imported frames play; the RisingOS sets always stretch over the stock scan. \"Match the stock animation length\" does the same for your own frames.", Features.Type.CHOICE, INTERVALS, 0),
        new Features.Feature(FP_ANIM_SIZE, "Animation size", "Scales the effect inside the stock animation area around the sensor.", Features.Type.CHOICE, SIZES, 0),
        new Features.Feature(FP_IMPORT, "Import custom fingerprint frames…", "Pick a folder of PNG/WEBP/JPG frames (sorted by file name, square with a transparent background recommended, up to 400 frames; the first 120 are used). A file named icon.png / icon.webp in the same folder becomes the idle icon for the \"Icon shipped with the animation set\" style.", Features.Type.ACTION, null, 0),
        new Features.Feature(FP_ICON, "Custom idle fingerprint icon", "Replaces the fingerprint symbol shown on the lock screen (and the PIN screen) with the style chosen below. Enrollment, in-app fingerprint prompts and the Always-on display keep the stock symbol. Lock and unlock once after changing."),
        new Features.Feature(FP_ICON_STYLE, "Icon style", "Code-drawn shapes follow the colour below; the RisingOS artwork keeps its own colours; two styles take the picture from the selected animation set.", Features.Type.CHOICE, ICON_STYLES, 0),
        new Features.Feature(FP_ICON_COLOR, "Icon colour", "Only used by the code-drawn icon styles (Rings, Scan frame, Hexagon, Diamond, Dot).", Features.Type.CHOICE, ICON_COLORS, 0),
        new Features.Feature(FP_ICON_SIZE, "Icon size", "Scales the idle icon relative to the stock size.", Features.Type.CHOICE, ICON_SIZES, 0),
        new Features.Feature(FP_ICON_HIDE, "Hide fingerprint icon", "Shows no idle fingerprint symbol at all on the lock screen and PIN screen; touching the sensor position still unlocks. Overrides the custom icon. Lock and unlock once after changing.")
    );

    // ------------------------------------------------------------------------------------------ previews

    static {
        Previews.register(FP_ANIM_SET, FingerprintCustomFeatures::animPreview, "Tap to preview · plays while your finger rests on the sensor; position and scan duration follow the phone's own animation");
        Previews.registerFrames(FP_ANIM_SET, FingerprintCustomFeatures::animFrames);
        Previews.register(FP_ICON_STYLE, FingerprintCustomFeatures::iconPreview, "Idle symbol on the lock screen · \"Rising\" artwork is RisingOS UDFPS art (Apache 2.0) via nubia-ly");
        Previews.register(FP_ICON_COLOR, (ctx, i) -> i <= 0 || i >= ICON_COLOR_ARGB.length ? null : new FpArtwork.Dot(ICON_COLOR_ARGB[i]));
    }

    /** Accent colour of the app theme for code-drawn glyph previews (falls back to RedMagic red). */
    private static int accent(Context ctx) {
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (ctx.getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true) && tv.data != 0) return tv.data;
        } catch (Throwable ignored) { }
        return 0xffff3030;
    }

    private static Drawable res(Context ctx, String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            int id = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
            return id == 0 ? null : ctx.getDrawable(id);
        } catch (Throwable t) { return null; }
    }

    /** First / representative frame of a set: asset frame, imported frame, or the RisingOS thumbnail. */
    private static Drawable animPreview(Context ctx, int i) {
        if (i < 0 || i >= ANIM_SET_DEFS.length) return null;
        String set = ANIM_SET_DEFS[i][1];
        int atlas = atlasId(set);
        if (atlas > 0) return res(ctx, "rising_anim_preview_" + atlas);
        if (Frames.CUSTOM.equals(set)) return Previews.file(Frames.fpFrame(ctx, 0));
        String path = Frames.fpAssetPath(ctx, set, 0);
        return path == null ? null : Previews.asset(ctx, path);
    }

    /** All frames of a set for the animated preview dialog (atlas tiles share one bitmap). */
    private static Drawable[] animFrames(Context ctx, int i) {
        if (i < 0 || i >= ANIM_SET_DEFS.length) return null;
        String set = ANIM_SET_DEFS[i][1];
        int atlas = atlasId(set);
        if (atlas > 0) return FpArtwork.atlasDrawables(ctx, atlas);
        List<Drawable> out = new ArrayList<>();
        if (Frames.CUSTOM.equals(set)) {
            File[] files = Frames.fpList(ctx);
            for (int k = 0; k < files.length && k < 90; k++) { Drawable d = Previews.file(files[k]); if (d != null) out.add(d); }
        } else {
            int n = Math.min(90, Frames.fpAssetCount(ctx, set));
            for (int k = 0; k < n; k++) { String p = Frames.fpAssetPath(ctx, set, k); Drawable d = p == null ? null : Previews.asset(ctx, p); if (d != null) out.add(d); }
        }
        return out.isEmpty() ? null : out.toArray(new Drawable[0]);
    }

    private static Drawable iconPreview(Context ctx, int i) {
        if (i < 0 || i >= ICON_STYLE_DEFS.length) return null;
        if (i < ICON_SET_ICON) return new FpArtwork.Glyph(i, accent(ctx), null, null, 100);
        if (i == ICON_SET_ICON || i == ICON_SET_FRAME) {
            int sel = Prefs.choice(ctx, FP_ANIM_SET);
            String set = sel >= 0 && sel < ANIM_SET_DEFS.length ? ANIM_SET_DEFS[sel][1] : ANIM_SET_DEFS[0][1];
            if (atlasId(set) > 0) {
                Bitmap atlas = FpArtwork.atlas(ctx, atlasId(set));     // RisingOS sets have no separate icon: first tile for both styles
                return atlas == null ? null : new FpArtwork.AtlasFrame(atlas, 0);
            }
            if (Frames.CUSTOM.equals(set)) {
                File icon = i == ICON_SET_ICON ? Frames.fpIcon(ctx) : null;
                return Previews.file(icon != null ? icon : Frames.fpFrame(ctx, 0));
            }
            String p = i == ICON_SET_ICON ? Frames.fpAssetIcon(ctx, set) : null;
            if (p == null) p = Frames.fpAssetPath(ctx, set, 0);
            return p == null ? null : Previews.asset(ctx, p);
        }
        return res(ctx, iconResource(i));
    }
}
