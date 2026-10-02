package io.github.jgjoe.byh.perf;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Generated order with its lines; {@code totalPrice} is the sum of quantity * price over {@code items}. */
public record GenOrder(String userId, LocalDateTime orderDate, LocalDate deliveryDate, long totalPrice,
                       String recipientName, String address, String zipcode, List<GenItem> items) {

    public GenOrder {
        items = List.copyOf(items);
    }
}
