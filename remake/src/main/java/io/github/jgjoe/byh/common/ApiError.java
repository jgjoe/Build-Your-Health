package io.github.jgjoe.byh.common;

/**
 * Error body shared by every API (RQ-N-011): a stable code and a human readable message.
 * Exception class names and stack traces must never appear here.
 */
public record ApiError(String code, String message) {
}
