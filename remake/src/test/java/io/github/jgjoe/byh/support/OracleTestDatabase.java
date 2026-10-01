package io.github.jgjoe.byh.support;

import java.time.Duration;
import org.testcontainers.oracle.OracleContainer;

/**
 * Single Oracle Free container shared by every test in the JVM: the static field starts it on
 * first use and all Spring contexts point at the same instance, so Flyway and the schema are
 * paid for once per test run.
 */
public final class OracleTestDatabase {

    private static final String IMAGE = "gvenzl/oracle-free:23-slim-faststart";

    private static final OracleContainer CONTAINER = startContainer();

    private OracleTestDatabase() {
    }

    public static OracleContainer container() {
        return CONTAINER;
    }

    private static OracleContainer startContainer() {
        OracleContainer container = new OracleContainer(IMAGE)
                // The Testcontainers default is 60s; the first boot of the image can take longer.
                .withStartupTimeout(Duration.ofMinutes(10));
        container.start();
        return container;
    }
}
