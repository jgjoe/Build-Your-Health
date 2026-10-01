package io.github.jgjoe.byh.common;

/** The order request is semantically invalid and nothing was stored (400, code {@code INVALID_ORDER}). */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
