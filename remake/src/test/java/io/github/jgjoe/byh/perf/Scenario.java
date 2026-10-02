package io.github.jgjoe.byh.perf;

import java.util.List;

/**
 * One index scenario of the R2 measurement design (docs/db-tuning.md 1.3): the secondary indexes
 * that exist on MEMBER, PRODUCT, ORDERS and ORDER_ITEMS while the scenario is measured.
 * Scenarios are declared in measurement order.
 */
public enum Scenario {

    /** Baseline: the R1 schema's primary keys only. */
    S0(List.of(), List.of()),

    /** Foreign-key column index on ORDER_ITEMS. */
    S1(List.of("IX_ORDER_ITEMS_ORDER"),
            List.of("CREATE INDEX IX_ORDER_ITEMS_ORDER ON ORDER_ITEMS(ORDER_ID)")),

    /** S1 plus a single-column index on the equality column. */
    S2A(List.of("IX_ORDER_ITEMS_ORDER", "IX_ORDERS_USER"),
            List.of("CREATE INDEX IX_ORDER_ITEMS_ORDER ON ORDER_ITEMS(ORDER_ID)",
                    "CREATE INDEX IX_ORDERS_USER ON ORDERS(USER_ID)")),

    /** S1 plus (equality column, sort columns): can stop after the ten newest rows. */
    S2B(List.of("IX_ORDER_ITEMS_ORDER", "IX_ORDERS_USER_DATE"),
            List.of("CREATE INDEX IX_ORDER_ITEMS_ORDER ON ORDER_ITEMS(ORDER_ID)",
                    "CREATE INDEX IX_ORDERS_USER_DATE ON ORDERS(USER_ID, ORDER_DATE, ORDER_ID)")),

    /** S1 plus the same columns in reverse order (sort columns first). */
    S2C(List.of("IX_ORDER_ITEMS_ORDER", "IX_ORDERS_DATE_USER"),
            List.of("CREATE INDEX IX_ORDER_ITEMS_ORDER ON ORDER_ITEMS(ORDER_ID)",
                    "CREATE INDEX IX_ORDERS_DATE_USER ON ORDERS(ORDER_DATE, ORDER_ID, USER_ID)"));

    private final List<String> indexNames;
    private final List<String> createStatements;

    Scenario(List<String> indexNames, List<String> createStatements) {
        this.indexNames = List.copyOf(indexNames);
        this.createStatements = List.copyOf(createStatements);
    }

    /** Names of the secondary indexes this scenario creates, in creation order. */
    public List<String> indexNames() {
        return indexNames;
    }

    /** DDL that creates exactly {@link #indexNames()}, in order. */
    public List<String> createStatements() {
        return createStatements;
    }
}
