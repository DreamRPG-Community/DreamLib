package cn.mythicland.dreamlib.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Token authentication for embedded web services.
 */
public final class WebAuth {

    private final String expectedToken;
    private final String tokenHeader;

    private WebAuth(String expectedToken, String tokenHeader) {
        this.expectedToken = Objects.requireNonNull(expectedToken, "expectedToken");
        this.tokenHeader = Objects.requireNonNull(tokenHeader, "tokenHeader");
    }

    /**
     * Creates authentication using Lib's generic token header.
     *
     * @param expectedToken expected token
     * @return token authenticator
     */
    public static WebAuth token(String expectedToken) {
        return token(expectedToken, "X-Lib-Token");
    }

    /**
     * Creates authentication using a caller-selected header while still accepting Bearer tokens.
     *
     * @param expectedToken expected token
     * @param tokenHeader   custom token header
     * @return token authenticator
     */
    public static WebAuth token(String expectedToken, String tokenHeader) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalArgumentException("expectedToken must not be blank");
        }
        if (tokenHeader == null || tokenHeader.isBlank()) {
            throw new IllegalArgumentException("tokenHeader must not be blank");
        }
        return new WebAuth(expectedToken, tokenHeader);
    }

    /**
     * Checks a request token in the configured header or Authorization Bearer header.
     *
     * @param request request
     * @return true when authorized
     */
    public boolean authorized(WebRequest request) {
        Objects.requireNonNull(request, "request");
        String supplied = request.header(tokenHeader);
        if (supplied == null) {
            String authorization = request.header("Authorization");
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                supplied = authorization.substring(7).trim();
            }
        }
        if (supplied == null) return false;
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
    }
}
