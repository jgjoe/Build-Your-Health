package io.github.jgjoe.byh.order.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** One order of the history/detail API, lines included. */
public record OrderSummary(Long orderId,
                           LocalDateTime orderDate,
                           LocalDate deliveryDate,
                           long totalPrice,
                           String recipientName,
                           String address,
                           List<OrderLine> lines) {

    public record OrderLine(String productId, String productName, int quantity, int price) {
    }
}
