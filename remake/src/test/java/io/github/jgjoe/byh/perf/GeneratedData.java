package io.github.jgjoe.byh.perf;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** One generated data set, in generation (and load) order. */
public record GeneratedData(List<GenMember> members, List<GenProduct> products, List<GenOrder> orders) {

    public GeneratedData {
        members = List.copyOf(members);
        products = List.copyOf(products);
        orders = List.copyOf(orders);
    }

    /** SHA-256 hex over a canonical serialization of every member, product, order and item, in list order. */
    public String digest() {
        MessageDigest sha256 = sha256();
        for (GenMember member : members) {
            update(sha256, "M|" + member.id() + "|" + member.name() + "|" + member.tier() + "\n");
        }
        for (GenProduct product : products) {
            update(sha256, "P|" + product.id() + "|" + product.name() + "|" + product.regularPrice()
                    + "|" + product.discountPrice() + "|" + product.manufacturer() + "\n");
        }
        for (GenOrder order : orders) {
            // LocalDateTime/LocalDate toString are ISO-8601 and locale-independent.
            update(sha256, "O|" + order.userId() + "|" + order.orderDate() + "|" + order.deliveryDate()
                    + "|" + order.totalPrice() + "|" + order.recipientName() + "|" + order.address()
                    + "|" + order.zipcode() + "\n");
            for (GenItem item : order.items()) {
                update(sha256, "I|" + item.productId() + "|" + item.quantity() + "|" + item.price() + "\n");
            }
        }
        return HexFormat.of().formatHex(sha256.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    private static void update(MessageDigest digest, String line) {
        digest.update(line.getBytes(StandardCharsets.UTF_8));
    }
}
