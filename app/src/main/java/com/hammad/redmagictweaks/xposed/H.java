package com.hammad.redmagictweaks.xposed;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Small, defensive hook helpers: nothing here throws; every miss is logged and skipped. */
public final class H {
    private H() {}

    public static Class<?> cls(ClassLoader cl, String name) {
        Class<?> c = XposedHelpers.findClassIfExists(name, cl);
        if (c == null) Lx.w("class not found: " + name);
        return c;
    }

    /** Find a method by name (any declaring class up the chain) and exact parameter types. */
    public static Method find(Class<?> c, String name, Class<?>... params) {
        if (c == null) return null;
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    /** All declared methods with this name (any params). */
    public static List<Method> findAll(Class<?> c, String name) {
        List<Method> out = new ArrayList<>();
        if (c == null) return out;
        for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name)) { m.setAccessible(true); out.add(m); }
        return out;
    }

    /**
     * Obfuscation fallback: the unique declared method matching return type + parameter types.
     * Returns null (and logs the candidates) when zero or several match.
     */
    public static Method findBySig(Class<?> c, Class<?> ret, Class<?>... params) {
        if (c == null) return null;
        List<Method> hits = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge()) continue;
            if (ret != null && m.getReturnType() != ret) continue;
            if (!Arrays.equals(m.getParameterTypes(), params)) continue;
            hits.add(m);
        }
        if (hits.size() == 1) { hits.get(0).setAccessible(true); return hits.get(0); }
        Lx.w("signature match for " + c.getName() + " " + (ret == null ? "?" : ret.getSimpleName()) + Arrays.toString(params) + " -> " + hits.size() + " candidates " + names(hits));
        return null;
    }

    /** Name first, then signature fallback. */
    public static Method resolve(Class<?> c, String name, Class<?> ret, Class<?>... params) {
        Method m = find(c, name, params);
        if (m == null) m = findBySig(c, ret, params);
        if (m == null) Lx.w("method not found: " + (c == null ? "?" : c.getName()) + "#" + name + Arrays.toString(params));
        return m;
    }

    /** Try several names (real name first, then known obfuscated names), then the signature fallback. */
    public static Method resolveAny(Class<?> c, Class<?> ret, Class<?>[] params, String... names) {
        if (c == null) return null;
        for (String n : names) { Method m = find(c, n, params); if (m != null) return m; }
        Method m = findBySig(c, ret, params);
        if (m == null) Lx.w("method not found: " + c.getName() + "#" + Arrays.toString(names) + Arrays.toString(params));
        return m;
    }

    public static boolean hook(Method m, XC_MethodHook cb) {
        if (m == null) return false;
        try { XposedBridge.hookMethod(m, cb); return true; }
        catch (Throwable t) { Lx.e("hook failed: " + m, t); return false; }
    }

    public static boolean hook(Class<?> c, String name, Class<?> ret, Class<?>[] params, XC_MethodHook cb) {
        return hook(resolve(c, name, ret, params), cb);
    }

    /** Hook every overload with this name. Returns how many were hooked. */
    public static int hookAll(Class<?> c, String name, XC_MethodHook cb) {
        int n = 0;
        for (Method m : findAll(c, name)) if (hook(m, cb)) n++;
        if (n == 0) Lx.w("no overload hooked: " + (c == null ? "?" : c.getName()) + "#" + name);
        return n;
    }

    /** Replace with a constant, guarded by a runtime check so the switch can be toggled without restart. */
    public static boolean constant(Class<?> c, String name, Class<?> ret, Class<?>[] params, final Object value, final java.util.function.BooleanSupplier when) {
        return hook(c, name, ret, params, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (when.getAsBoolean()) p.setResult(value);
            }
        });
    }

    public static XC_MethodReplacement returning(Object v) { return XC_MethodReplacement.returnConstant(v); }

    /** True when the current call stack contains cls#method (method may be null for any method). */
    public static boolean calledFrom(String cls, String method) {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if (e.getClassName().equals(cls) && (method == null || e.getMethodName().equals(method))) return true;
        }
        return false;
    }

    public static boolean calledFromPrefix(String classPrefix) {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if (e.getClassName().startsWith(classPrefix)) return true;
        }
        return false;
    }

    public static Class<?>[] types(Class<?>... t) { return t; }

    static String names(List<Method> ms) {
        StringBuilder b = new StringBuilder("[");
        for (Method m : ms) b.append(Modifier.isStatic(m.getModifiers()) ? "static " : "").append(m.getName()).append(' ');
        return b.append(']').toString();
    }
}
