package io.github.jgjoe.byh;

import io.github.jgjoe.byh.support.IntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Secondary indexes adopted from the R2 measurements (docs/db-tuning.md, scenario S2b).
 * Column order is part of the decision: the equality column must lead.
 */
class SchemaIndexTest extends IntegrationTestBase {

    @Test
    void orderItemsForeignKeyColumnIsIndexed() {
        assertThat(indexColumns("IX_ORDER_ITEMS_ORDER", "ORDER_ITEMS")).containsExactly("ORDER_ID");
    }

    @Test
    void ordersAreIndexedByMemberThenNewestFirstColumns() {
        assertThat(indexColumns("IX_ORDERS_USER_DATE", "ORDERS"))
                .containsExactly("USER_ID", "ORDER_DATE", "ORDER_ID");
    }

    private List<String> indexColumns(String indexName, String tableName) {
        return jdbc.queryForList(
                "SELECT COLUMN_NAME FROM USER_IND_COLUMNS WHERE INDEX_NAME = ? AND TABLE_NAME = ?"
                        + " ORDER BY COLUMN_POSITION",
                String.class, indexName, tableName);
    }
}
