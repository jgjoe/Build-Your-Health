package io.github.jgjoe.byh.common;

/** Authentication failed (401, code {@code UNAUTHORIZED}). */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
