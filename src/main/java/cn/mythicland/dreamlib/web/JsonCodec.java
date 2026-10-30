package cn.mythicland.dreamlib.web;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.*;

/**
 * Small dependency-free JSON codec for Lib's embedded web services.
 *
 * <p>The codec intentionally produces ordinary Java trees: objects are maps, arrays are lists,
 * JSON numbers are {@link Long} or {@link Double}, and JSON null is {@code null}. This keeps the
 * web layer independent from any plugin domain model.</p>
 */
public final class JsonCodec {

    private JsonCodec() {
    }

    /**
     * Parses any JSON value.
     *
     * @param source JSON text
     * @return parsed Java tree
     */
    public static Object parse(String source) {
        Objects.requireNonNull(source, "source");
        return new Parser(source).parse();
    }

    /**
     * Parses a JSON object.
     *
     * @param source JSON object text
     * @return parsed object
     */
    public static Map<String, Object> parseObject(String source) {
        Object value = parse(source);
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("JSON root must be an object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    /**
     * Serializes a JSON-compatible value. Maps, collections, arrays, records, primitives, enums,
     * and null are supported.
     *
     * @param value value to serialize
     * @return JSON text
     */
    public static String stringify(Object value) {
        StringBuilder output = new StringBuilder();
        write(value, output);
        return output.toString();
    }

    private static void write(Object value, StringBuilder output) {
        if (value == null) {
            output.append("null");
            return;
        }
        if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            writeString(value instanceof Enum<?> enumeration ? enumeration.name() : String.valueOf(value), output);
            return;
        }
        if (value instanceof Boolean || value instanceof Number) {
            if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
                throw new IllegalArgumentException("JSON numbers must be finite");
            }
            if (value instanceof Float floatValue && !Float.isFinite(floatValue)) {
                throw new IllegalArgumentException("JSON numbers must be finite");
            }
            output.append(value);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) output.append(',');
                first = false;
                writeString(String.valueOf(entry.getKey()), output);
                output.append(':');
                write(entry.getValue(), output);
            }
            output.append('}');
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            output.append('[');
            boolean first = true;
            for (Object entry : iterable) {
                if (!first) output.append(',');
                first = false;
                write(entry, output);
            }
            output.append(']');
            return;
        }
        if (value.getClass().isArray()) {
            output.append('[');
            for (int index = 0; index < Array.getLength(value); index++) {
                if (index > 0) output.append(',');
                write(Array.get(value, index), output);
            }
            output.append(']');
            return;
        }
        if (value.getClass().isRecord()) {
            Map<String, Object> components = new LinkedHashMap<>();
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                try {
                    components.put(component.getName(), component.getAccessor().invoke(value));
                } catch (IllegalAccessException | InvocationTargetException exception) {
                    throw new IllegalArgumentException("Cannot serialize record component "
                            + component.getName(), exception);
                }
            }
            write(components, output);
            return;
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    private static void writeString(String value, StringBuilder output) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) output.append(String.format("\\u%04x", (int) character));
                    else output.append(character);
                }
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source;
        }

        private static boolean isDigitOneToNine(char character) {
            return character >= '1' && character <= '9';
        }

        private Object parse() {
            skipWhitespace();
            Object value = readValue();
            skipWhitespace();
            if (index != source.length()) throw error("Unexpected trailing characters");
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (index >= source.length()) throw error("Unexpected end of JSON");
            return switch (source.charAt(index)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> values = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return values;
            while (true) {
                skipWhitespace();
                if (index >= source.length() || source.charAt(index) != '"') {
                    throw error("Object keys must be strings");
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                values.put(key, readValue());
                skipWhitespace();
                if (consume('}')) return values;
                expect(',');
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> values = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return values;
            while (true) {
                values.add(readValue());
                skipWhitespace();
                if (consume(']')) return values;
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < source.length()) {
                char character = source.charAt(index++);
                if (character == '"') return value.toString();
                if (character < 0x20) throw error("Control characters are not allowed in JSON strings");
                if (character != '\\') {
                    value.append(character);
                    continue;
                }
                if (index >= source.length()) throw error("Unterminated escape sequence");
                char escape = source.charAt(index++);
                switch (escape) {
                    case '"', '\\', '/' -> value.append(escape);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(readUnicodeEscape());
                    default -> throw error("Unknown escape sequence: \\" + escape);
                }
            }
            throw error("Unterminated JSON string");
        }

        private char readUnicodeEscape() {
            if (index + 4 > source.length()) throw error("Incomplete unicode escape");
            String hex = source.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                throw error("Invalid unicode escape");
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!source.startsWith(literal, index)) throw error("Invalid literal");
            index += literal.length();
            return value;
        }

        private Number readNumber() {
            int start = index;
            if (consume('-')) {
                if (index >= source.length()) throw error("Invalid number");
            }
            if (consume('0')) {
                if (index < source.length() && Character.isDigit(source.charAt(index))) {
                    throw error("Numbers cannot contain leading zeroes");
                }
            } else {
                if (index >= source.length() || !isDigitOneToNine(source.charAt(index))) {
                    throw error("Invalid number");
                }
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            }
            boolean decimal = false;
            if (consume('.')) {
                decimal = true;
                if (index >= source.length() || !Character.isDigit(source.charAt(index))) {
                    throw error("Invalid decimal number");
                }
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            }
            if (index < source.length() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (index < source.length() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
                if (index >= source.length() || !Character.isDigit(source.charAt(index))) {
                    throw error("Invalid exponent");
                }
                while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            }
            String number = source.substring(start, index);
            try {
                if (decimal) return Double.valueOf(number);
                return Long.valueOf(number);
            } catch (NumberFormatException exception) {
                throw error("Invalid number");
            }
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private void expect(char expected) {
            if (!consume(expected)) throw error("Expected '" + expected + "'");
        }

        private boolean consume(char expected) {
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + index);
        }
    }
}
