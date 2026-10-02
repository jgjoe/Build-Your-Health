package io.github.jgjoe.byh.perf;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Fixed-seed generator for the index/execution-plan measurement data (RQ-F-011).
 *
 * <p>Determinism rules: the only randomness source is {@link SplittableRandom} seeded from the
 * config; no wall clock, no {@code Math.random}, no iteration over unordered collections. The
 * generated order list is sorted by (orderDate, userId) - that is the load order.</p>
 */
public final class PerfDataGenerator {

    private static final String[] MANUFACTURERS = {
            "동원샘물", "제주삼다수", "롯데칠성", "광동제약", "웅진식품",
            "남양유업", "매일유업", "서울우유", "빙그레", "hy",
            "오리온", "롯데제과", "크라운제과", "해태제과", "농심",
            "삼양식품", "오뚜기", "CJ제일제당", "대상", "샘표",
            "청정원", "풀무원", "한살림", "아워홈", "신세계푸드",
            "이마트", "홈플러스", "코스트코", "마켓컬리", "오아시스마켓",
            "컬리", "닥터유", "칼로바이", "잠백이", "마이프로틴",
            "헬스뷰", "뉴트리코어", "고려은단", "종근당건강", "안국건강",
            "한미약품", "유한양행", "동아제약", "광동헬스", "일양약품",
            "노바렉스", "에스티팜", "콜마비앤에이치", "서흥", "한풍제약"};

    private static final String[] ADDRESSES = {
            "서울시 중구 세종대로 1",
            "서울시 강남구 테헤란로 152",
            "서울시 송파구 올림픽로 300",
            "서울시 마포구 월드컵북로 396",
            "경기도 성남시 분당구 판교역로 235",
            "경기도 수원시 영통구 광교로 145",
            "인천시 연수구 송도과학로 32",
            "부산시 해운대구 센텀중앙로 79",
            "대전시 유성구 대학로 99",
            "대구시 수성구 동대구로 111"};

    private static final int MIN_PRICE = 5_000;
    private static final int MAX_PRICE = 100_000;
    private static final int PRICE_STEP = 10;
    private static final int PRICE_LEVELS = (MAX_PRICE - MIN_PRICE) / PRICE_STEP + 1;

    private PerfDataGenerator() {
    }

    public static GeneratedData generate(GeneratorConfig config) {
        SplittableRandom random = new SplittableRandom(config.seed());
        List<GenProduct> products = generateProducts(config, random);
        List<GenMember> members = new ArrayList<>(config.memberCount());
        List<GenOrder> orders = new ArrayList<>(safeOrderCapacity(config.orderCount()));
        generateMembersAndOrders(config, random, products, members, orders);
        orders.sort(Comparator.comparing(GenOrder::orderDate).thenComparing(GenOrder::userId));
        return new GeneratedData(members, products, orders);
    }

    private static List<GenProduct> generateProducts(GeneratorConfig config, SplittableRandom random) {
        List<GenProduct> products = new ArrayList<>(config.productCount());
        for (int sequence = 1; sequence <= config.productCount(); sequence++) {
            int regularPrice = MIN_PRICE + PRICE_STEP * random.nextInt(PRICE_LEVELS);
            // Smallest multiple of 10 that is >= 70% of the regular price.
            int minimumDiscount = ceilToTen(ceilTimes(regularPrice, 7, 10));
            int discountLevels = (regularPrice - minimumDiscount) / PRICE_STEP;
            int discountPrice = minimumDiscount + PRICE_STEP * random.nextInt(discountLevels + 1);
            String manufacturer = MANUFACTURERS[random.nextInt(MANUFACTURERS.length)];
            products.add(new GenProduct(String.format(Locale.ROOT, "X%05d", sequence),
                    String.format(Locale.ROOT, "perf product %05d", sequence),
                    regularPrice, discountPrice, manufacturer));
        }
        return products;
    }

    private static void generateMembersAndOrders(GeneratorConfig config, SplittableRandom random,
                                                 List<GenProduct> products, List<GenMember> members,
                                                 List<GenOrder> orders) {
        long windowSeconds = Duration.between(config.fromInclusive(), config.toExclusive()).getSeconds();
        int sequence = 0;
        for (Tier tier : config.tiers()) {
            for (int i = 0; i < tier.members(); i++) {
                sequence++;
                GenMember member = new GenMember(GeneratorConfig.memberId(sequence),
                        String.format(Locale.ROOT, "member%06d", sequence), tier.name());
                members.add(member);
                for (int j = 0; j < tier.ordersPerMember(); j++) {
                    orders.add(generateOrder(config, random, products, member, windowSeconds));
                }
            }
        }
    }

    private static GenOrder generateOrder(GeneratorConfig config, SplittableRandom random,
                                          List<GenProduct> products, GenMember member, long windowSeconds) {
        long offsetSeconds = random.nextLong(windowSeconds);
        LocalDateTime orderDate = config.fromInclusive().plusSeconds(offsetSeconds);
        LocalDate deliveryDate = orderDate.toLocalDate().plusDays(2 + random.nextInt(4));
        int itemCount = 1 + random.nextInt(4);
        List<GenItem> items = new ArrayList<>(itemCount);
        long totalPrice = 0;
        for (int i = 0; i < itemCount; i++) {
            GenProduct product = products.get(random.nextInt(products.size()));
            int quantity = 1 + random.nextInt(3);
            items.add(new GenItem(product.id(), quantity, product.discountPrice()));
            totalPrice += (long) quantity * product.discountPrice();
        }
        String zipcode = String.format(Locale.ROOT, "%05d", random.nextInt(100_000));
        return new GenOrder(member.id(), orderDate, deliveryDate, totalPrice, member.name(),
                ADDRESSES[random.nextInt(ADDRESSES.length)], zipcode, items);
    }

    /** ceil(value * numerator / denominator) for positive values. */
    private static int ceilTimes(int value, int numerator, int denominator) {
        return (int) (((long) value * numerator + denominator - 1) / denominator);
    }

    private static int ceilToTen(int value) {
        return (value + PRICE_STEP - 1) / PRICE_STEP * PRICE_STEP;
    }

    private static int safeOrderCapacity(long orderCount) {
        return (int) Math.min(orderCount, Integer.MAX_VALUE);
    }
}
