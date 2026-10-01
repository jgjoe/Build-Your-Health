package io.github.jgjoe.byh.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts executed JDBC statements so a test can prove that a query does not grow with the
 * number of rows (RQ-F-009). Reset before the request under measurement.
 */
public final class StatementCounter {

    private static final AtomicInteger EXECUTED = new AtomicInteger();

    private StatementCounter() {
    }

    static void recordExecution() {
        EXECUTED.incrementAndGet();
    }

    public static void reset() {
        EXECUTED.set(0);
    }

    public static int executedStatements() {
        return EXECUTED.get();
    }
}
