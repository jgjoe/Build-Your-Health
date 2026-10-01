package io.github.jgjoe.byh.common;

/** Request parameter is missing or out of range (400, code {@code INVALID_PARAMETER}). */
public class InvalidParameterException extends RuntimeException {

    public InvalidParameterException(String message) {
        super(message);
    }
}
