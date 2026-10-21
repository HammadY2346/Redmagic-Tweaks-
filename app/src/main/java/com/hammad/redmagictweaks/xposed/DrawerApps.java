package com.hammad.redmagictweaks.xposed;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.UserHandle;
import android.os.UserManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;

/**
 * Name-agnostic access to the launcher's all-apps model objects (fw 11.0.11, R8-obfuscated):
 * <ul>
 *   <li>BaseAllAppsAdapter (allapps.O) holds an AlphabeticalAppsList (allapps.M) whose {@code h()} returns the
 *       {@code List<AdapterItem>} the adapter binds from. We find that list by scanning the adapter's fields for an
 *       object with a public no-arg List getter whose size equals the adapter's real item count.</li>
 *   <li>AdapterItem (O$a) has a field holding the AppInfo (model.data.d) — found as the field whose value has
 *       {@code getTargetComponent()} (ItemInfo API names are kept).</li>
 *   <li>AppInfo: {@code getTargetComponent()}, public fields {@code title} / {@code user} (kept), and an
 *       ItemInfoWithIcon field holding a BitmapInfo whose first {@code Bitmap} field is the launcher-rendered icon.</li>
 * </ul>
 * Everything is cached per class after the first successful lookup and degrades to null, never throws.
 */
final class DrawerApps {
    private DrawerApps() {}

    private static Field itemsHolderField;      // adapter field -> AlphabeticalAppsList
    private static Method itemsMethod;          // AlphabeticalAppsList#h()
    private static Class<?> itemClass; private static Field itemInfoField;
    private static Class<?> infoClass; private static Field bitmapInfoField; private static Field bitmapField;
    private static final Map<UserHandle, Long> serials = new HashMap<>();
    private static boolean loggedItems, loggedInfo, loggedIcon;

    /** The adapter's backing item list, or null when it cannot be identified (yet). */
    static List<?> itemsOf(Object adapter, int realCount) {
        try {
            if (itemsHolderField != null && itemsMethod != null) {
                Object holder = itemsHolderField.get(adapter);
                Object l = holder == null ? null : itemsMethod.invoke(holder);
                if (l instanceof List) return (List<?>) l;
            }
            if (realCount <= 0) return null;
            // pass 1: the known accessor name h(); pass 2: any public no-arg List getter, but only on allapps.* objects
            for (int pass = 0; pass < 2 && itemsMethod == null; pass++) {
                for (Class<?> k = adapter.getClass(); k != null && !k.getName().startsWith("androidx.") && k != Object.class && itemsMethod == null; k = k.getSuperclass()) {
                    for (Field f : k.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                        f.setAccessible(true);
                        Object holder = f.get(adapter);
                        if (holder == null || holder instanceof Context || holder instanceof android.view.View || holder instanceof CharSequence) continue;
                        Method m = listGetter(holder, realCount, pass == 1);
                        if (m != null) {
                            itemsHolderField = f; itemsMethod = m;
                            if (!loggedItems) { loggedItems = true; Lx.i("Drawer: item list = " + k.getSimpleName() + "." + f.getName() + " (" + holder.getClass().getName() + ")#" + m.getName() + "()"); }
                            return (List<?>) m.invoke(holder);
                        }
                    }
                }
            }
        } catch (Throwable t) { Lx.e("Drawer: itemsOf", t); }
        if (!loggedItems) { loggedItems = true; Lx.w("Drawer: adapter item list not found (custom order/folders unavailable, positions only)"); }
        return null;
    }

    private static Method listGetter(Object holder, int realCount, boolean anyGetter) {
        Class<?> c = holder.getClass();
        if (c.getName().startsWith("java.") || c.getName().startsWith("android.")) return null;
        if (!anyGetter) {
            try {
                Method h = c.getMethod("h");
                if (List.class.isAssignableFrom(h.getReturnType()) && sizeMatches(h.invoke(holder), realCount)) return h;
            } catch (Throwable ignored) { }
            return null;
        }
        if (!c.getName().startsWith("com.android.launcher3.allapps.")) return null;
        for (Method m : c.getMethods()) {
            if (m.getParameterTypes().length != 0 || Modifier.isStatic(m.getModifiers()) || !List.class.isAssignableFrom(m.getReturnType())) continue;
            if (m.getDeclaringClass().getName().startsWith("java.")) continue;
            try { if (sizeMatches(m.invoke(holder), realCount)) return m; } catch (Throwable ignored) { }
        }
        return null;
    }

    private static boolean sizeMatches(Object l, int n) { return l instanceof List && ((List<?>) l).size() == n; }

