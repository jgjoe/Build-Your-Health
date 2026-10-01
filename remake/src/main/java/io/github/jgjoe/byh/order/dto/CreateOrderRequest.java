package io.github.jgjoe.byh.order.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Order creation request. The client may send a price per line, but it is ignored:
 * the total is always computed from the current DB prices (RQ-F-007).
 */
public record CreateOrderRequest(String recipientName,
                                 LocalDate deliveryDate,
                                 String address,
                                 String zipcode,
                                 List<OrderItemRequest> items) {

    public record OrderItemRequest(String productId, Integer quantity, Integer price) {
    }
}
