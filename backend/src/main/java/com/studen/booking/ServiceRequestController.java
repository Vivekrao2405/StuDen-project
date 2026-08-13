package com.studen.booking;

import com.studen.orders.OrderService;
import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-requests")
public class ServiceRequestController {

    private final ServiceRequestService serviceRequestService;
    private final OrderService orderService;

    public ServiceRequestController(ServiceRequestService serviceRequestService, OrderService orderService) {
        this.serviceRequestService = serviceRequestService;
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ServiceRequestResponse> createRequest(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateServiceRequestRequest request) {
        ServiceRequestResponse response = serviceRequestService.createRequest(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public List<ServiceRequestResponse> getMyRequests(@AuthenticationPrincipal UserPrincipal principal) {
        return serviceRequestService.listMyRequests(principal.getId());
    }

    @GetMapping("/incoming")
    public List<ServiceRequestResponse> getIncomingRequests(@AuthenticationPrincipal UserPrincipal principal) {
        return serviceRequestService.listIncomingRequests(principal.getId());
    }

    @GetMapping("/{requestId}")
    public ServiceRequestResponse getRequest(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId) {
        return serviceRequestService.getRequest(principal.getId(), requestId);
    }

    @PostMapping("/{requestId}/accept")
    public ServiceRequestResponse accept(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId) {
        ServiceRequestResponse response = serviceRequestService.acceptRequest(principal.getId(), requestId);
        // Eagerly create the order the instant a request is accepted — no manual "create order"
        // step on the frontend. Orchestrated here rather than inside ServiceRequestService itself
        // so that service stays free of any dependency on the newer orders feature; any exception
        // here propagates normally (not swallowed) so an order-creation failure surfaces rather
        // than silently leaving the request ACCEPTED with no order. getOrCreateOrder is also
        // exposed as its own endpoint, so a client can self-heal by retrying if this ever fails.
        orderService.getOrCreateOrder(principal.getId(), requestId);
        return response;
    }

    @PostMapping("/{requestId}/reject")
    public ServiceRequestResponse reject(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId, @Valid @RequestBody(required = false) RejectServiceRequestRequest request) {
        return serviceRequestService.rejectRequest(principal.getId(), requestId, request);
    }
}
