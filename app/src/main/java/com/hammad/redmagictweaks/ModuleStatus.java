package com.hammad.redmagictweaks;

/** Replaced by the module's own hook when it is active in LSPosed. */
public final class ModuleStatus {
    private ModuleStatus() {}
    /** Screenshot tests only (the hook replaces isActive itself). */
    static boolean testActive;
    public static boolean isActive() { return testActive; }
}
