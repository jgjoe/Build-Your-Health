package io.github.jgjoe.byh.support;

import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** JDBC and MockMvc fixtures shared by the API tests. */
public final class TestFixtures {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private TestFixtures() {
    }

    public static void insertMember(JdbcTemplate jdbc, String id, String rawPassword, String name) {
        jdbc.update("INSERT INTO MEMBER (ID, PASSWORD_HASH, NAME) VALUES (?, ?, ?)",
                id, PASSWORD_ENCODER.encode(rawPassword), name);
    }

    public static void insertProduct(JdbcTemplate jdbc, String productId, String productName,
                                     int regularPrice, int discountPrice, String manufacturer) {
        jdbc.update("INSERT INTO PRODUCT (PRODUCT_ID, PRODUCT_NAME, REGULAR_PRICE, DISCOUNT_PRICE, MANUFACTURER) "
                        + "VALUES (?, ?, ?, ?, ?)",
                productId, productName, regularPrice, discountPrice, manufacturer);
    }

    public static long insertOrder(JdbcTemplate jdbc, String userId, String orderTimestamp,
                                   String deliveryDate, int totalPrice, String recipientName,
                                   String address, String zipcode) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ORDERS (USER_ID, ORDER_DATE, DELIVERY_DATE, TOTAL_PRICE, RECIPIENT_NAME, "
                            + "SHIPPING_ADDRESS, SHIPPING_ZIPCODE) VALUES (?, TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS'), "
                            + "TO_DATE(?, 'YYYY-MM-DD'), ?, ?, ?, ?)",
                    new String[] {"ORDER_ID"});
            statement.setString(1, userId);
            statement.setString(2, orderTimestamp);
            statement.setString(3, deliveryDate);
            statement.setInt(4, totalPrice);
            statement.setString(5, recipientName);
            statement.setString(6, address);
            statement.setString(7, zipcode);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public static long insertOrderItem(JdbcTemplate jdbc, long orderId, String productId, int quantity, int price) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ORDER_ITEMS (ORDER_ID, PRODUCT_ID, QUANTITY, PRICE) VALUES (?, ?, ?, ?)",
                    new String[] {"ITEM_ID"});
            statement.setLong(1, orderId);
            statement.setString(2, productId);
            statement.setInt(3, quantity);
            statement.setInt(4, price);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    public static int countRows(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    public static List<OrderItemRow> orderItems(JdbcTemplate jdbc, long orderId) {
        return jdbc.query("SELECT PRODUCT_ID, QUANTITY, PRICE FROM ORDER_ITEMS WHERE ORDER_ID = ? ORDER BY PRODUCT_ID",
                (rs, rowNum) -> new OrderItemRow(rs.getString("PRODUCT_ID"), rs.getInt("QUANTITY"), rs.getInt("PRICE")),
                orderId);
    }

    public static OrderRow order(JdbcTemplate jdbc, long orderId) {
        return jdbc.queryForObject(
                "SELECT ORDER_ID, USER_ID, TOTAL_PRICE, RECIPIENT_NAME FROM ORDERS WHERE ORDER_ID = ?",
                (rs, rowNum) -> new OrderRow(rs.getLong("ORDER_ID"), rs.getString("USER_ID"),
                        rs.getInt("TOTAL_PRICE"), rs.getString("RECIPIENT_NAME")),
                orderId);
    }

    public static MockHttpSession login(MockMvc mockMvc, String id, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(loginBody(id, password).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .session(session))
                .andReturn();
        assertThat(result.getResponse().getStatus())
                .as("login status of %s (response body: %s)", id, Responses.body(result))
                .isEqualTo(200);
        MockHttpSession created = (MockHttpSession) result.getRequest().getSession(false);
        return created != null ? created : session;
    }

    public static String loginBody(String id, String password) {
        return "{\"id\":\"" + id + "\",\"password\":\"" + password + "\"}";
    }

    /** Order request body; {@code recipientName} is omitted when {@code null}. */
    public static String orderBody(String recipientName, String deliveryDate, String address, String zipcode,
                                   String itemsJson) {
        StringBuilder json = new StringBuilder("{");
        if (recipientName != null) {
            json.append("\"recipientName\":").append(jsonString(recipientName)).append(',');
        }
        return json.append("\"deliveryDate\":").append(jsonString(deliveryDate))
                .append(",\"address\":").append(jsonString(address))
                .append(",\"zipcode\":").append(jsonString(zipcode))
                .append(",\"items\":").append(itemsJson)
                .append('}')
                .toString();
    }

    /** One order line; {@code price} is included only when the caller wants to send a price. */
    public static String orderItem(String productId, int quantity, Integer price) {
        StringBuilder json = new StringBuilder("{\"productId\":").append(jsonString(productId))
                .append(",\"quantity\":").append(quantity);
        if (price != null) {
            json.append(",\"price\":").append(price);
        }
        return json.append('}').toString();
    }

    public static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * Injects a failure into the order-item insert (RQ-N-004 / TC-206): the trigger refuses any
     * order item pointing at product {@code FAIL01}.
     */
    public static void createFailTrigger(JdbcTemplate jdbc) {
        jdbc.execute("""
                BEGIN
                  EXECUTE IMMEDIATE q'[CREATE OR REPLACE TRIGGER TRG_TEST_FAIL
                    BEFORE INSERT ON ORDER_ITEMS
                    FOR EACH ROW
                    WHEN (NEW.PRODUCT_ID = 'FAIL01')
                  BEGIN
                    RAISE_APPLICATION_ERROR(-20001, 'injected');
                  END;]';
                END;
                """);
    }

    public static void dropFailTriggerIfExists(JdbcTemplate jdbc) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM USER_TRIGGERS WHERE TRIGGER_NAME = 'TRG_TEST_FAIL'", Integer.class);
        if (existing != null && existing > 0) {
            jdbc.execute("DROP TRIGGER TRG_TEST_FAIL");
        }
    }

    /** Database row of {@code ORDER_ITEMS} as stored by the application. */
    public record OrderItemRow(String productId, int quantity, int price) {
    }

    /** Database row of {@code ORDERS} as stored by the application. */
    public record OrderRow(long orderId, String userId, int totalPrice, String recipientName) {
    }
}
