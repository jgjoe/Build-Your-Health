package io.github.jgjoe.byh.auth.dto;

/** Login response body: {@code {"id": "...", "name": "..."}}. */
public record LoginResponse(String id, String name) {
}
