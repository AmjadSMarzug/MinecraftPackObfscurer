package com.packobfscurer.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer with no dependencies.
 *
 * Objects keep their key order, and numbers keep their original text so that
 * "1.0" never turns into "1" when a pack is minified.
 *
 * Values: {@link Map} (objects), {@link List} (arrays), {@link String},
 * {@link Number} wrapped as {@link Raw}, {@link Boolean} and null.
 */
public final class Json {

    /** A number kept as the exact text from the source file. */
    public record Raw(String text) {
        @Override
        public String toString() {
            return text;
        }
    }

    /** Thrown when the input is not valid JSON. The message includes line and column. */
    public static final class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }

    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipWs();
        Object value = p.readValue();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw p.error("unexpected trailing content");
        }
        return value;
    }

    /** Writes compact JSON, or indented JSON when pretty is true. */
    public static String write(Object value, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, pretty, 0);
        if (pretty) {
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- reading

    private Object readValue() {
        if (pos >= src.length()) {
            throw error("unexpected end of input");
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return readNumber();
                }
                throw error("unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            if (peek() != '"') {
                throw error("expected a string key");
            }
            String key = readString();
            skipWs();
            if (peek() != ':') {
                throw error("expected ':'");
            }
            pos++;
            skipWs();
            map.put(key, readValue());
            skipWs();
            char c = peek();
            pos++;
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                pos--;
                throw error("expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(readValue());
            skipWs();
            char c = peek();
            pos++;
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                pos--;
                throw error("expected ',' or ']'");
            }
        }
    }

    private String readString() {
        pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw error("unterminated string");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= src.length()) {
                throw error("unterminated escape");
            }
            char e = src.charAt(pos++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (pos + 4 > src.length()) {
                        throw error("bad unicode escape");
                    }
                    try {
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("bad unicode escape");
                    }
                    pos += 4;
                    break;
                default:
                    pos--;
                    throw error("bad escape '\\" + e + "'");
            }
        }
    }

    private Raw readNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                pos++;
            } else {
                break;
            }
        }
        String text = src.substring(start, pos);
        if (text.isEmpty() || text.equals("-")) {
            throw error("bad number");
        }
        return new Raw(text);
    }

    private void expect(String word) {
        if (!src.startsWith(word, pos)) {
            throw error("expected '" + word + "'");
        }
        pos += word.length();
    }

    private char peek() {
        if (pos >= src.length()) {
            throw error("unexpected end of input");
        }
        return src.charAt(pos);
    }

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '﻿') {
                pos++;
            } else {
                break;
            }
        }
    }

    private ParseException error(String message) {
        int line = 1;
        int col = 1;
        for (int i = 0; i < Math.min(pos, src.length()); i++) {
            if (src.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new ParseException(message + " at line " + line + ", column " + col);
    }

    /** Returns a copy with every object's keys in a random order. Array order is kept, since it can matter. */
    public static Object shuffleKeys(Object value, java.util.Random rnd) {
        if (value instanceof Map<?, ?> map) {
            List<Object> keys = new ArrayList<>(map.keySet());
            java.util.Collections.shuffle(keys, rnd);
            Map<String, Object> out = new LinkedHashMap<>();
            for (Object k : keys) {
                out.put(String.valueOf(k), shuffleKeys(map.get(k), rnd));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(shuffleKeys(o, rnd));
            }
            return out;
        }
        return value;
    }

    // ---------------------------------------------------------------- writing

    private static void writeValue(StringBuilder sb, Object v, boolean pretty, int depth) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Raw r) {
            sb.append(r.text());
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            writeObject(sb, m, pretty, depth);
        } else if (v instanceof List<?> l) {
            writeArray(sb, l, pretty, depth);
        } else {
            throw new IllegalArgumentException("cannot write " + v.getClass());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> m, boolean pretty, int depth) {
        if (m.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            indent(sb, pretty, depth + 1);
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(pretty ? ": " : ":");
            writeValue(sb, e.getValue(), pretty, depth + 1);
        }
        indent(sb, pretty, depth);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> l, boolean pretty, int depth) {
        if (l.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        boolean first = true;
        for (Object o : l) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            indent(sb, pretty, depth + 1);
            writeValue(sb, o, pretty, depth + 1);
        }
        indent(sb, pretty, depth);
        sb.append(']');
    }

    private static void indent(StringBuilder sb, boolean pretty, int depth) {
        if (pretty) {
            sb.append('\n');
            sb.append("  ".repeat(depth));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
