package com.mbazos.jclaude.json;

import java.util.List;
import java.util.Map;

/**
 * Standalone test class for JsonParser and JsonPath.
 * No external test framework required — run with:
 *   java -cp target/test-classes:target/classes com.mbazos.jclaude.json.JsonParserTest
 */
public class JsonParserTest {

    public static void main(String[] args) {
        testEmptyObject();
        testEmptyArray();
        testNestedObject();
        testUnicodeEscape();
        testScientificNotation();
        testLongInteger();
        testBooleanAndNull();
        testStatsCacheStructure();
        testJsonPathNavigation();

        System.out.println("All tests passed.");
    }

    // -------------------------------------------------------------------------
    // Test cases
    // -------------------------------------------------------------------------

    private static void testEmptyObject() {
        Object result = JsonParser.parse("{}");
        assertNotNull(result, "emptyObject: result not null");
        assertEqual(true, result instanceof Map, "emptyObject: is Map");
        assertEqual(0, ((Map<?, ?>) result).size(), "emptyObject: size is 0");
    }

    private static void testEmptyArray() {
        Object result = JsonParser.parse("[]");
        assertNotNull(result, "emptyArray: result not null");
        assertEqual(true, result instanceof List, "emptyArray: is List");
        assertEqual(0, ((List<?>) result).size(), "emptyArray: size is 0");
    }

    private static void testNestedObject() {
        Object result = JsonParser.parse("{\"a\":{\"b\":42}}");
        assertEqual(true, result instanceof Map, "nestedObject: is Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> outer = (Map<String, Object>) result;
        assertEqual(true, outer.get("a") instanceof Map, "nestedObject: 'a' is Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) outer.get("a");
        assertEqual(42L, inner.get("b"), "nestedObject: 'b' == 42L");
    }

    private static void testUnicodeEscape() {
        // café -> café
        Object result = JsonParser.parse("{\"emoji\":\"caf\\u00e9\"}");
        assertEqual(true, result instanceof Map, "unicodeEscape: is Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEqual("café", map.get("emoji"), "unicodeEscape: value is 'café'");
    }

    private static void testScientificNotation() {
        Object result = JsonParser.parse("{\"val\":1.5e10}");
        assertEqual(true, result instanceof Map, "scientificNotation: is Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        Object val = map.get("val");
        assertEqual(true, val instanceof Double, "scientificNotation: val is Double");
        assertEqual(1.5e10, val, "scientificNotation: val == 1.5e10");
    }

    private static void testLongInteger() {
        Object result = JsonParser.parse("{\"n\":12345678901234}");
        assertEqual(true, result instanceof Map, "longInteger: is Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEqual(12345678901234L, map.get("n"), "longInteger: n == 12345678901234L");
    }

    private static void testBooleanAndNull() {
        Object result = JsonParser.parse("{\"ok\":true,\"missing\":null}");
        assertEqual(true, result instanceof Map, "booleanAndNull: is Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEqual(Boolean.TRUE, map.get("ok"), "booleanAndNull: ok == true");
        assertEqual(true, map.containsKey("missing"), "booleanAndNull: 'missing' key exists");
        assertNull(map.get("missing"), "booleanAndNull: missing == null");
    }

    private static void testStatsCacheStructure() {
        String json = "{\"dailyActivity\":[{\"date\":\"2026-05-27\",\"messageCount\":127,"
                + "\"sessionCount\":3,\"toolCallCount\":45}],\"totalSessions\":479}";
        Object result = JsonParser.parse(json);
        assertEqual(true, result instanceof Map, "statsCache: is Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) result;

        // dailyActivity is a List
        Object daily = root.get("dailyActivity");
        assertEqual(true, daily instanceof List, "statsCache: dailyActivity is List");
        List<?> dailyList = (List<?>) daily;
        assertEqual(1, dailyList.size(), "statsCache: dailyActivity has 1 entry");

        // first entry is a Map with expected fields
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) dailyList.get(0);
        assertEqual("2026-05-27", entry.get("date"), "statsCache: date");
        assertEqual(127L, entry.get("messageCount"), "statsCache: messageCount");
        assertEqual(3L, entry.get("sessionCount"), "statsCache: sessionCount");
        assertEqual(45L, entry.get("toolCallCount"), "statsCache: toolCallCount");

        // totalSessions at root level
        assertEqual(479L, root.get("totalSessions"), "statsCache: totalSessions");
    }

    private static void testJsonPathNavigation() {
        String json = "{\"dailyActivity\":[{\"date\":\"2026-05-27\",\"messageCount\":127,"
                + "\"sessionCount\":3,\"toolCallCount\":45}],\"totalSessions\":479}";
        Object root = JsonParser.parse(json);

        // getString
        String date = JsonPath.getString(root, "dailyActivity", "0", "date");
        assertEqual("2026-05-27", date, "jsonPath: getString dailyActivity[0].date");

        // getLong — messageCount
        Long msgCount = JsonPath.getLong(root, "dailyActivity", "0", "messageCount");
        assertEqual(127L, msgCount, "jsonPath: getLong dailyActivity[0].messageCount");

        // getLong — totalSessions
        Long totalSessions = JsonPath.getLong(root, "totalSessions");
        assertEqual(479L, totalSessions, "jsonPath: getLong totalSessions");

        // getList — dailyActivity
        List<Object> list = JsonPath.getList(root, "dailyActivity");
        assertNotNull(list, "jsonPath: getList dailyActivity not null");
        assertEqual(1, list.size(), "jsonPath: getList dailyActivity size");

        // getMap — first entry
        Map<String, Object> entry = JsonPath.getMap(root, "dailyActivity", "0");
        assertNotNull(entry, "jsonPath: getMap dailyActivity[0] not null");
        assertEqual("2026-05-27", entry.get("date"), "jsonPath: getMap entry date");

        // missing path returns null
        String missing = JsonPath.getString(root, "nonexistent", "path");
        assertNull(missing, "jsonPath: missing path returns null");
    }

    // -------------------------------------------------------------------------
    // Assertion helpers
    // -------------------------------------------------------------------------

    private static void assertEqual(Object expected, Object actual, String testName) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "FAIL [" + testName + "]: expected " + expected + " but got " + actual);
        }
        System.out.println("PASS: " + testName);
    }

    private static void assertNotNull(Object actual, String testName) {
        if (actual == null) {
            throw new AssertionError("FAIL [" + testName + "]: expected non-null but got null");
        }
        System.out.println("PASS: " + testName);
    }

    private static void assertNull(Object actual, String testName) {
        if (actual != null) {
            throw new AssertionError(
                    "FAIL [" + testName + "]: expected null but got " + actual);
        }
        System.out.println("PASS: " + testName);
    }
}
