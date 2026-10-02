package io.github.jgjoe.byh.perf;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the fixed-seed generator: distribution shape, determinism, field rules. No DB. */
class PerfDataGeneratorTest {

    private static final long SMALL_SEED = 1L;

    @Test
    void g1_fullConfigCountsAndRepresentatives() {
        GeneratorConfig config = GeneratorConfig.full(20261002L);

        assertThat(config.memberCount()).isEqualTo(22_020);
        assertThat(config.orderCount()).isEqualTo(600_000L);
        assertThat(config.representative("heavy")).isEqualTo("u000001");
        assertThat(config.representative("mid")).isEqualTo("u000021");
        assertThat(config.representative("light")).isEqualTo("u002021");
    }

    @Test
    void g2_membersPerTierAndOrdersPerMember() {
        GeneratorConfig config = GeneratorConfig.small(SMALL_SEED);
        GeneratedData data = PerfDataGenerator.generate(config);

        Map<String, Long> membersByTier = data.members().stream()
                .collect(Collectors.groupingBy(GenMember::tier, Collectors.counting()));
        Map<String, Long> ordersByUser = data.orders().stream()
                .collect(Collectors.groupingBy(GenOrder::userId, Collectors.counting()));
        Map<String, Integer> ordersPerMemberByTier = config.tiers().stream()
                .collect(Collectors.toMap(Tier::name, Tier::ordersPerMember));

        for (Tier tier : config.tiers()) {
            assertThat(membersByTier.getOrDefault(tier.name(), 0L))
                    .as("members of tier %s", tier.name())
                    .isEqualTo((long) tier.members());
        }
        for (GenMember member : data.members()) {
            assertThat(ordersByUser.getOrDefault(member.id(), 0L))
                    .as("orders of member %s", member.id())
                    .isEqualTo(ordersPerMemberByTier.get(member.tier()).longValue());
        }
    }

    @Test
    void g3_sameSeedSameDataDifferentSeedDifferentDigest() {
        GeneratedData first = PerfDataGenerator.generate(GeneratorConfig.small(1L));
        GeneratedData second = PerfDataGenerator.generate(GeneratorConfig.small(1L));
        GeneratedData other = PerfDataGenerator.generate(GeneratorConfig.small(2L));

        assertThat(second.members()).isEqualTo(first.members());
        assertThat(second.products()).isEqualTo(first.products());
        assertThat(second.orders()).isEqualTo(first.orders());
        assertThat(second.digest()).isEqualTo(first.digest());
        assertThat(other.digest()).isNotEqualTo(first.digest());
    }

    @Test
    void g4_ordersSortedAndDatesInRange() {
        GeneratorConfig config = GeneratorConfig.small(SMALL_SEED);
        List<GenOrder> orders = PerfDataGenerator.generate(config).orders();

        assertThat(orders).isSortedAccordingTo(
                Comparator.comparing(GenOrder::orderDate).thenComparing(GenOrder::userId));
        for (GenOrder order : orders) {
            assertThat(order.orderDate().getNano()).as("second resolution").isZero();
            assertThat(order.orderDate())
                    .isAfterOrEqualTo(config.fromInclusive())
                    .isBefore(config.toExclusive());
            assertThat(order.deliveryDate())
                    .as("delivery = order date + 2..5 days")
                    .isAfterOrEqualTo(order.orderDate().toLocalDate().plusDays(2))
                    .isBeforeOrEqualTo(order.orderDate().toLocalDate().plusDays(5));
        }
    }

    @Test
    void g5_itemsReferenceProductsAndSumToTotal() {
        GeneratedData data = PerfDataGenerator.generate(GeneratorConfig.small(SMALL_SEED));
        Map<String, GenProduct> productsById = data.products().stream()
                .collect(Collectors.toMap(GenProduct::id, product -> product));

        for (GenOrder order : data.orders()) {
            assertThat(order.items()).hasSizeBetween(1, 4);
            long sum = 0;
            for (GenItem item : order.items()) {
                assertThat(item.quantity()).isBetween(1, 3);
                assertThat(productsById).as("product %s exists", item.productId())
                        .containsKey(item.productId());
                assertThat(item.price())
                        .as("price = discount price of %s", item.productId())
                        .isEqualTo(productsById.get(item.productId()).discountPrice());
                sum += (long) item.quantity() * item.price();
            }
            assertThat(order.totalPrice()).as("total = sum(quantity * price)").isEqualTo(sum);
        }
    }

    @Test
    void g6_productIdsSequentialAndPricesInRange() {
        GeneratedData data = PerfDataGenerator.generate(GeneratorConfig.small(SMALL_SEED));

        List<String> sortedIds =
                data.products().stream().map(GenProduct::id).sorted().toList();
        List<String> expectedIds = IntStream.rangeClosed(1, data.products().size())
                .mapToObj(i -> "X" + String.format("%05d", i))
                .toList();
        assertThat(sortedIds).containsExactlyElementsOf(expectedIds);

        for (GenProduct product : data.products()) {
            assertThat(product.regularPrice()).isBetween(5_000, 100_000);
            assertThat(product.regularPrice() % 10).as("multiple of 10").isZero();
            assertThat(product.discountPrice() % 10).as("multiple of 10").isZero();
            int floor = (int) ((7L * product.regularPrice() + 9) / 10);
            assertThat(product.discountPrice())
                    .as("discount in [ceil(0.7 * regular), regular]")
                    .isBetween(floor, product.regularPrice());
        }
    }

    @Test
    void g7_memberIdsUniqueAndFormatted() {
        List<String> ids = PerfDataGenerator.generate(GeneratorConfig.small(SMALL_SEED)).members()
                .stream()
                .map(GenMember::id)
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).allMatch(id -> id.matches("u\\d{6}"));
    }
}
