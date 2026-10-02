package io.github.jgjoe.byh.perf;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Row counts plus order-independent, ORDER_ID/ITEM_ID-independent content hashes of the current
 * schema (TC-214). Each hash is the SUM of ORA_HASH over a '|'-joined text of the business columns,
 * so the value does not depend on physical row order or on identity values.
 */
public record PerfChecksum(long members, long products, long orders, long orderItems,
                           long memberHash, long productHash, long orderHash, long itemHash) {

    private static final String MEMBER_HASH =
            "SELECT SUM(ORA_HASH(ID || '|' || NAME)) FROM MEMBER";

    private static final String PRODUCT_HASH =
            "SELECT SUM(ORA_HASH(PRODUCT_ID || '|' || PRODUCT_NAME || '|' || REGULAR_PRICE || '|'"
                    + " || DISCOUNT_PRICE || '|' || MANUFACTURER)) FROM PRODUCT";

    private static final String ORDER_HASH =
            "SELECT SUM(ORA_HASH(USER_ID || '|' || TO_CHAR(ORDER_DATE, 'YYYYMMDDHH24MISS') || '|'"
                    + " || TO_CHAR(DELIVERY_DATE, 'YYYYMMDD') || '|' || TOTAL_PRICE || '|'"
                    + " || RECIPIENT_NAME || '|' || SHIPPING_ADDRESS || '|' || SHIPPING_ZIPCODE)) FROM ORDERS";

    private static final String ITEM_HASH =
            "SELECT SUM(ORA_HASH(o.USER_ID || '|' || TO_CHAR(o.ORDER_DATE, 'YYYYMMDDHH24MISS') || '|'"
                    + " || oi.PRODUCT_ID || '|' || oi.QUANTITY || '|' || oi.PRICE))"
                    + " FROM ORDER_ITEMS oi JOIN ORDERS o ON o.ORDER_ID = oi.ORDER_ID";

    public static PerfChecksum compute(Connection connection) throws SQLException {
        return new PerfChecksum(
                count(connection, "MEMBER"),
                count(connection, "PRODUCT"),
                count(connection, "ORDERS"),
                count(connection, "ORDER_ITEMS"),
                hash(connection, MEMBER_HASH),
                hash(connection, PRODUCT_HASH),
                hash(connection, ORDER_HASH),
                hash(connection, ITEM_HASH));
    }

    private static long count(Connection connection, String table) throws SQLException {
        return hash(connection, "SELECT COUNT(*) FROM " + table);
    }

    private static long hash(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            long value = resultSet.getLong(1);
            return resultSet.wasNull() ? 0L : value;
        }
    }
}
