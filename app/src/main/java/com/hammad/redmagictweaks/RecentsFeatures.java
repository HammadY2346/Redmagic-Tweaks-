package com.hammad.redmagictweaks;

import com.hammad.redmagictweaks.xposed.RecentsArt;

/** Recent apps of the stock launcher: card styles (xposed/RecentsHooks, art and card motion in xposed/RecentsArt). */
public final class RecentsFeatures {
    private RecentsFeatures() {}

    public static final Features.Section SECTION = new Features.Section("recents", "Recent apps",
        "Card styles for the stock launcher's recent apps: One UI 8, carousel, stack, cover flow, grid",
        R.drawable.ic_recents,
        new String[]{"com.zte.mifavor.launcher"},
        new Features.Feature(RecentsArt.KEY_STYLE, "Recent apps style",
            "One UI 8 = Samsung's look: the app icon centred over each card, no name or buttons (tap the icon for the menu), rounder and slightly smaller cards, a \"Close all\" pill at the bottom. "
            + "Carousel = the card in the middle is full size and the others shrink towards the sides. Stack = iOS-style deck, every card lies over the one on its left. "
            + "Cover flow = the side cards turn towards the middle in 3D. Grid / List = the launcher's own two layouts. Stock = whatever the launcher's own setting says. "
            + "The options below can change each part of a style. Restart the launcher after changing.",
            Features.Type.CHOICE, RecentsArt.STYLES, 0),
        new Features.Feature(RecentsArt.KEY_HEADER, "Icon and name above the cards",
            "Auto follows the style (One UI 8 = icon in the middle, the others stock). Without the stock row the mini-window, split and ⋮ buttons are gone: tap the app icon for the same menu. Restart the launcher.",
            Features.Type.CHOICE, RecentsArt.HEADERS, 0),
        new Features.Feature(RecentsArt.KEY_CLEAR, "Close all button",
            "The round button under the cards, a One UI \"Close all\" pill, or no button. Auto follows the style. Restart the launcher.",
            Features.Type.CHOICE, RecentsArt.CLEARS, 0),
        new Features.Feature(RecentsArt.KEY_CORNERS, "Card corners",
            "Corner roundness of the app screenshots; the swipe-up animation morphs into the same corners. Auto: One UI 8 26 dp, carousel and cover flow 24 dp, stack 22 dp, else stock 16 dp. Restart the launcher.",
            Features.Type.CHOICE, RecentsArt.CORNERS, 0).num(0, 48, 1, "dp"),
        new Features.Feature(RecentsArt.KEY_SIZE, "Card size",
            "Smaller cards show more of the apps next to them. Auto: One UI 8 90 %, carousel and stack 92 %, cover flow 86 %. Only the list layout. Restart the launcher.",
            Features.Type.CHOICE, RecentsArt.SIZES, 0).num(60, 100, 1, "%")
    );

    static {
        for (final String key : new String[]{RecentsArt.KEY_STYLE, RecentsArt.KEY_HEADER, RecentsArt.KEY_CLEAR, RecentsArt.KEY_CORNERS, RecentsArt.KEY_SIZE}) {
            Previews.register(key, (ctx, i) -> LookPreview.recentsCrop(ctx, Looks.with(Looks.live(ctx), key, i), key), "Your other recent apps options stay as they are");
        }
    }
}
