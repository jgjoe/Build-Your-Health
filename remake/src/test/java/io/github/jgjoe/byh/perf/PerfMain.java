package io.github.jgjoe.byh.perf;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;

/**
 * Command-line entry point for the perf measurement database (RQ-F-011).
 *
 * <p>Connection details come only from the environment ({@code PERF_DB_URL},
 * {@code PERF_DB_USERNAME}, {@code PERF_DB_PASSWORD}) - nothing is hardcoded.</p>
 *
 * <pre>
 *   load [--seed N]   migrate the target schema, refuse a non-empty ORDERS, load full(seed)
 *   checksum          print row counts and content hashes as key=value lines
 * </pre>
 */
public final class PerfMain {

    private static final String URL_ENV = "PERF_DB_URL";
    private static final String USERNAME_ENV = "PERF_DB_USERNAME";
    private static final String PASSWORD_ENV = "PERF_DB_PASSWORD";

    private static final long DEFAULT_SEED = 20261002L;

    private PerfMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(2);
            return;
        }
        switch (args[0]) {
            case "load" -> load(args);
            case "checksum" -> checksum();
            default -> {
                System.err.println("Unknown command: " + args[0]);
                usage();
                System.exit(2);
            }
        }
    }

    /** Runs Flyway on the target schema, refuses a non-empty ORDERS, loads GeneratorConfig.full(seed). */
    private static void load(String[] args) throws SQLException {
        long seed = DEFAULT_SEED;
        for (int i = 1; i < args.length; i++) {
            if ("--seed".equals(args[i]) && i + 1 < args.length) {
                seed = Long.parseLong(args[++i]);
            } else {
                System.err.println("Usage: load [--seed N]");
                System.exit(2);
            }
        }
        String url = requiredEnvironment(URL_ENV);
        String username = requiredEnvironment(USERNAME_ENV);
        String password = requiredEnvironment(PASSWORD_ENV);

        System.out.println("Migrating " + username + " with classpath:db/migration ...");
        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            long existingOrders = countOrders(connection);
            if (existingOrders > 0) {
                System.err.println("Refusing to load: ORDERS already contains " + existingOrders
                        + " rows. Reset the measurement DB first:"
                        + " docker compose -f perf/compose.yaml down -v");
                System.exit(1);
                return;
            }
            GeneratorConfig config = GeneratorConfig.full(seed);
            System.out.println("Generating and loading " + config.memberCount() + " members, "
                    + config.productCount() + " products, " + config.orderCount()
                    + " orders (seed " + seed + ") ...");
            LoadResult result = PerfDataLoader.load(connection, config);
            System.out.println("Loaded members=" + result.members()
                    + " products=" + result.products()
                    + " orders=" + result.orders()
                    + " orderItems=" + result.orderItems());
            System.out.println("elapsed=" + result.elapsed());
        }
    }

    private static void checksum() throws SQLException {
        String url = requiredEnvironment(URL_ENV);
        String username = requiredEnvironment(USERNAME_ENV);
        String password = requiredEnvironment(PASSWORD_ENV);
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            PerfChecksum checksum = PerfChecksum.compute(connection);
            System.out.println("members=" + checksum.members());
            System.out.println("products=" + checksum.products());
            System.out.println("orders=" + checksum.orders());
            System.out.println("orderItems=" + checksum.orderItems());
            System.out.println("memberHash=" + checksum.memberHash());
            System.out.println("productHash=" + checksum.productHash());
            System.out.println("orderHash=" + checksum.orderHash());
            System.out.println("itemHash=" + checksum.itemHash());
        }
    }

    private static long countOrders(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT COUNT(*) FROM ORDERS")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            List<String> missing = new ArrayList<>();
            for (String candidate : List.of(URL_ENV, USERNAME_ENV, PASSWORD_ENV)) {
                String candidateValue = System.getenv(candidate);
                if (candidateValue == null || candidateValue.isBlank()) {
                    missing.add(candidate);
                }
            }
            System.err.println("Missing environment variable(s): " + String.join(", ", missing)
                    + ". Set PERF_DB_URL, PERF_DB_USERNAME and PERF_DB_PASSWORD.");
            System.exit(2);
        }
        return value;
    }

    private static void usage() {
        System.err.println("Usage: PerfMain <load [--seed N] | checksum>");
    }
}
