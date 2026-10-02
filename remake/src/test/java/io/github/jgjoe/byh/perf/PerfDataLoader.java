package io.github.jgjoe.byh.perf;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC loader for the measurement data (RQ-F-011).
 *
 * <p>Orders are inserted in generated (load) order with a single session, so the identity
 * ORDER_ID values are assigned in that order. After each batch the new IDs are read back with
 * {@code ORDER_ID > lastMax} - in a single-session load this returns exactly the batch in insert
 * order - and used to link the order lines. No index beyond the primary keys is created.</p>
 */
public final class PerfDataLoader {

    private static final String MEMBER_INSERT =
            "INSERT INTO MEMBER (ID, PASSWORD_HASH, NAME) VALUES (?, ?, ?)";
    private static final String PRODUCT_INSERT =
            "INSERT INTO PRODUCT (PRODUCT_ID, PRODUCT_NAME, REGULAR_PRICE, DISCOUNT_PRICE, MANUFACTURER)"
                    + " VALUES (?, ?, ?, ?, ?)";
    private static final String ORDER_INSERT =
            "INSERT INTO ORDERS (USER_ID, ORDER_DATE, DELIVERY_DATE, TOTAL_PRICE, RECIPIENT_NAME,"
                    + " SHIPPING_ADDRESS, SHIPPING_ZIPCODE) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String ITEM_INSERT =
            "INSERT INTO ORDER_ITEMS (ORDER_ID, PRODUCT_ID, QUANTITY, PRICE) VALUES (?, ?, ?, ?)";
    private static final String NEW_ORDER_IDS =
            "SELECT ORDER_ID FROM ORDERS WHERE ORDER_ID > ? ORDER BY ORDER_ID";

    /** No login is attempted against generated members; the NOT NULL column only needs a value. */
    private static final String PASSWORD_HASH = "!";
    private static final int ORDER_BATCH = 2_000;

    private PerfDataLoader() {
    }

    /** Inserts the generated rows into a freshly migrated schema, commits, then calls gatherStatistics. */
    public static LoadResult load(Connection connection, GeneratorConfig config) throws SQLException {
        long started = System.nanoTime();
        GeneratedData data = PerfDataGenerator.generate(config);
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        long orderItems;
        try {
            insertMembers(connection, data.members());
            insertProducts(connection, data.products());
            orderItems = insertOrdersAndItems(connection, data.orders());
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
        gatherStatistics(connection);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        return new LoadResult(data.members().size(), data.products().size(), data.orders().size(),
                orderItems, elapsed);
    }

    private static void insertMembers(Connection connection, List<GenMember> members) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MEMBER_INSERT)) {
            for (GenMember member : members) {
                statement.setString(1, member.id());
                statement.setString(2, PASSWORD_HASH);
                statement.setString(3, member.name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertProducts(Connection connection, List<GenProduct> products) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PRODUCT_INSERT)) {
            for (GenProduct product : products) {
                statement.setString(1, product.id());
                statement.setString(2, product.name());
                statement.setInt(3, product.regularPrice());
                statement.setInt(4, product.discountPrice());
                statement.setString(5, product.manufacturer());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static long insertOrdersAndItems(Connection connection, List<GenOrder> orders) throws SQLException {
        long lastOrderId = 0;
        long orderItemCount = 0;
        List<GenOrder> batch = new ArrayList<>(ORDER_BATCH);
        try (PreparedStatement orderInsert = connection.prepareStatement(ORDER_INSERT);
                PreparedStatement itemInsert = connection.prepareStatement(ITEM_INSERT);
                PreparedStatement newOrderIds = connection.prepareStatement(NEW_ORDER_IDS)) {
            for (GenOrder order : orders) {
                bindOrder(orderInsert, order);
                orderInsert.addBatch();
                batch.add(order);
                if (batch.size() == ORDER_BATCH) {
                    orderInsert.executeBatch();
                    lastOrderId = insertItemsOfBatch(newOrderIds, itemInsert, batch, lastOrderId);
                    orderItemCount += itemCount(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                orderInsert.executeBatch();
                insertItemsOfBatch(newOrderIds, itemInsert, batch, lastOrderId);
                orderItemCount += itemCount(batch);
            }
        }
        return orderItemCount;
    }

    private static void bindOrder(PreparedStatement statement, GenOrder order) throws SQLException {
        statement.setString(1, order.userId());
        statement.setObject(2, order.orderDate());
        statement.setObject(3, order.deliveryDate());
        statement.setLong(4, order.totalPrice());
        statement.setString(5, order.recipientName());
        statement.setString(6, order.address());
        statement.setString(7, order.zipcode());
    }

    /**
     * Reads back the identity ORDER_IDs of the just-inserted batch and inserts the batch's order
     * lines. The query returns rows in insert order, which is what the batch list holds.
     */
    private static long insertItemsOfBatch(PreparedStatement newOrderIds, PreparedStatement itemInsert,
                                           List<GenOrder> batch, long lastOrderId) throws SQLException {
        newOrderIds.setLong(1, lastOrderId);
        newOrderIds.setFetchSize(batch.size());
        List<Long> ids = new ArrayList<>(batch.size());
        try (ResultSet resultSet = newOrderIds.executeQuery()) {
            while (resultSet.next()) {
                ids.add(resultSet.getLong(1));
            }
        }
        if (ids.size() != batch.size()) {
            throw new SQLException("Expected " + batch.size() + " new ORDER_ID values but read "
                    + ids.size() + "; ORDERS may already contain rows, or another session inserted orders");
        }
        for (int i = 0; i < ids.size(); i++) {
            long orderId = ids.get(i);
            for (GenItem item : batch.get(i).items()) {
                itemInsert.setLong(1, orderId);
                itemInsert.setString(2, item.productId());
                itemInsert.setInt(3, item.quantity());
                itemInsert.setInt(4, item.price());
                itemInsert.addBatch();
            }
        }
        itemInsert.executeBatch();
        return ids.get(ids.size() - 1);
    }

    private static int itemCount(List<GenOrder> batch) {
        int count = 0;
        for (GenOrder order : batch) {
            count += order.items().size();
        }
        return count;
    }

    /**
     * DBMS_STATS on MEMBER, PRODUCT, ORDERS and ORDER_ITEMS of the current schema. All columns are
     * gathered without histograms (SIZE 1) except ORDERS.USER_ID, which gets SIZE 254 - that is the
     * skewed column the measurement design wants the optimizer to know about.
     */
    public static void gatherStatistics(Connection connection) throws SQLException {
        gatherTableStatistics(connection, "MEMBER", "FOR ALL COLUMNS SIZE 1");
        gatherTableStatistics(connection, "PRODUCT", "FOR ALL COLUMNS SIZE 1");
        gatherTableStatistics(connection, "ORDERS", "FOR ALL COLUMNS SIZE 1 FOR COLUMNS USER_ID SIZE 254");
        gatherTableStatistics(connection, "ORDER_ITEMS", "FOR ALL COLUMNS SIZE 1");
    }

    private static void gatherTableStatistics(Connection connection, String table, String methodOpt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "BEGIN DBMS_STATS.GATHER_TABLE_STATS(OWNNAME => USER, TABNAME => ?, CASCADE => TRUE,"
                        + " NO_INVALIDATE => FALSE, METHOD_OPT => ?); END;")) {
            statement.setString(1, table);
            statement.setString(2, methodOpt);
            statement.execute();
        }
    }
}
