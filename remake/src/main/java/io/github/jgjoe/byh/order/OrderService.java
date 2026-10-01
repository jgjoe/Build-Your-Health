package io.github.jgjoe.byh.order;

import io.github.jgjoe.byh.common.InvalidOrderException;
import io.github.jgjoe.byh.common.InvalidParameterException;
import io.github.jgjoe.byh.common.NotFoundException;
import io.github.jgjoe.byh.common.PageResponse;
import io.github.jgjoe.byh.order.dto.CreateOrderRequest;
import io.github.jgjoe.byh.order.dto.CreateOrderResponse;
import io.github.jgjoe.byh.order.dto.CreateOrderRequest.OrderItemRequest;
import io.github.jgjoe.byh.order.dto.OrderSummary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Order creation, history and detail (RQ-F-006..RQ-F-010). */
@Service
public class OrderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderMapper orderMapper;

    public OrderService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * Stores the order header and every line in one transaction. The total is computed from
     * the current DB prices; anything the client sent as a price is ignored. Missing products
     * fail the request before anything is written (RQ-F-007).
     */
    @Transactional
    public CreateOrderResponse createOrder(String userId, CreateOrderRequest request) {
        validate(request);

        List<String> productIds = distinctProductIds(request.items());
        List<ProductPrice> prices = orderMapper.findPricesByProductIds(productIds);
        Map<String, Integer> priceByProductId = new HashMap<>();
        for (ProductPrice price : prices) {
            priceByProductId.put(price.getProductId(), price.getDiscountPrice());
        }
        if (priceByProductId.size() != productIds.size()) {
            throw new InvalidOrderException("존재하지 않는 상품이 포함되어 있습니다.");
        }

        long totalPrice = 0;
        for (OrderItemRequest item : request.items()) {
            totalPrice += (long) priceByProductId.get(item.productId()) * item.quantity();
        }

        OrderInsert order = new OrderInsert();
        order.setUserId(userId);
        order.setDeliveryDate(request.deliveryDate());
        order.setTotalPrice(totalPrice);
        order.setRecipientName(request.recipientName());
        order.setAddress(request.address());
        order.setZipcode(request.zipcode());
        orderMapper.insertOrder(order);

        for (OrderItemRequest item : request.items()) {
            orderMapper.insertOrderItem(order.getOrderId(), item.productId(), item.quantity(),
                    priceByProductId.get(item.productId()));
        }
        return new CreateOrderResponse(order.getOrderId(), totalPrice);
    }

    /** One count query plus one paged join query, whatever the number of orders (RQ-F-009). */
    public PageResponse<OrderSummary> findOrders(String userId, int page, int size) {
        validatePage(page, size);
        long total = orderMapper.countOrdersByUserId(userId);
        List<OrderHistoryRow> rows = total == 0
                ? List.of()
                : orderMapper.findOrdersByUserId(userId, ((long) page - 1) * size, size);
        return new PageResponse<>(rows.stream().map(OrderService::toSummary).toList(), page, size, total);
    }

    /** Order detail is looked up by order id and member id, so a foreign order is not found (RQ-F-010). */
    public OrderSummary findOrder(String userId, long orderId) {
        return orderMapper.findOrderByIdAndUserId(orderId, userId).stream()
                .findFirst()
                .map(OrderService::toSummary)
                .orElseThrow(() -> new NotFoundException("주문을 찾을 수 없습니다."));
    }

    private static void validate(CreateOrderRequest request) {
        if (request == null) {
            throw new InvalidOrderException("주문 요청이 비어 있습니다.");
        }
        if (isBlank(request.recipientName())) {
            throw new InvalidOrderException("받는 사람을 입력해야 합니다.");
        }
        if (request.deliveryDate() == null) {
            throw new InvalidOrderException("배송 희망일을 입력해야 합니다.");
        }
        if (isBlank(request.address())) {
            throw new InvalidOrderException("배송 주소를 입력해야 합니다.");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new InvalidOrderException("주문 상품이 하나 이상 필요합니다.");
        }
        for (OrderItemRequest item : request.items()) {
            if (item == null || isBlank(item.productId())) {
                throw new InvalidOrderException("상품 번호가 올바르지 않습니다.");
            }
            if (item.quantity() == null || item.quantity() < 1) {
                throw new InvalidOrderException("수량은 1 이상이어야 합니다.");
            }
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 1) {
            throw new InvalidParameterException("page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidParameterException("size는 1 이상 100 이하여야 합니다.");
        }
    }

    private static List<String> distinctProductIds(List<OrderItemRequest> items) {
        Set<String> productIds = new LinkedHashSet<>();
        for (OrderItemRequest item : items) {
            productIds.add(item.productId());
        }
        return new ArrayList<>(productIds);
    }

    private static OrderSummary toSummary(OrderHistoryRow row) {
        List<OrderSummary.OrderLine> lines = row.getLines() == null
                ? List.of()
                : row.getLines().stream()
                        .map(line -> new OrderSummary.OrderLine(line.getProductId(), line.getProductName(),
                                line.getQuantity() == null ? 0 : line.getQuantity(),
                                line.getPrice() == null ? 0 : line.getPrice()))
                        .toList();
        return new OrderSummary(row.getOrderId(), row.getOrderDate(), row.getDeliveryDate(),
                row.getTotalPrice() == null ? 0 : row.getTotalPrice(), row.getRecipientName(), row.getAddress(), lines);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
