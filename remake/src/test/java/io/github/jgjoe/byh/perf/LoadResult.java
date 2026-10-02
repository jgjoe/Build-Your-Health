package io.github.jgjoe.byh.perf;

import java.time.Duration;

/** Rows inserted by one load (generated rows only; Flyway seed rows are not counted). */
public record LoadResult(long members, long products, long orders, long orderItems, Duration elapsed) {
}
