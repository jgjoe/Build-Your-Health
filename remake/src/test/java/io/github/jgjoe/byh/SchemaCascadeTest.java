package io.github.jgjoe.byh;

import io.github.jgjoe.byh.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** TC-210 (cascade rules of the migrated schema, RQ-F-001). */
class SchemaCascadeTest extends IntegrationTestBase {

    @Test
    void tc210_deletingOrderCascadesToItems() {
        insertMember("alice", "pw1234", "앨리스");
        insertProduct("P1", "상품1", 2000, 1000, "제조사A");
        insertProduct("P2", "상품2", 3000, 2500, "제조사B");

        long orderId = insertOrder("alice", "2026-05-01 10:00:00", "2026-05-05", 3500, "홍길동");
        insertOrderItem(orderId, "P1", 1, 1000);
        insertOrderItem(orderId, "P2", 1, 2500);
        assertThat(countRows("ORDER_ITEMS")).isEqualTo(2);

        jdbc.update("DELETE FROM ORDERS WHERE ORDER_ID = ?", orderId);
        assertThat(countRows("ORDERS")).isZero();
        assertThat(countRows("ORDER_ITEMS")).as("order items of a deleted order").isZero();

        insertMember("bob", "pw1234", "밥");
        long bobOrder = insertOrder("bob", "2026-05-02 10:00:00", "2026-05-05", 2000, "밥");
        insertOrderItem(bobOrder, "P1", 2, 1000);
        assertThat(countRows("ORDERS")).isEqualTo(1);

        jdbc.update("DELETE FROM MEMBER WHERE ID = ?", "bob");
        assertThat(countRows("MEMBER")).as("alice must survive").isEqualTo(1);
        assertThat(countRows("ORDERS")).as("orders of a deleted member").isZero();
        assertThat(countRows("ORDER_ITEMS")).as("items of an order of a deleted member").isZero();
    }
}
