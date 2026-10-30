package cn.mythicland.dreamlib.web;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bounded multipart/form-data parser for small administrative uploads.
 */
public final class MultipartParser {

    private MultipartParser() {
    }

    /**
     * Parses a multipart body.
     *
     * @param contentType request Content-Type
     * @param body        body bytes
     * @return parsed form
     */
    public static MultipartData parse(String contentType, byte[] body) {
        Objects.requireNonNull(body, "body");
        String boundary = boundary(contentType);
        byte[] marker = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<Part> parts = new ArrayList<>();
        int position = indexOf(body, marker, 0);
        if (position < 0) throw new WebException(400, "INVALID_MULTIPART", "Multipart 边界无效");

        while (position >= 0) {
            int cursor = position + marker.length;
            if (cursor + 1 < body.length && body[cursor] == '-' && body[cursor + 1] == '-') break;
            if (cursor + 1 >= body.length || body[cursor] != '\r' || body[cursor + 1] != '\n') {
                throw new WebException(400, "INVALID_MULTIPART", "Multipart 分隔符无效");
            }
            cursor += 2;
            int headerEnd = indexOf(body, new byte[]{'\r', '\n', '\r', '\n'}, cursor);
            if (headerEnd < 0) throw new WebException(400, "INVALID_MULTIPART", "Multipart 请求头不完整");
            Map<String, String> headers = parseHeaders(body, cursor, headerEnd);
            int contentStart = headerEnd + 4;
            int nextBoundary = indexOf(body, concat(new byte[]{'\r', '\n'}, marker), contentStart);
            if (nextBoundary < 0) throw new WebException(400, "INVALID_MULTIPART", "Multipart 内容不完整");
            byte[] content = Arrays.copyOfRange(body, contentStart, nextBoundary);
            String disposition = headers.get("content-disposition");
            String name = parameter(disposition, "name");
            if (name == null || name.isBlank()) {
                throw new WebException(400, "INVALID_MULTIPART", "Multipart 字段名缺失");
            }
            parts.add(new Part(
                    name,
                    fileName(disposition),
                    headers.getOrDefault("content-type", "application/octet-stream"),
                    content
            ));
            position = nextBoundary + 2;
        }
        return new MultipartData(parts);
    }

    private static String boundary(String contentType) {
        if (contentType == null) throw new WebException(400, "INVALID_MULTIPART", "缺少 multipart Content-Type");
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.regionMatches(true, 0, "boundary=", 0, 9)) continue;
            String value = trimmed.substring(9).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isBlank()) return value;
        }
        throw new WebException(400, "INVALID_MULTIPART", "缺少 multipart boundary");
    }

    private static Map<String, String> parseHeaders(byte[] body, int start, int end) {
        String raw = decodeHeader(body, start, end);
        Map<String, String> headers = new LinkedHashMap<>();
        for (String line : raw.split("\\r\\n")) {
            int separator = line.indexOf(':');
            if (separator <= 0) throw new WebException(400, "INVALID_MULTIPART", "Multipart 请求头无效");
            headers.put(line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                    line.substring(separator + 1).trim());
        }
        return headers;
    }

    private static String decodeHeader(byte[] body, int start, int end) {
        byte[] bytes = Arrays.copyOfRange(body, start, end);
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        return utf8.indexOf('\ufffd') >= 0
                ? new String(bytes, StandardCharsets.ISO_8859_1)
                : utf8;
    }

    private static String fileName(String disposition) {
        String extended = parameter(disposition, "filename*");
        return extended == null ? parameter(disposition, "filename") : decodeExtendedFileName(extended);
    }

    private static String decodeExtendedFileName(String value) {
        int languageStart = value.indexOf('\'');
        int encodedStart = languageStart < 0 ? -1 : value.indexOf('\'', languageStart + 1);
        if (encodedStart < 0) return value;
        String charsetName = value.substring(0, languageStart);
        String encoded = value.substring(encodedStart + 1).replace("+", "%2B");
        try {
            return URLDecoder.decode(
                    encoded,
                    charsetName.isBlank() ? StandardCharsets.UTF_8 : Charset.forName(charsetName)
            );
        } catch (IllegalArgumentException exception) {
            return value;
        }
    }

    private static String parameter(String header, String name) {
        if (header == null) return null;
        for (String part : header.split(";")) {
            String trimmed = part.trim();
            String prefix = name + "=";
            if (!trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) continue;
            String value = trimmed.substring(prefix.length()).trim();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1).replace("\\\"", "\"");
            }
            return value;
        }
        return null;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static int indexOf(byte[] source, byte[] target, int from) {
        outer:
        for (int index = Math.max(from, 0); index <= source.length - target.length; index++) {
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) continue outer;
            }
            return index;
        }
        return -1;
    }

    /**
     * Multipart form data.
     *
     * @param parts parsed parts
     */
    public record MultipartData(List<Part> parts) {
        public MultipartData {
            Objects.requireNonNull(parts, "parts");
            parts = List.copyOf(parts);
        }

        public Part first(String name) {
            return parts.stream().filter(part -> part.name().equals(name)).findFirst().orElse(null);
        }

        public List<Part> named(String name) {
            return parts.stream().filter(part -> part.name().equals(name)).toList();
        }

        public Map<String, String> fields() {
            Map<String, String> fields = new LinkedHashMap<>();
            for (Part part : parts) {
                if (part.fileName() == null) fields.put(part.name(), part.text());
            }
            return Collections.unmodifiableMap(fields);
        }
    }

    /**
     * One multipart field or file.
     *
     * @param name        field name
     * @param fileName    optional file name
     * @param contentType part content type
     * @param content     content bytes
     */
    public record Part(String name, String fileName, String contentType, byte[] content) {
        public Part {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(contentType, "contentType");
            Objects.requireNonNull(content, "content");
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }

        public String text() {
            return new String(content, StandardCharsets.UTF_8);
        }
    }
}
