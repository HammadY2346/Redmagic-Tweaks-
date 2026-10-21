package com.hammad.redmagictweaks.xposed;

import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * The drawer's "⋮" menu (stock: {@code AllAppsSortMenuView}, a vertical LinearLayout added to the DragLayer with the three
 * sort choices, positioned by {@code setSortMenuViewPosition(anchor)} BELOW the button with pivotY = 0).
 * <ul>
 *   <li>With the search bar at the bottom (One UI layout) "below" is off-screen, so the menu looked like it never opened:
 *       when it does not fit under the anchor it now opens ABOVE it, growing upwards (pivot at its bottom).</li>
 *   <li>With the custom layout on, two One UI entries are appended under the sort choices: "Edit" (the drawer edit mode,
 *       same as the Edit pill) and "Hidden apps".</li>
 * </ul>
 */
final class DrawerMenu {
    private DrawerMenu() {}

    private static final String MENU = "com.android.launcher3.allappsmenu.AllAppsSortMenuView";
    private static final int TAG_EXTRAS = 0x7f5e0d01;

    static void install(ClassLoader cl) {
        Class<?> menu = XposedHelpers.findClassIfExists(MENU, cl);
        if (menu == null) { Lx.w("Drawer menu: " + MENU + " not found"); return; }
        Method pos = null;
        for (Method m : menu.getDeclaredMethods())
            if (m.getName().equals("setSortMenuViewPosition") && m.getParameterTypes().length == 1) pos = m;
        if (pos == null) { Lx.w("Drawer menu: setSortMenuViewPosition not found"); return; }
        boolean ok = H.hook(pos, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try { addExtras((LinearLayout) p.thisObject); } catch (Throwable t) { Lx.e("Drawer menu: extras", t); }
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { flipIfNeeded((View) p.thisObject, (View) p.args[0]); } catch (Throwable t) { Lx.e("Drawer menu: position", t); }
            }
        });
        Lx.i("Drawer menu: hooks " + (ok ? "ready" : "NOT installed"));
    }

    /** Open above the ⋮ button when the stock position (below it) would run past the bottom of the screen. */
    private static void flipIfNeeded(View menu, View anchor) {
        if (anchor == null) return;
        View root = anchor.getRootView();
        int screenH = root != null ? root.getHeight() : 0;
        if (screenH <= 0) return;
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int mh = menu.getMeasuredHeight();
        int pad = padding(menu);
        int nav = LauncherHooks.navInset(anchor);
        int bottomIfBelow = loc[1] + anchor.getHeight() + pad + mh;
        if (bottomIfBelow <= screenH - nav) return;                                 // fits: stock placement
        float y = menu.getY() - anchor.getHeight() - 2f * pad - mh;                 // same gap, other side of the button
        if (y < 0) y = 0;
        menu.setY(y);
        menu.setPivotY(mh);
        Lx.i("Drawer menu: opened above the ⋮ button (bottom search bar)");
    }

    private static int padding(View v) {
        try {
            int id = v.getResources().getIdentifier("mfvc_medium_padding", "dimen", v.getContext().getPackageName());
            if (id != 0) return v.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) { }
        return Math.round(8 * v.getResources().getDisplayMetrics().density);
    }

    /** "Edit" / "Hidden apps" under the sort choices, styled like them; only while the custom layout is in use. */
    private static void addExtras(final LinearLayout menu) {
        final LauncherHooks.State st = LauncherHooks.visibleState();
        boolean want = st != null && st.edit != null && st.edit.customOn();
        Object tag = menu.getTag(TAG_EXTRAS);
        if (!want) { if (tag instanceof View[]) for (View v : (View[]) tag) v.setVisibility(View.GONE); return; }
        if (tag instanceof View[]) { for (View v : (View[]) tag) v.setVisibility(View.VISIBLE); return; }
        TextView ref = null;
        for (int i = 0; i < menu.getChildCount(); i++)
            if (menu.getChildAt(i) instanceof TextView) { ref = (TextView) menu.getChildAt(i); break; }
        float density = menu.getResources().getDisplayMetrics().density;

        View divider = new View(menu.getContext());
        int lineColor = ref != null ? (ref.getCurrentTextColor() & 0x00FFFFFF) | 0x26000000 : 0x26888888;
        divider.setBackgroundColor(lineColor);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(density)));
        dlp.topMargin = dlp.bottomMargin = Math.round(4 * density);
        menu.addView(divider, dlp);

        TextView edit = item(menu, ref, "Edit");
        TextView hidden = item(menu, ref, "Hidden apps");
        edit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                close(menu);
                LauncherHooks.State s = LauncherHooks.visibleState();
                if (s != null && s.edit != null) s.edit.setEditing(true);
            }
        });
        hidden.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                close(menu);
                LauncherHooks.State s = LauncherHooks.visibleState();
                if (s != null && s.edit != null) s.edit.hiddenDialog();
            }
        });
        menu.setTag(TAG_EXTRAS, new View[]{divider, edit, hidden});
    }

    private static TextView item(LinearLayout menu, TextView ref, String text) {
        TextView tv = new TextView(menu.getContext());
        tv.setText(text);
        ViewGroup.LayoutParams lp;
        if (ref != null) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, ref.getTextSize());
            ColorStateList c = ref.getTextColors();
            if (c != null) tv.setTextColor(c);
            tv.setTypeface(ref.getTypeface());
            tv.setGravity(ref.getGravity());
            tv.setPadding(ref.getPaddingLeft(), ref.getPaddingTop(), ref.getPaddingRight(), ref.getPaddingBottom());
            tv.setMinHeight(ref.getMinHeight());
            tv.setMinWidth(ref.getMinWidth());
            if (ref.getBackground() != null && ref.getBackground().getConstantState() != null)
                tv.setBackground(ref.getBackground().getConstantState().newDrawable().mutate());
            lp = ref.getLayoutParams() instanceof LinearLayout.LayoutParams
                    ? new LinearLayout.LayoutParams((LinearLayout.LayoutParams) ref.getLayoutParams())
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            float d = menu.getResources().getDisplayMetrics().density;
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tv.setPadding(0, Math.round(14 * d), 0, Math.round(14 * d));
            lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        tv.setClickable(true);
        menu.addView(tv, lp);
        return tv;
    }

    private static void close(View menu) {
        try { XposedHelpers.callMethod(menu, "close", true); }
        catch (Throwable t) {
            try { ViewGroup parent = (ViewGroup) menu.getParent(); if (parent != null) parent.removeView(menu); } catch (Throwable ignored) { }
        }
    }

    @SuppressWarnings("unused") private static final Rect TMP = new Rect();
}
