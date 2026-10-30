package cn.mythicland.dreamlib.web;

import java.io.Serial;
import java.util.Objects;

/**
 * Expected HTTP failure raised by a web handler.
 */
public final class WebException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    /**
     * Creates an expected HTTP failure.
     *
     * @param status  HTTP status
     * @param code    stable machine-readable code
     * @param message user-facing message
     */
    public WebException(int status, String code, String message) {
        super(Objects.requireNonNull(message, "message"));
        if (status < 400 || status > 599) throw new IllegalArgumentException("Invalid HTTP error status");
        this.status = status;
        this.code = Objects.requireNonNull(code, "code");
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
