package com.hammad.redmagictweaks.xposed;

/**
 * Feature keys + choice tables of the paged-drawer extras that live in LauncherHooks / DrawerEdit, in a public class so
 * the app's Features catalogue (owned by the coordinator) can reference the same strings. Hooks read them through
 * {@code Cfg.on(KEY)} / {@code Cfg.choice(KEY)}; index 0 of every choice table is "Stock" (= off).
 */
public final class DrawerKeys {
    private DrawerKeys() {}

    /** CHOICE: gap between drawer pages (each page is inset by this much on both sides, One UI style). */
    public static final String KEY_PAGE_PAD = "launcher_drawer_page_pad";
    public static final String[] PAGE_PADS = {"Stock (none)", "12 dp", "16 dp", "20 dp", "24 dp"};
    public static final int[] PAGE_PAD_DP = {0, 12, 16, 20, 24};

    /** SWITCH: fixed grid slots in the custom layout (One UI): moving an icon leaves a gap, pages may be half empty. */
    public static final String KEY_FIXED_SLOTS = "launcher_drawer_fixed_slots";

    /** SWITCH: hide the search bar (and the menu button in it) at the top of the drawer. */
    public static final String KEY_HIDE_SEARCH = "launcher_drawer_hide_search";

    /**
     * CHOICE: where the drawer search bar sits. Bottom = One UI (pill above the nav bar, page dots right above it; while
     * searching it is back at the top like stock). Hidden = same as {@link #KEY_HIDE_SEARCH} (that older switch still wins).
     */
    public static final String KEY_SEARCH_POS = "launcher_drawer_search_pos";
    public static final String[] SEARCH_POSITIONS = {"Top (stock)", "Bottom (One UI)", "Hidden"};
    static final int SEARCH_TOP = 0, SEARCH_BOTTOM = 1, SEARCH_HIDDEN = 2;

    /** SWITCH: hide only the "⋮" menu button next to the drawer search bar. */
    public static final String KEY_HIDE_MENU = "launcher_drawer_hide_menu";

    /** CHOICE: app label text size in the drawer. */
    public static final String KEY_LABEL_SIZE = "launcher_drawer_label_size";
    public static final String[] LABEL_SIZES = {"Stock", "Small", "Smaller"};
    static final float[] LABEL_SCALE = {1f, 0.86f, 0.74f};

    /** CHOICE: how an opened drawer folder looks: the card popup, or One UI's full-screen folder (big title, blurred
     *  background, + and colour buttons, rounded 4-column panel). */
    public static final String KEY_FOLDER_OPEN = "launcher_folder_open_style";
    public static final String[] FOLDER_OPEN_STYLES = {"Card (popup)", "One UI (full screen)"};
    static boolean folderOneUi() { return Cfg.choice(KEY_FOLDER_OPEN) == 1; }

    static int pagePadDp() {
        int i = Cfg.choice(KEY_PAGE_PAD);
        return Math.max(0, Cfg.num(KEY_PAGE_PAD, i > 0 && i < PAGE_PAD_DP.length ? PAGE_PAD_DP[i] : 0));
    }

    private static int cachedSearchPos = -1;
    private static long cachedSearchAt;

    /** {@link #searchPos()} re-read at most every 500 ms (it is asked before every measure of the drawer container). */
    static int searchPosCached() {
        long now = android.os.SystemClock.uptimeMillis();
        if (cachedSearchPos < 0 || now - cachedSearchAt > 500) { cachedSearchPos = searchPos(); cachedSearchAt = now; }
        return cachedSearchPos;
    }

    static int searchPos() {
        if (Cfg.on(KEY_HIDE_SEARCH)) return SEARCH_HIDDEN;
        int i = Cfg.choice(KEY_SEARCH_POS);
        return i > 0 && i < SEARCH_POSITIONS.length ? i : SEARCH_TOP;
    }

    static float labelScale() {
        if (Cfg.hasNum(KEY_LABEL_SIZE)) return Math.max(0.3f, Cfg.num(KEY_LABEL_SIZE, 100) / 100f);
        int i = Cfg.choice(KEY_LABEL_SIZE);
        return i > 0 && i < LABEL_SCALE.length ? LABEL_SCALE[i] : 1f;
    }
}
