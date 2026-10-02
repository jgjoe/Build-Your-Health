package io.github.jgjoe.byh.perf;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Integration tests for the JDBC loader and checksums: counts, TC-214, consistency, stats, indexes. */
class PerfDataLoaderTest {

    private static final long SEED = 20261002L;

    @Test
    void l1_countsAndPerUserDistribution() throws Exception {
        GeneratorConfig config = GeneratorConfig.small(SEED);
        GeneratedData expected = PerfDataGenerator.generate(config);
        long expectedItems = expected.orders().stream().mapToLong(order -> order.items().size()).sum();

        try (PerfTestSchema schema = PerfTestSchema.create()) {
            Connection connection = schema.connection();
            LoadResult result = PerfDataLoader.load(connection, config);

            assertThat(result.members()).isEqualTo(config.memberCount());
            assertThat(result.products()).isEqualTo(config.productCount());
            assertThat(result.orders()).isEqualTo(config.orderCount());
            assertThat(result.orderItems()).isEqualTo(expectedItems);

            assertThat(count(connection, "MEMBER")).isEqualTo(config.memberCount() + 1);
            assertThat(count(connection, "PRODUCT")).isEqualTo(config.productCount() + 1);
            assertThat(count(connection, "ORDERS")).isEqualTo(config.orderCount());
            assertThat(count(connection, "ORDER_ITEMS")).isEqualTo(expectedItems);

            Map<String, String> tierByMember = expected.members().stream()
                    .collect(Collectors.toMap(GenMember::id, GenMember::tier));
            Map<String, Integer> ordersPerMemberByTier = config.tiers().stream()
                    .collect(Collectors.toMap(Tier::name, Tier::ordersPerMember));
            Set<String> seenUsers = new HashSet<>();
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT USER_ID, COUNT(*) FROM ORDERS GROUP BY USER_ID")) {
                while (rs.next()) {
                    String userId = rs.getString(1);
                    seenUsers.add(userId);
                    assertThat(tierByMember).as("generated member %s", userId).containsKey(userId);
                    assertThat(rs.getLong(2))
                            .as("orders of %s", userId)
                            .isEqualTo(ordersPerMemberByTier.get(tierByMember.get(userId)).longValue());
                }
            }
            assertThat(seenUsers).hasSize(config.memberCount());
        }
    }

    @Test
    void l2_sameSeedSameChecksumDifferentSeedDifferent() throws Exception {
        GeneratorConfig config = GeneratorConfig.small(SEED);

        PerfChecksum first;
        try (PerfTestSchema schema = PerfTestSchema.create()) {
            PerfDataLoader.load(schema.connection(), config);
            first = PerfChecksum.compute(schema.connection());
        }
        PerfChecksum second;
        try (PerfTestSchema schema = PerfTestSchema.create()) {
            PerfDataLoader.load(schema.connection(), config);
            second = PerfChecksum.compute(schema.connection());
        }
        assertThat(second).isEqualTo(first);

        PerfChecksum other;
        try (PerfTestSchema schema = PerfTestSchema.create()) {
            PerfDataLoader.load(schema.connection(), GeneratorConfig.small(7L));
            other = PerfChecksum.compute(schema.connection());
        }
        assertThat(other).isNotEqualTo(first);
    }

    @Test
    void l3_totalsMatchItemSumsAndItemCounts() throws Exception {
        try (PerfTestSchema schema = PerfTestSchema.create()) {
            Connection connection = schema.connection();
            PerfDataLoader.load(connection, GeneratorConfig.small(SEED));

            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM ORDERS o WHERE o.TOTAL_PRICE <> "
                            + "(SELECT SUM(QUANTITY * PRICE) FROM ORDER_ITEMS i"
                            + " WHERE i.ORDER_ID = o.ORDER_ID)")) {
                rs.next();
                assertThat(rs.getLong(1)).as("orders with wrong total").isZero();
            }
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM ORDERS o WHERE "
                            + "(SELECT COUNT(*) FROM ORDER_ITEMS i WHERE i.ORDER_ID = o.ORDER_ID)"
                            + " NOT BETWEEN 1 AND 4")) {
                rs.next();
                assertThat(rs.getLong(1)).as("orders without 1..4 items").isZero();
            }
        }
    }

    @Test
    void l4_statisticsGathered() throws Exception {
        try (PerfTestSchema schema = PerfTestSchema.create()) {
            Connection connection = schema.connection();
            PerfDataLoader.load(connection, GeneratorConfig.small(SEED));

            long rowCount = count(connection, "ORDERS");
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT NUM_ROWS FROM USER_TABLES WHERE TABLE_NAME = 'ORDERS'")) {
                assertThat(rs.next()).as("USER_TABLES row for ORDERS").isTrue();
                assertThat(rs.getLong(1)).isEqualTo(rowCount);
            }
            assertThat(histogram(connection, "ORDERS", "USER_ID")).isNotEqualTo("NONE");
            assertThat(histogram(connection, "ORDERS", "ORDER_DATE")).isEqualTo("NONE");
        }
    }

    @Test
    void l5_onlyPrimaryKeyIndexesExist() throws Exception {
        try (PerfTestSchema schema = PerfTestSchema.create()) {
            Connection connection = schema.connection();
            PerfDataLoader.load(connection, GeneratorConfig.small(SEED));

            Set<String> indexNames = new HashSet<>();
            try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT INDEX_NAME FROM USER_INDEXES"
                            + " WHERE TABLE_NAME IN ('MEMBER', 'PRODUCT', 'ORDERS', 'ORDER_ITEMS')"
                            + " AND INDEX_TYPE <> 'LOB'")) {
                while (rs.next()) {
                    indexNames.add(rs.getString(1));
                }
            }
            assertThat(indexNames)
                    .containsExactlyInAnyOrder("PK_MEMBER", "PK_PRODUCT", "PK_ORDERS", "PK_ORDER_ITEMS");
        }
    }

    private static long count(Connection connection, String table) throws Exception {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String histogram(Connection connection, String table, String column) throws Exception {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT HISTOGRAM FROM USER_TAB_COL_STATISTICS"
                        + " WHERE TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'")) {
            assertThat(rs.next()).as("column statistics for %s.%s", table, column).isTrue();
            return rs.getString(1);
        }
    }
}
