package io.github.jgjoe.byh.perf;

/**
 * One member tier of the measurement data: how many members it has and how many orders each of
 * them places. Tiers are processed in config order, which is also the member-ID order.
 */
public record Tier(String name, int members, int ordersPerMember) {
}
