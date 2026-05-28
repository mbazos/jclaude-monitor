package com.mbazos.jclaude.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal recursive-descent JSON parser.
 * Returns one of: Map<String,Object>, List<Object>, String, Long, Double, Boolean, or null.
 */
public class JsonParser {

    private final String input;
    private int pos;

    private JsonParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public static Object parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON input must not be null");
        }
        JsonParser parser = new JsonParser(json);
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos != json.length()) {
            throw new IllegalArgumentException(
                    "Unexpected trailing content at position " + parser.pos);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Recursive-descent parse methods
    // -------------------------------------------------------------------------

    private Object parseValue() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw new IllegalArgumentException("Unexpected end of input at position " + pos);
        }
        char ch = input.charAt(pos);
        return switch (ch) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f', 'n' -> parseLiteral();
            default -> {
                if (ch == '-' || Character.isDigit(ch)) {
                    yield parseNumber();
                }
                throw new IllegalArgumentException(
                        "Unexpected character '" + ch + "' at position " + pos);
            }
        };
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unterminated object at position " + pos);
            }
            char next = input.charAt(pos);
            if (next == '}') {
                pos++;
                return map;
            }
            if (next != ',') {
                throw new IllegalArgumentException(
                        "Expected ',' or '}' in object at position " + pos + ", got '" + next + "'");
            }
            pos++; // consume ','
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skipWhitespace();
        if (pos < input.length() && input.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unterminated array at position " + pos);
            }
            char next = input.charAt(pos);
            if (next == ']') {
                pos++;
                return list;
            }
            if (next != ',') {
                throw new IllegalArgumentException(
                        "Expected ',' or ']' in array at position " + pos + ", got '" + next + "'");
            }
            pos++; // consume ','
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char ch = input.charAt(pos++);
            if (ch == '"') {
                return sb.toString();
            }
            if (ch == '\\') {
                if (pos >= input.length()) {
                    throw new IllegalArgumentException("Unterminated escape sequence at position " + pos);
                }
                char esc = input.charAt(pos++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > input.length()) {
                            throw new IllegalArgumentException(
                                    "Incomplete \\uXXXX escape at position " + (pos - 2));
                        }
                        String hex = input.substring(pos, pos + 4);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException(
                                    "Invalid \\uXXXX escape '\\u" + hex + "' at position " + (pos - 2));
                        }
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException(
                            "Unknown escape sequence '\\" + esc + "' at position " + (pos - 2));
                }
            } else {
                sb.append(ch);
            }
        }
        throw new IllegalArgumentException("Unterminated string starting near position " + pos);
    }

    private Object parseNumber() {
        int start = pos;
        boolean isDecimal = false;

        if (pos < input.length() && input.charAt(pos) == '-') {
            pos++;
        }
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos < input.length() && input.charAt(pos) == '.') {
            isDecimal = true;
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            isDecimal = true;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }

        String raw = input.substring(start, pos);
        if (raw.isEmpty() || raw.equals("-")) {
            throw new IllegalArgumentException("Invalid number at position " + start);
        }
        try {
            if (isDecimal) {
                return Double.parseDouble(raw);
            } else {
                return Long.parseLong(raw);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number '" + raw + "' at position " + start);
        }
    }

    private Object parseLiteral() {
        if (input.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (input.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (input.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown literal at position " + pos + ": '" + input.substring(pos, Math.min(pos + 10, input.length())) + "'");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void skipWhitespace() {
        while (pos < input.length()) {
            char ch = input.charAt(pos);
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private void expect(char expected) {
        if (pos >= input.length()) {
            throw new IllegalArgumentException(
                    "Expected '" + expected + "' but reached end of input at position " + pos);
        }
        char actual = input.charAt(pos);
        if (actual != expected) {
            throw new IllegalArgumentException(
                    "Expected '" + expected + "' but found '" + actual + "' at position " + pos);
        }
        pos++;
    }
}
