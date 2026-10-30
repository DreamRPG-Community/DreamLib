package cn.mythicland.dreamlib.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common bounded request, token authentication, and error handling wrapper.
 */
public abstract class AuthenticatedHttpHandler implements HttpHandler {

    private final Logger logger;
    private final WebAuth auth;
    private final int maxBodyBytes;

    /**
     * Creates an authenticated handler.
     *
     * @param logger       logger for unexpected failures
     * @param auth         token authenticator
     * @param maxBodyBytes maximum request body size
     */
    protected AuthenticatedHttpHandler(Logger logger, WebAuth auth, int maxBodyBytes) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.auth = Objects.requireNonNull(auth, "auth");
        if (maxBodyBytes < 1) throw new IllegalArgumentException("maxBodyBytes must be positive");
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            WebResponse response = new WebResponse(exchange);
            try {
                WebRequest request = WebRequest.from(exchange, maxBodyBytes);
                if (requiresAuthentication(request) && !auth.authorized(request)) {
                    response.error(401, "UNAUTHORIZED", "需要有效的访问令牌");
                    return;
                }
                handleAuthenticated(request, response);
            } catch (WebException exception) {
                response.error(exception.status(), exception.code(), exception.getMessage());
            } catch (IllegalArgumentException exception) {
                response.error(400, "BAD_REQUEST", exception.getMessage());
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Embedded web request failed", exception);
                response.error(500, "INTERNAL_ERROR", "服务器内部错误");
            }
        }
    }

    /**
     * Allows a handler to leave static assets public while keeping business routes protected.
     *
     * @param request request
     * @return whether authentication is required
     */
    @SuppressWarnings("SameReturnValue")
    protected boolean requiresAuthentication(WebRequest request) {
        return true;
    }

    /**
     * Handles a validated request.
     *
     * @param request  request snapshot
     * @param response response writer
     * @throws IOException if the route fails to read or write the exchange
     */
    @SuppressWarnings("RedundantThrows")
    protected abstract void handleAuthenticated(WebRequest request, WebResponse response) throws IOException;
}
