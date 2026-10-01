package io.github.jgjoe.byh;

import com.jayway.jsonpath.ReadContext;
import io.github.jgjoe.byh.support.IntegrationTestBase;
import io.github.jgjoe.byh.support.Responses;
import io.github.jgjoe.byh.support.TestFixtures;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** TC-204..TC-207 and TC-209 (order creation and access control, RQ-F-005..RQ-F-010, RQ-F-012, RQ-N-011). */
class OrderApiTest extends IntegrationTestBase {

    private static final String DELIVERY_DATE = "2026-10-10";
    private static final String ADDRESS = "서울시 중구 세종대로 1";
    private static final String ZIPCODE = "04523";

    @Test
    void tc204_ordersRequireLogin() throws Exception {
        insertProduct("P1", "상품1", 2000, 1000, "제조사A");
        String body = TestFixtures.orderBody("홍길동", DELIVERY_DATE, ADDRESS, ZIPCODE,
                "[" + TestFixtures.orderItem("P1", 1, null) + "]");

        MvcResult create = mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andReturn();
        assertThat(create.getResponse().getStatus()).isEqualTo(401);
        assertThat(countRows("ORDERS")).isZero();

        MvcResult history = mockMvc.perform(get("/api/orders")).andReturn();
        assertThat(history.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void tc205_orderTotalUsesDbPriceAndIgnoresClientPrice() throws Exception {
        insertMember("alice", "pw1234", "앨리스");
        insertProduct("P1", "상품1", 2000, 1000, "제조사A");
        insertProduct("P2", "상품2", 3000, 2500, "제조사B");
        MockHttpSession session = login("alice", "pw1234");

        String body = TestFixtures.orderBody("홍길동", DELIVERY_DATE, ADDRESS, ZIPCODE,
                "[" + TestFixtures.orderItem("P1", 2, 1) + "," + TestFixtures.orderItem("P2", 1, 1) + "]");
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8))
                        .session(session))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        ReadContext json = Responses.json(result);
        long orderId = Responses.longValue(json, "$.orderId");
        assertThat(Responses.longValue(json, "$.totalPrice")).isEqualTo(4500);

        assertThat(countRows("ORDERS")).isEqualTo(1);
        TestFixtures.OrderRow stored = TestFixtures.order(jdbc, orderId);
        assertThat(stored.userId()).isEqualTo("alice");
        assertThat(stored.totalPrice()).isEqualTo(4500);
        assertThat(stored.recipientName()).isEqualTo("홍길동");

