package io.github.jgjoe.byh.perf;

/** The three order queries of the R2 measurement design (docs/db-tuning.md 1.1). */
public enum MeasuredQuery {

    /** Order history first page: {@code findOrdersByUserId}, offset 0, size 10. */
    Q1("io.github.jgjoe.byh.order.OrderMapper.findOrdersByUserId"),

    /** Order history total count: {@code countOrdersByUserId}. */
    Q2("io.github.jgjoe.byh.order.OrderMapper.countOrdersByUserId"),

    /** Order detail: {@code findOrderByIdAndUserId}. */
    Q3("io.github.jgjoe.byh.order.OrderMapper.findOrderByIdAndUserId");

    private final String statementId;

    MeasuredQuery(String statementId) {
        this.statementId = statementId;
    }

    /** The MyBatis mapped statement id inside {@code mapper/OrderMapper.xml}. */
    public String statementId() {
        return statementId;
    }
}
