package io.github.jgjoe.byh.common;

import java.util.List;

/** Paged list response: the page of items plus the paging metadata. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
