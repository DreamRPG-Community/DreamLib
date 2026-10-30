package cn.mythicland.dreamlib.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Common JSON and HTTP response helpers.
 */
public final class WebResponse {

    private final HttpExchange exchange;

    /**
     * Creates a response context owned by the current Lib handler.
     *
     * @param exchange underlying exchange
     */
    public WebResponse(HttpExchange exchange) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
    }

    /**
     * Sends a JSON response.
     *
     * @param status HTTP status
     * @param body   response body
     * @throws IOException if writing fails
     */
    public void json(int status, Object body) throws IOException {
        send(status, "application/json; charset=utf-8", JsonCodec.stringify(body));
    }

    /**
     * Sends a standard error envelope.
     *
     * @param status  HTTP status
     * @param code    stable error code
     * @param message user-facing message
     * @throws IOException if writing fails
     */
    public void error(int status, String code, String message) throws IOException {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        json(status, Map.of("error", error));
    }

    /**
     * Sends a 404 response.
     *
     * @param message message
     * @throws IOException if writing fails
     */
    public void notFound(String message) throws IOException {
        error(404, "NOT_FOUND", message);
    }

    /**
     * Sends a 409 response.
     *
     * @param code    stable error code
     * @param message message
     * @throws IOException if writing fails
     */
    public void conflict(String code, String message) throws IOException {
        error(409, code, message);
    }

    /**
     * Sends a raw response.
     *
     * @param status      HTTP status
     * @param contentType content type
     * @param body        UTF-8 body
     * @throws IOException if writing fails
     */
    public void send(int status, String contentType, String body) throws IOException {
        send(status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sends a raw byte response.
     *
     * @param status      HTTP status
     * @param contentType content type
     * @param body        body bytes
     * @throws IOException if writing fails
     */
    public void send(int status, String contentType, byte[] body) throws IOException {
        Objects.requireNonNull(body, "body");
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try {
            exchange.getResponseBody().write(body);
        } finally {
            exchange.getResponseBody().close();
        }
    }
}
