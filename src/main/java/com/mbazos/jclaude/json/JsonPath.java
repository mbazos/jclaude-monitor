package com.mbazos.jclaude.json;

import java.util.List;
import java.util.Map;

/**
 * Static helpers for navigating a parsed JSON tree (Map/List/primitives)
 * without verbose casting. All methods return null if the path doesn't exist
 * or the value is the wrong type — they never throw for missing paths.
 */
public final class JsonPath {

    private JsonPath() {}

    /**
     * Navigate a nested tree using a sequence of keys (for Maps) or
     * integer-string indices (for Lists, e.g. "0", "1").
     * Returns null if any step along the path is missing or incompatible.
     */
    public static Object get(Object root, String... keys) {
        Object current = root;
        for (String key : keys) {
            if (current == null) {
                return null;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(key);
            } else if (current instanceof List<?> list) {
                try {
                    int index = Integer.parseInt(key);
                    if (index < 0 || index >= list.size()) {
                        return null;
                    }
                    current = list.get(index);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    /** Returns the value at the given path as a String, or null if missing/wrong type. */
    public static String getString(Object root, String... keys) {
        Object val = get(root, keys);
        return val instanceof String s ? s : null;
    }

    /** Returns the value at the given path as a Long, or null if missing/wrong type. */
    public static Long getLong(Object root, String... keys) {
        Object val = get(root, keys);
        if (val instanceof Long l) return l;
        if (val instanceof Integer i) return i.longValue();
        return null;
    }

    /** Returns the value at the given path as a Double, or null if missing/wrong type. */
    public static Double getDouble(Object root, String... keys) {
        Object val = get(root, keys);
        if (val instanceof Double d) return d;
        if (val instanceof Long l) return l.doubleValue();
        if (val instanceof Float f) return f.doubleValue();
        return null;
    }

    /** Returns the value at the given path as a Boolean, or null if missing/wrong type. */
    public static Boolean getBoolean(Object root, String... keys) {
        Object val = get(root, keys);
        return val instanceof Boolean b ? b : null;
    }

    /** Returns the value at the given path as a List, or null if missing/wrong type. */
    @SuppressWarnings("unchecked")
    public static List<Object> getList(Object root, String... keys) {
        Object val = get(root, keys);
        return val instanceof List<?> ? (List<Object>) val : null;
    }

    /** Returns the value at the given path as a Map, or null if missing/wrong type. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMap(Object root, String... keys) {
        Object val = get(root, keys);
        return val instanceof Map<?, ?> ? (Map<String, Object>) val : null;
    }
}
