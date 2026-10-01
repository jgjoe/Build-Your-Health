package io.github.jgjoe.byh.common;

/** The requested resource does not exist for the current member (404, code {@code NOT_FOUND}). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
