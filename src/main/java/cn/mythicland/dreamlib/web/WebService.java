package cn.mythicland.dreamlib.web;

import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Facade for Lib-owned embedded web infrastructure.
 */
public final class WebService {

    private final Executor executor;

    /**
     * Creates a web service backed by an executor owned by Lib.
     *
     * @param executor request executor
     */
    public WebService(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Starts a plugin-owned server.
     *
     * @param bindAddress bind address
     * @param port        port
     * @param handler     handler
     * @return running server
     * @throws IOException if binding fails
     */
    public EmbeddedHttpServer start(String bindAddress, int port, HttpHandler handler) throws IOException {
        return EmbeddedHttpServer.start(bindAddress, port, handler, executor);
    }

    /**
     * Creates a generic token authenticator.
     *
     * @param token expected token
     * @return authenticator
     */
    public WebAuth token(String token) {
        return WebAuth.token(token);
    }

    /**
     * Creates a token authenticator with a custom header.
     *
     * @param token  expected token
     * @param header header name
     * @return authenticator
     */
    public WebAuth token(String token, String header) {
        return WebAuth.token(token, header);
    }
}
