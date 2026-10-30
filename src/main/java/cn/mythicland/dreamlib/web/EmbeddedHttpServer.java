package cn.mythicland.dreamlib.web;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Small lifecycle wrapper for a plugin-owned JDK HTTP server.
 */
public final class EmbeddedHttpServer implements AutoCloseable {

    private final HttpServer server;

    private EmbeddedHttpServer(HttpServer server) {
        this.server = server;
    }

    /**
     * Starts an HTTP server with one catch-all context.
     *
     * @param bindAddress the address to bind
     * @param port        the TCP port
     * @param handler     the handler for every request
     * @param executor    the executor used by HTTP exchanges
     * @return the running server
     * @throws IOException          if binding or context creation fails
     * @throws NullPointerException if an argument is null
     */
    public static EmbeddedHttpServer start(
            String bindAddress,
            int port,
            HttpHandler handler,
            Executor executor
    ) throws IOException {
        Objects.requireNonNull(bindAddress, "bindAddress");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(executor, "executor");
        HttpServer server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
        server.createContext("/", handler);
        server.setExecutor(executor);
        server.start();
        return new EmbeddedHttpServer(server);
    }

    /**
     * Returns the actual bound port.
     *
     * @return the bound port
     */
    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * Stops accepting requests immediately.
     */
    @Override
    public void close() {
        server.stop(0);
    }
}
