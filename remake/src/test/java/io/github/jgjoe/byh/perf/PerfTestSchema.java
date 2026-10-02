package io.github.jgjoe.byh.perf;

import io.github.jgjoe.byh.support.OracleTestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.testcontainers.oracle.OracleContainer;

/**
 * Isolated Oracle schema for perf measurement tests (TC-214 tooling).
 *
 * <p>The app's integration tests delete every row of the default user schema before each test,
 * so perf tests must never write there. Each instance creates a uniquely named Oracle user
 * through the container's built-in SYSTEM account (password supplied at runtime by
 * Testcontainers), migrates it with the app's Flyway locations up to the R1 baseline (V1 schema + V2 seed),
 * and holds one connection for that user. Closing drops the user CASCADE.</p>
 */
public final class PerfTestSchema implements AutoCloseable {

    /** Built-in Oracle DBA account; the password always comes from the container at runtime. */
    private static final String ADMIN_USER = "SYSTEM";

    private final String jdbcUrl;
    private final String adminPassword;
    private final String username;
    private final Connection connection;

    private PerfTestSchema(String jdbcUrl, String adminPassword, String username, Connection connection) {
        this.jdbcUrl = jdbcUrl;
        this.adminPassword = adminPassword;
        this.username = username;
        this.connection = connection;
    }

    /** Creates a fresh user, migrates it, and opens its connection. */
    public static PerfTestSchema create() throws SQLException {
        OracleContainer container = OracleTestDatabase.container();
        String jdbcUrl = container.getJdbcUrl();
        String adminPassword = container.getPassword();
        String username = "PERF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        // Alphanumeric starting with a letter: safe as an unquoted Oracle password.
        String password = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        try (Connection admin = DriverManager.getConnection(jdbcUrl, ADMIN_USER, adminPassword);
                Statement stmt = admin.createStatement()) {
            stmt.execute("CREATE USER " + username + " IDENTIFIED BY " + password);
            stmt.execute("GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE TRIGGER,"
                    + " UNLIMITED TABLESPACE TO " + username);
            // The measurement harness reads V$SQL and DBMS_XPLAN.DISPLAY_CURSOR as this user.
            stmt.execute("GRANT SELECT_CATALOG_ROLE TO " + username);
        }
        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .target(PerfDataLoader.BASELINE_SCHEMA_VERSION)
                .load()
                .migrate();
        Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
        return new PerfTestSchema(jdbcUrl, adminPassword, username, connection);
    }

    /** The connection of the dedicated user; closed (with the schema) by {@link #close()}. */
    public Connection connection() {
        return connection;
    }

    /** The dedicated Oracle user backing this schema. */
    public String username() {
        return username;
    }

    @Override
    public void close() throws SQLException {
        try {
            connection.close();
        } finally {
            try (Connection admin = DriverManager.getConnection(jdbcUrl, ADMIN_USER, adminPassword);
                    Statement stmt = admin.createStatement()) {
                stmt.execute("DROP USER " + username + " CASCADE");
            }
        }
    }
}
