package io.github.jgjoe.byh.order;

import io.github.jgjoe.byh.auth.LoginMember;
import io.github.jgjoe.byh.common.PageResponse;
import io.github.jgjoe.byh.order.dto.CreateOrderRequest;
import io.github.jgjoe.byh.order.dto.CreateOrderResponse;
import io.github.jgjoe.byh.order.dto.OrderSummary;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order API (RQ-F-006..RQ-F-010). Authentication is enforced by {@code LoginInterceptor}
 * for every path under {@code /api/orders}.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse create(@RequestBody CreateOrderRequest request, HttpSession session) {
        return orderService.createOrder(LoginMember.memberId(session), request);
    }

    @GetMapping
    public PageResponse<OrderSummary> history(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              HttpSession session) {
        return orderService.findOrders(LoginMember.memberId(session), page, size);
    }

    @GetMapping("/{orderId}")
    public OrderSummary detail(@PathVariable long orderId, HttpSession session) {
        return orderService.findOrder(LoginMember.memberId(session), orderId);
    }
}
