package cn.mythicland.dreamlib.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Immutable request snapshot for Lib's embedded HTTP services.
 */
public final class WebRequest {

    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final Map<String, String> headers;
    private final byte[] body;

    private WebRequest(
            String method,
            String path,
            Map<String, String> query,
            Map<String, String> headers,
            byte[] body
    ) {
        this.method = method;
        this.path = path;
        this.query = Collections.unmodifiableMap(new LinkedHashMap<>(query));
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.body = body.clone();
    }

    /**
     * Reads an exchange into a bounded request snapshot.
     *
     * @param exchange HTTP exchange
     * @param maxBytes maximum request body size
     * @return request snapshot
     * @throws IOException if the request body cannot be read
     */
    public static WebRequest from(HttpExchange exchange, int maxBytes) throws IOException {
        Objects.requireNonNull(exchange, "exchange");
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        byte[] body = exchange.getRequestBody().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            throw new WebException(413, "BODY_TOO_LARGE", "请求内容过大");
        }
        URI uri = exchange.getRequestURI();
        Map<String, String> query = parsePairs(uri.getRawQuery());
        Map<String, String> headers = new LinkedHashMap<>();
        Headers exchangeHeaders = exchange.getRequestHeaders();
        exchangeHeaders.forEach((name, values) -> {
            if (!values.isEmpty()) headers.put(name.toLowerCase(Locale.ROOT), values.getFirst());
        });
        return new WebRequest(exchange.getRequestMethod(), uri.getPath(), query, headers, body);
    }

    private static Map<String, String> parsePairs(String raw) {
        Map<String, String> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return values;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            String[] parts = pair.split("=", 2);
            values.put(decode(parts[0]), parts.length == 1 ? "" : decode(parts[1]));
        }
        return values;
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new WebException(400, "INVALID_ENCODING", "请求编码无效");
        }
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public Map<String, String> query() {
        return query;
    }

    public String query(String name) {
        return query.get(name);
    }

    public String header(String name) {
        return name == null ? null : headers.get(name.toLowerCase(Locale.ROOT));
    }

    public String contentType() {
        return header("Content-Type");
    }

    /**
     * Returns whether this request carries a multipart form body.
     *
     * @return true for multipart/form-data requests
     */
    public boolean isMultipart() {
        String value = contentType();
        return value != null && value.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    /**
     * Returns whether this request declares a JSON body.
     *
     * @return true for application/json requests
     */
    public boolean isJson() {
        String value = contentType();
        return value != null && value.toLowerCase(Locale.ROOT).startsWith("application/json");
    }

    public byte[] body() {
        return body.clone();
    }

    /**
     * Re-checks the already buffered body against a route-specific limit.
     *
     * @param maxBytes maximum allowed bytes
     */
    public void requireBodySize(int maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        if (body.length > maxBytes) throw new WebException(413, "BODY_TOO_LARGE", "请求内容过大");
    }

    /**
     * Parses an URL-encoded form body.
     *
     * @return decoded form values
     */
    public Map<String, String> readForm() {
        return parsePairs(new String(body, StandardCharsets.UTF_8));
    }

    /**
     * Parses a JSON object body.
     *
     * @return decoded JSON object
     */
    public Map<String, Object> readJson() {
        try {
            return JsonCodec.parseObject(new String(body, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new WebException(400, "INVALID_JSON", "JSON 请求无效");
        }
    }

    /**
     * Parses a multipart form body.
     *
     * @return multipart parts
     */
    public MultipartParser.MultipartData readMultipart() {
        return MultipartParser.parse(contentType(), body);
    }
}
