package com.mbazos.jclaude.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonParserTest {

    @Test
    void emptyObject() {
        Object result = JsonParser.parse("{}");
        Map<?, ?> map = assertInstanceOf(Map.class, result);
        assertEquals(0, map.size());
    }

    @Test
    void emptyArray() {
        Object result = JsonParser.parse("[]");
        List<?> list = assertInstanceOf(List.class, result);
        assertEquals(0, list.size());
    }

    @Test
    void nestedObject() {
        Object result = JsonParser.parse("{\"a\":{\"b\":42}}");
        Map<?, ?> outer = assertInstanceOf(Map.class, result);
        Map<?, ?> inner = assertInstanceOf(Map.class, outer.get("a"));
        assertEquals(42L, inner.get("b"));
    }

    @Test
    void unicodeEscape() {
        // café -> café
        Object result = JsonParser.parse("{\"emoji\":\"caf\\u00e9\"}");
        Map<?, ?> map = assertInstanceOf(Map.class, result);
        assertEquals("café", map.get("emoji"));
    }

    @Test
    void scientificNotation() {
        Object result = JsonParser.parse("{\"val\":1.5e10}");
        Map<?, ?> map = assertInstanceOf(Map.class, result);
        Double val = assertInstanceOf(Double.class, map.get("val"));
        assertEquals(1.5e10, val);
    }

    @Test
    void longInteger() {
        Object result = JsonParser.parse("{\"n\":12345678901234}");
        Map<?, ?> map = assertInstanceOf(Map.class, result);
        assertEquals(12345678901234L, map.get("n"));
    }

    @Test
    void booleanAndNull() {
        Object result = JsonParser.parse("{\"ok\":true,\"missing\":null}");
        Map<?, ?> map = assertInstanceOf(Map.class, result);
        assertEquals(Boolean.TRUE, map.get("ok"));
        assertTrue(map.containsKey("missing"));
        assertNull(map.get("missing"));
    }

    @Test
    void statsCacheStructure() {
        String json = "{\"dailyActivity\":[{\"date\":\"2026-05-27\",\"messageCount\":127,"
                + "\"sessionCount\":3,\"toolCallCount\":45}],\"totalSessions\":479}";
        Object result = JsonParser.parse(json);
        Map<?, ?> root = assertInstanceOf(Map.class, result);

        List<?> daily = assertInstanceOf(List.class, root.get("dailyActivity"));
        assertEquals(1, daily.size());

        Map<?, ?> entry = assertInstanceOf(Map.class, daily.get(0));
        assertEquals("2026-05-27", entry.get("date"));
        assertEquals(127L, entry.get("messageCount"));
        assertEquals(3L, entry.get("sessionCount"));
        assertEquals(45L, entry.get("toolCallCount"));

        assertEquals(479L, root.get("totalSessions"));
    }

    @Test
    void jsonPathNavigation() {
        String json = "{\"dailyActivity\":[{\"date\":\"2026-05-27\",\"messageCount\":127,"
                + "\"sessionCount\":3,\"toolCallCount\":45}],\"totalSessions\":479}";
        Object root = JsonParser.parse(json);

        assertEquals("2026-05-27", JsonPath.getString(root, "dailyActivity", "0", "date"));
        assertEquals(127L, JsonPath.getLong(root, "dailyActivity", "0", "messageCount"));
        assertEquals(479L, JsonPath.getLong(root, "totalSessions"));

        List<Object> list = JsonPath.getList(root, "dailyActivity");
        assertNotNull(list);
        assertEquals(1, list.size());

        Map<String, Object> entry = JsonPath.getMap(root, "dailyActivity", "0");
        assertNotNull(entry);
        assertEquals("2026-05-27", entry.get("date"));

        assertNull(JsonPath.getString(root, "nonexistent", "path"));
    }
}
