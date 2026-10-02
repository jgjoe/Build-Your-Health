package io.github.jgjoe.byh.perf;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the fixed index scenarios: declaration order, index names, and the exact
 * table/column list every create statement must cover.
 */
class ScenarioTest {

    /** One index a scenario must create: its name and the table(columns) it covers. */
    private record ExpectedIndex(String name, String tableAndColumns) {
    }

    private static final ExpectedIndex ITEMS_ORDER_ID =
            new ExpectedIndex("IX_ORDER_ITEMS_ORDER", "ORDER_ITEMS(ORDER_ID)");
    private static final ExpectedIndex ORDERS_USER =
            new ExpectedIndex("IX_ORDERS_USER", "ORDERS(USER_ID)");
    private static final ExpectedIndex ORDERS_USER_DATE =
            new ExpectedIndex("IX_ORDERS_USER_DATE", "ORDERS(USER_ID, ORDER_DATE, ORDER_ID)");
    private static final ExpectedIndex ORDERS_DATE_USER =
            new ExpectedIndex("IX_ORDERS_DATE_USER", "ORDERS(ORDER_DATE, ORDER_ID, USER_ID)");

    @Test
    void declarationOrderIsS0S1S2aS2bS2c() {
        assertThat(Scenario.values()).containsExactly(
                Scenario.S0, Scenario.S1, Scenario.S2A, Scenario.S2B, Scenario.S2C);
    }

    @Test
    void s0HasNoIndexesAndNoStatements() {
        assertThat(Scenario.S0.indexNames()).isEmpty();
        assertThat(Scenario.S0.createStatements()).isEmpty();
    }

    @Test
    void indexNamesMatchTheFixedScenarios() {
        assertThat(Scenario.S1.indexNames()).containsExactly("IX_ORDER_ITEMS_ORDER");
        assertThat(Scenario.S2A.indexNames()).containsExactly("IX_ORDER_ITEMS_ORDER", "IX_ORDERS_USER");
        assertThat(Scenario.S2B.indexNames()).containsExactly("IX_ORDER_ITEMS_ORDER", "IX_ORDERS_USER_DATE");
        assertThat(Scenario.S2C.indexNames()).containsExactly("IX_ORDER_ITEMS_ORDER", "IX_ORDERS_DATE_USER");
    }

    @Test
    void createStatementsTargetExactlyTheFixedTableAndColumns() {
        assertCreateStatements(Scenario.S1, List.of(ITEMS_ORDER_ID));
        assertCreateStatements(Scenario.S2A, List.of(ITEMS_ORDER_ID, ORDERS_USER));
        assertCreateStatements(Scenario.S2B, List.of(ITEMS_ORDER_ID, ORDERS_USER_DATE));
        assertCreateStatements(Scenario.S2C, List.of(ITEMS_ORDER_ID, ORDERS_DATE_USER));
    }

    /**
     * Every scenario creates exactly the expected indexes: one statement per index name, each
     * statement normalized (upper case, single spaces, no whitespace around parentheses, single
     * space after commas) contains that index's table and column list and nothing else in it.
     */
    private static void assertCreateStatements(Scenario scenario, List<ExpectedIndex> expected) {
        List<String> statements = scenario.createStatements().stream()
                .map(ScenarioTest::normalize)
                .toList();
        assertThat(statements).as("%s create statements", scenario).hasSize(expected.size());
        assertThat(statements).allSatisfy(statement -> assertThat(statement).startsWith("CREATE INDEX"));
        for (ExpectedIndex index : expected) {
            List<String> matching = statements.stream()
                    .filter(statement -> statement.contains(index.name()))
                    .toList();
            assertThat(matching).as("%s: statements creating %s", scenario, index.name()).hasSize(1);
            assertThat(matching.get(0))
                    .as("%s: %s columns", scenario, index.name())
                    .contains("ON " + index.tableAndColumns());
        }
    }

    /** Upper case, collapse whitespace, then drop spaces around parentheses and commas. */
    private static String normalize(String statement) {
        return statement.toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*\\(\\s*", "(")
                .replaceAll("\\s*\\)", ")")
                .replaceAll("\\s*,\\s*", ", ")
                .trim();
    }
}