        assertThat(TestFixtures.orderItems(jdbc, orderId)).containsExactlyInAnyOrder(
                new TestFixtures.OrderItemRow("P1", 2, 1000),
                new TestFixtures.OrderItemRow("P2", 1, 2500));
    }

    @Test
    void tc206_failureWhileStoringItemsRollsBackWholeOrder() throws Exception {
        insertMember("alice", "pw1234", "앨리스");
        insertProduct("P1", "상품1", 2000, 1000, "제조사A");
        insertProduct("FAIL01", "실패상품", 1000, 900, "제조사A");
        createFailTrigger();
        MockHttpSession session = login("alice", "pw1234");

        String body = TestFixtures.orderBody("홍길동", DELIVERY_DATE, ADDRESS, ZIPCODE,
                "[" + TestFixtures.orderItem("P1", 1, null) + "," + TestFixtures.orderItem("FAIL01", 1, null) + "]");
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8))
                        .session(session))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(Responses.code(result)).isEqualTo("INTERNAL_ERROR");
        assertThat(countRows("ORDERS")).as("order header must be rolled back").isZero();
        assertThat(countRows("ORDER_ITEMS")).as("order items must be rolled back").isZero();
    }

    @Test
    void tc207_rejectsInvalidOrders() throws Exception {
        insertMember("alice", "pw1234", "앨리스");
        insertProduct("P1", "상품1", 2000, 1000, "제조사A");
        MockHttpSession session = login("alice", "pw1234");

        List<String> invalidBodies = List.of(
                TestFixtures.orderBody("홍길동", DELIVERY_DATE, ADDRESS, ZIPCODE, "[]"),
                TestFixtures.orderBody("홍길동", DELIVERY_DATE, ADDRESS, ZIPCODE,
                        "[" + TestFixtures.orderItem("P1", 0, null) + "]"),
                TestFixtures.orderBody("홍길동", DELIVERY_DATE, ADDRESS, ZIPCODE,
                        "[" + TestFixtures.orderItem("P1", -3, null) + "]"),
                TestFixtures.orderBody("홍길동", DELIVERY_DATE, ADDRESS, ZIPCODE,
                        "[" + TestFixtures.orderItem("NOPE", 1, null) + "]"),
                TestFixtures.orderBody(null, DELIVERY_DATE, ADDRESS, ZIPCODE,
                        "[" + TestFixtures.orderItem("P1", 1, null) + "]"));

        for (String body : invalidBodies) {
            MvcResult result = mockMvc.perform(post("/api/orders")
                            .contentType(APPLICATION_JSON)
                            .content(body.getBytes(StandardCharsets.UTF_8))
                            .session(session))
                    .andReturn();
            assertThat(result.getResponse().getStatus()).as("request %s", body).isEqualTo(400);
            assertThat(Responses.code(result)).as("request %s", body).isEqualTo("INVALID_ORDER");
            assertThat(countRows("ORDERS")).as("request %s", body).isZero();
        }
        assertThat(countRows("ORDER_ITEMS")).isZero();
    }

    @Test
    void tc207_errorBodyHasNoStackTraceOrExceptionName() throws Exception {
        insertMember("alice", "pw1234", "앨리스");
        insertProduct("P1", "상품1", 2000, 1000, "제조사A");
        insertProduct("FAIL01", "실패상품", 1000, 900, "제조사A");
        createFailTrigger();
        MockHttpSession session = login("alice", "pw1234");

        String failingBody = TestFixtures.orderBody("홍길동", DELIVERY_DATE, ADDRESS, ZIPCODE,
                "[" + TestFixtures.orderItem("FAIL01", 1, null) + "]");
        MvcResult internalError = mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content(failingBody.getBytes(StandardCharsets.UTF_8))
                        .session(session))
                .andReturn();
        assertThat(internalError.getResponse().getStatus()).isEqualTo(500);

        String invalidBody = TestFixtures.orderBody("홍길동", DELIVERY_DATE, ADDRESS, ZIPCODE,
                "[" + TestFixtures.orderItem("P1", 0, null) + "]");
        MvcResult badRequest = mockMvc.perform(post("/api/orders")
                        .contentType(APPLICATION_JSON)
                        .content(invalidBody.getBytes(StandardCharsets.UTF_8))
                        .session(session))
                .andReturn();
        assertThat(badRequest.getResponse().getStatus()).isEqualTo(400);

        for (MvcResult result : List.of(internalError, badRequest)) {
            String body = Responses.body(result);
            assertThat(Responses.fieldNames(result)).as("error body %s", body)
                    .containsExactlyInAnyOrder("code", "message");
            assertThat(body).doesNotContain("Exception");
            assertThat(body).doesNotContain("ORA-");
            assertThat(body).doesNotContain("at io.github");
        }
    }

    @Test
    void tc209_memberSeesOnlyOwnOrders() throws Exception {
        insertMember("alice", "pw1234", "앨리스");
        insertMember("bob", "pw1234", "밥");
        insertProduct("P1", "상품1", 2000, 1000, "제조사A");

        long aliceFirst = insertOrder("alice", "2026-01-01 10:00:00", "2026-01-05", 1000, "앨리스");
        insertOrderItem(aliceFirst, "P1", 1, 1000);
        long aliceSecond = insertOrder("alice", "2026-01-02 10:00:00", "2026-01-05", 1000, "앨리스");
        insertOrderItem(aliceSecond, "P1", 1, 1000);
        long bobOrder = insertOrder("bob", "2026-01-03 10:00:00", "2026-01-05", 1000, "밥");
        insertOrderItem(bobOrder, "P1", 1, 1000);

        MockHttpSession bobSession = login("bob", "pw1234");

        MvcResult bobHistory = mockMvc.perform(get("/api/orders").session(bobSession)).andReturn();
        assertThat(bobHistory.getResponse().getStatus()).isEqualTo(200);
        ReadContext bobJson = Responses.json(bobHistory);
        assertThat(Responses.longValue(bobJson, "$.total")).isEqualTo(1);
        assertThat(Responses.longs(bobJson, "$.items[*].orderId")).containsExactly(bobOrder);

        MvcResult foreignOrder = mockMvc.perform(get("/api/orders/{orderId}", aliceFirst).session(bobSession))
                .andReturn();
        assertThat(foreignOrder.getResponse().getStatus()).as("another member's order").isEqualTo(404);
        assertThat(Responses.code(foreignOrder)).isEqualTo("NOT_FOUND");

        MockHttpSession aliceSession = login("alice", "pw1234");
        MvcResult ownOrder = mockMvc.perform(get("/api/orders/{orderId}", aliceSecond).session(aliceSession))
                .andReturn();
        assertThat(ownOrder.getResponse().getStatus()).isEqualTo(200);
        assertThat(Responses.longValue(Responses.json(ownOrder), "$.orderId")).isEqualTo(aliceSecond);
    }
}
