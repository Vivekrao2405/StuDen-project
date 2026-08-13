package com.studen.orders;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// No class-level @RequestMapping, same reasoning as ConversationController: getOrCreateOrder is a
// sub-resource of /api/v1/service-requests (implemented here since it's this feature's own
// get-or-create logic), while everything else lives under /api/v1/orders.
@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/v1/service-requests/{requestId}/order")
    public OrderResponse getOrCreateOrder(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID requestId) {
        return orderService.getOrCreateOrder(principal.getId(), requestId);
    }

    @GetMapping("/api/v1/orders")
    public List<OrderResponse> listMyOrders(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) OrderStatus status) {
        return orderService.listMyOrders(principal.getId(), status);
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public OrderResponse getOrder(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID orderId) {
        return orderService.getOrder(principal.getId(), orderId);
    }

    @PostMapping("/api/v1/orders/{orderId}/submit")
    public OrderResponse submitWork(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID orderId,
            @Valid @RequestBody SubmitWorkRequest request) {
        return orderService.submitWork(principal.getId(), orderId, request);
    }

    @PostMapping("/api/v1/orders/{orderId}/complete")
    public OrderResponse completeOrder(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID orderId) {
        return orderService.completeOrder(principal.getId(), orderId);
    }

    @PostMapping("/api/v1/orders/{orderId}/cancel")
    public OrderResponse cancelOrder(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID orderId,
            @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancelOrder(principal.getId(), orderId, request);
    }
}
