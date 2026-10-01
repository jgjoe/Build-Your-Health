package io.github.jgjoe.byh.order.dto;

/** Order creation response. */
public record CreateOrderResponse(Long orderId, long totalPrice) {
}