    /** The AppInfo (ItemInfo subclass) carried by an adapter item, or null for non-app rows. */
    static Object appInfoOf(Object item) {
        if (item == null) return null;
        try {
            if (itemClass == item.getClass() && itemInfoField != null) return itemInfoField.get(item);
            for (Class<?> k = item.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    f.setAccessible(true);
                    Object v = f.get(item);
                    if (v != null && isItemInfo(v.getClass())) {
                        itemClass = item.getClass(); itemInfoField = f;
                        if (!loggedInfo) { loggedInfo = true; Lx.i("Drawer: AdapterItem." + f.getName() + " = " + v.getClass().getName()); }
                        return v;
                    }
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static final Map<Class<?>, Boolean> itemInfoClasses = new HashMap<>();

    /** ItemInfo API names are kept on this firmware: any class with getTargetComponent() somewhere up its chain. */
    private static boolean isItemInfo(Class<?> c) {
        Boolean b = itemInfoClasses.get(c);
        if (b == null) {
            b = !c.getName().startsWith("java.") && !c.getName().startsWith("android.") && H.find(c, "getTargetComponent") != null;
            itemInfoClasses.put(c, b);
        }
        return b;
    }

    static ComponentName componentOf(Object info) {
        if (info == null) return null;
        try { Object c = XposedHelpers.callMethod(info, "getTargetComponent"); if (c instanceof ComponentName) return (ComponentName) c; } catch (Throwable ignored) { }
        try { Object c = XposedHelpers.getObjectField(info, "componentName"); if (c instanceof ComponentName) return (ComponentName) c; } catch (Throwable ignored) { }
        return null;
    }

    static UserHandle userOf(Object info) {
        try { Object u = XposedHelpers.getObjectField(info, "user"); if (u instanceof UserHandle) return (UserHandle) u; } catch (Throwable ignored) { }
        return null;
    }

    static CharSequence titleOf(Object info) {
        try { Object t = XposedHelpers.getObjectField(info, "title"); if (t instanceof CharSequence) return (CharSequence) t; } catch (Throwable ignored) { }
        return null;
    }

    /** Stable key: component flattened + "#serial" for non-primary users; null when the item has no component. */
    static String keyOf(Context ctx, Object info) {
        ComponentName cn = componentOf(info);
        if (cn == null) return null;
        long serial = 0;
        UserHandle u = userOf(info);
        if (u != null) {
            Long s = serials.get(u);
            if (s == null) {
                try { UserManager um = (UserManager) ctx.getSystemService(Context.USER_SERVICE); s = um == null ? 0L : um.getSerialNumberForUser(u); }
                catch (Throwable t) { s = (long) u.hashCode(); }
                serials.put(u, s);
            }
            serial = s;
        }
        return serial == 0 ? cn.flattenToString() : cn.flattenToString() + "#" + serial;
    }

    /** The launcher-rendered icon bitmap of an AppInfo (BitmapInfo.icon), or null. */
    static Bitmap iconOf(Object info) {
        if (info == null) return null;
        try {
            if (infoClass != null && infoClass.isInstance(info) && bitmapInfoField != null && bitmapField != null) {
                Object bi = bitmapInfoField.get(info);
                Object b = bi == null ? null : bitmapField.get(bi);
                return b instanceof Bitmap ? (Bitmap) b : null;
            }
            for (Class<?> k = info.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType() == String.class || CharSequence.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object bi = f.get(info);
                    if (bi == null || bi instanceof Bitmap) continue;
                    Field bf = bitmapFieldOf(bi.getClass());
                    if (bf != null) {
                        Object b = bf.get(bi);
                        if (b instanceof Bitmap) {
                            infoClass = k; bitmapInfoField = f; bitmapField = bf;
                            if (!loggedIcon) { loggedIcon = true; Lx.i("Drawer: icon bitmap via " + k.getSimpleName() + "." + f.getName() + " (" + bi.getClass().getName() + ")." + bf.getName()); }
                            return (Bitmap) b;
                        }
                    }
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static Field bitmapFieldOf(Class<?> c) {
        if (c.getName().startsWith("java.") || c.getName().startsWith("android.")) return null;
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == Bitmap.class) { f.setAccessible(true); return f; }
            }
        }
        return null;
    }

    /** Start an app the way the launcher would (LauncherApps handles work/private profiles). */
    static boolean launch(Context ctx, Object info, Rect sourceBounds) {
        ComponentName cn = componentOf(info);
        UserHandle u = userOf(info);
        try {
            LauncherApps la = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (la != null && cn != null) {
                la.startMainActivity(cn, u != null ? u : android.os.Process.myUserHandle(), sourceBounds, null);
                return true;
            }
        } catch (Throwable t) { Lx.w("Drawer: LauncherApps.startMainActivity failed: " + t); }
        try {
            Object it = XposedHelpers.callMethod(info, "getIntent");
            if (it instanceof Intent) {
                Intent i = new Intent((Intent) it);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (sourceBounds != null) i.setSourceBounds(sourceBounds);
                ctx.startActivity(i);
                return true;
            }
        } catch (Throwable t) { Lx.e("Drawer: launch via intent", t); }
        return false;
    }
}
