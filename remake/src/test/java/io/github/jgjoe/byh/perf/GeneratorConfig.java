package io.github.jgjoe.byh.perf;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Fixed description of a measurement data set: seed, member tiers, products and order window. */
public record GeneratorConfig(long seed, List<Tier> tiers, int productCount,
                              LocalDateTime fromInclusive, LocalDateTime toExclusive) {

    public GeneratorConfig {
        tiers = List.copyOf(tiers);
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("tiers must not be empty");
        }
        if (productCount <= 0) {
            throw new IllegalArgumentException("productCount must be positive: " + productCount);
        }
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "fromInclusive must be before toExclusive: " + fromInclusive + " .. " + toExclusive);
        }
    }

    /** heavy 20x10,000 / mid 2,000x150 / light 20,000x5, 2,000 products, 2023-10-01T00:00 .. 2026-10-01T00:00 */
    public static GeneratorConfig full(long seed) {
        return new GeneratorConfig(seed,
                List.of(new Tier("heavy", 20, 10_000),
                        new Tier("mid", 2_000, 150),
                        new Tier("light", 20_000, 5)),
                2_000,
                LocalDateTime.of(2023, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0));
    }

    /** heavy 2x200 / mid 10x20 / light 50x2, 30 products, same date range */
    public static GeneratorConfig small(long seed) {
        return new GeneratorConfig(seed,
                List.of(new Tier("heavy", 2, 200),
                        new Tier("mid", 10, 20),
                        new Tier("light", 50, 2)),
                30,
                LocalDateTime.of(2023, 10, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0));
    }

    public int memberCount() {
        int count = 0;
        for (Tier tier : tiers) {
            count += tier.members();
        }
        return count;
    }

    public long orderCount() {
        long count = 0;
        for (Tier tier : tiers) {
            count += (long) tier.members() * tier.ordersPerMember();
        }
        return count;
    }

    /** ID of the first member of the named tier, e.g. full(): heavy=u000001, mid=u000021, light=u002021 */
    public String representative(String tierName) {
        int membersBefore = 0;
        for (Tier tier : tiers) {
            if (tier.name().equals(tierName)) {
                return memberId(membersBefore + 1);
            }
            membersBefore += tier.members();
        }
        throw new IllegalArgumentException("No tier named '" + tierName + "' in " + tiers);
    }

    /** {@code u} + 6-digit sequence, shared by the generator and the representative lookup. */
    static String memberId(int sequence) {
        return String.format(Locale.ROOT, "u%06d", sequence);
    }
}
