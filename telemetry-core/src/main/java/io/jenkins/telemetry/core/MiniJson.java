package io.jenkins.telemetry.core;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MiniJson {
    private MiniJson() {
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, value);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        return (Map<String, Object>) map;
    }

    public static Object parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON must not be null");
        }
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("Trailing content");
        }
        return value;
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            writeString(out, string);
        } else if (value instanceof Character character) {
            writeString(out, String.valueOf(character));
        } else if (value instanceof Boolean || value instanceof Byte || value instanceof Short ||
                   value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof Float number) {
            if (Float.isFinite(number)) out.append(number); else out.append("null");
        } else if (value instanceof Double number) {
            if (Double.isFinite(number)) out.append(number); else out.append("null");
        } else if (value instanceof Number number) {
            out.append(number);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) out.append(',');
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else if (value.getClass().isArray()) {
            out.append('[');
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) out.append(',');
                writeValue(out, Array.get(value, i));
            }
            out.append(']');
        } else if (value instanceof Enum<?> enumeration) {
            writeString(out, enumeration.name());
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input;
        }

        private Object parseValue() {
            skipWhitespace();
            if (atEnd()) throw error("Unexpected end of input");
            return switch (input.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char ch = input.charAt(index++);
                if (ch == '"') return result.toString();
                if (ch != '\\') {
                    result.append(ch);
                    continue;
                }
                if (atEnd()) throw error("Unfinished escape");
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (index + 4 > input.length()) throw error("Invalid unicode escape");
                        String hex = input.substring(index, index + 4);
                        try {
                            result.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw error("Invalid unicode escape");
                        }
                        index += 4;
                    }
                    default -> throw error("Invalid escape: " + escaped);
                }
            }
            throw error("Unterminated string");
        }

        private Object parseLiteral(String literal, Object value) {
            if (!input.startsWith(literal, index)) throw error("Expected " + literal);
            index += literal.length();
            return value;
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) index++;
            while (!atEnd() && Character.isDigit(input.charAt(index))) index++;
            boolean decimal = false;
            if (!atEnd() && input.charAt(index) == '.') {
                decimal = true;
                index++;
                while (!atEnd() && Character.isDigit(input.charAt(index))) index++;
            }
            if (!atEnd() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (!atEnd() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
                while (!atEnd() && Character.isDigit(input.charAt(index))) index++;
            }
            if (start == index) throw error("Expected value");
            String text = input.substring(start, index);
            try {
                return decimal ? Double.parseDouble(text) : Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw error("Invalid number: " + text);
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (atEnd() || input.charAt(index) != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char value) {
            return !atEnd() && input.charAt(index) == value;
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char ch = input.charAt(index);
                if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') index++;
                else return;
            }
        }

        private boolean atEnd() {
            return index >= input.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + index);
        }
    }
}
