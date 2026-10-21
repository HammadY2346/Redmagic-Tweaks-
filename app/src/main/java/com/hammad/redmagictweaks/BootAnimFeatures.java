package com.hammad.redmagictweaks;

/** Boot animation page (nubia-ly's boot animations + your own archive), applied by BootAnim through root. */
public final class BootAnimFeatures {
    private BootAnimFeatures() {}

    public static final String BOOTANIM = "bootanim_choice";
    public static final String BOOTANIM_IMPORT = "bootanim_import";
    public static final String BOOTANIM_STATUS = "bootanim_status";

    /** {id, label}: the RisingOS set nubia-ly ships (assets/bootanimations, built by CI), stock and an imported archive. */
    public static final String[][] DEFS = {
        {"stock", "Stock (RedMagic)"},
        {"rising", "Rising"},
        {"cyberpunk", "Cyberpunk"},
        {"valorant", "Valorant"},
        {"google", "Google"},
        {"google_monet", "Google Monet"},
        {"custom", "Your own (imported .zip)"},
    };
    public static final String[] CHOICES = new String[DEFS.length];
    static { for (int i = 0; i < DEFS.length; i++) CHOICES[i] = DEFS[i][1]; }

    public static String id(int index) { return index < 0 || index >= DEFS.length ? "stock" : DEFS[index][0]; }

    public static final Features.Section SECTION = new Features.Section("bootanim", "Boot animation",
        "nubia-ly's boot animations or your own, shown from the next boot (root; the stock file is never changed)",
        R.drawable.ic_power,
        new String[]{"boot"},
        new Features.Feature(BOOTANIM, "Boot animation",
            "Rising, Cyberpunk, Valorant, Google and Google Monet (from nubia-ly / RisingOS), the stock one, or your own archive. "
            + ("Picking one stages it right away with root; it plays from the next boot. A small helper module (" + com.hammad.redmagictweaks.BuildConfig.APP_NAME + " · boot animation) ")
            + "shows it at boot without touching the original file, and goes back to stock by itself after two boots in a row that don't finish. Reboot to see it.",
            Features.Type.CHOICE, CHOICES, 0),
        new Features.Feature(BOOTANIM_IMPORT, "Import your own boot animation…",
            "Pick a bootanimation.zip (desc.txt + part0/, part1/… frames, stored uncompressed). Then choose \"Your own\" above.",
            Features.Type.ACTION, null, 0),
        new Features.Feature(BOOTANIM_STATUS, "Status / restore stock",
            "What is selected, what the last boot did, and a button back to the stock animation.",
            Features.Type.ACTION, null, 0)
    );

    static {
        Previews.register(BOOTANIM, BootAnim::thumb, "Tap to play the preview · shown from the next boot");
        Previews.registerFrames(BOOTANIM, BootAnim::frames);
    }
}
