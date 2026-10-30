package cn.mythicland.dreamlib.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class WebRequestTest {

    @Test
    void authenticatesTokenAndParsesJson() throws Exception {
        TestExchange exchange = new TestExchange("{\"ok\":true}");
        exchange.requestHeaders().set("X-Test-Token", "secret");
        WebRequest request = WebRequest.from(exchange, 1024);

        assertTrue(WebAuth.token("secret", "X-Test-Token").authorized(request));
        assertFalse(WebAuth.token("wrong", "X-Test-Token").authorized(request));
        assertEquals(Boolean.TRUE, request.readJson().get("ok"));
    }

    @Test
    void enforcesRequestBodyLimit() {
        assertThrows(WebException.class, () -> WebRequest.from(new TestExchange("12345"), 4));
    }

    private static final class TestExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final InputStream requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        private TestExchange(String body) {
            requestBody = new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        private Headers requestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return URI.create("/api/test?name=hello+world");
        }

        @Override
        public String getRequestMethod() {
            return "POST";
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int responseCode, long responseLength) {
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public int getResponseCode() {
            return -1;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream inputStream, OutputStream outputStream) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
