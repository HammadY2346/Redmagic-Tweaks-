package com.hammad.redmagictweaks;

import android.content.Context;
import android.graphics.drawable.Drawable;

/** Lock screen preview with the current settings (drawn by {@link LookPreview}). */
public final class LockPreview {
    private LockPreview() {}

    public static Drawable render(Context ctx) { return render(ctx, Looks.live(ctx)); }

    public static Drawable render(Context ctx, Looks.V v) { return LookPreview.phone(ctx, v, LookPreview.LOCK, 170); }
}
