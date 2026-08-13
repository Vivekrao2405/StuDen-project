package com.studen.orders;

import com.studen.booking.ServiceRequest;
import com.studen.booking.ServiceRequestRepository;
import com.studen.booking.ServiceRequestStatus;
import com.studen.common.exception.ConflictException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.notification.NotificationType;
import com.studen.notification.Notifier;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final WorkOrderRepository workOrderRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final StudentPortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final Notifier notifier;

    public OrderService(WorkOrderRepository workOrderRepository, ServiceRequestRepository serviceRequestRepository,
            StudentPortfolioRepository portfolioRepository, UserRepository userRepository, Notifier notifier) {
        this.workOrderRepository = workOrderRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.portfolioRepository = portfolioRepository;
        this.userRepository = userRepository;
        this.notifier = notifier;
    }

    // Deliberately NOT @Transactional — identical reasoning to
    // ConversationService.getOrCreateConversation: ensureOrderExists's insert and the final
    // findByServiceRequestId read below each run in their own short, independent transaction, so
    // a losing concurrent insert (e.g. two tabs both hitting this right after acceptance) rolls
    // back only its own tiny transaction, and the subsequent read always sees the winner's
    // already-committed row.
    public OrderResponse getOrCreateOrder(UUID userId, UUID serviceRequestId) {
        ServiceRequest request = serviceRequestRepository.findByIdAndRequesterIdOrProviderId(serviceRequestId, userId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Service request not found"));

        if (request.getStatus() != ServiceRequestStatus.ACCEPTED) {
            throw new ConflictException("An order can only be created for an accepted request.");
        }

        ensureOrderExists(request);

        WorkOrder order = workOrderRepository.findByServiceRequestId(serviceRequestId)
                .orElseThrow(() -> new IllegalStateException("Order should exist after ensureOrderExists"));
        return toResponse(order);
    }

    private void ensureOrderExists(ServiceRequest request) {
        if (workOrderRepository.findByServiceRequestId(request.getId()).isPresent()) {
            return;
        }
        ServiceRequest requestRef = serviceRequestRepository.getReferenceById(request.getId());
        User providerRef = userRepository.getReferenceById(request.getProvider().getId());
        User requesterRef = userRepository.getReferenceById(request.getRequester().getId());
        try {
            workOrderRepository.save(new WorkOrder(requestRef, providerRef, requesterRef));
            log.info("WorkOrder created for ServiceRequest {}", request.getId());
        } catch (DataIntegrityViolationException ignored) {
            // Lost the race to a concurrent creation trigger (accept's own eager call, or another
            // "View Order" click) — the winner's row already exists (unique constraint on
            // service_request_id), and the caller's subsequent findByServiceRequestId picks it up.
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMyOrders(UUID userId, OrderStatus statusFilter) {
        List<WorkOrder> orders = workOrderRepository.findAllByRequesterIdOrProviderId(userId);
        if (statusFilter != null) {
            orders = orders.stream().filter(o -> o.getStatus() == statusFilter).toList();
        }
        return toResponses(orders);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID userId, UUID orderId) {
        WorkOrder order = workOrderRepository.findByIdAndRequesterIdOrProviderId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse submitWork(UUID providerId, UUID orderId, SubmitWorkRequest request) {
        WorkOrder order = workOrderRepository.findByIdAndProviderId(orderId, providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        String link = request.link() == null || request.link().isBlank() ? null : request.link().trim();
        int updated = workOrderRepository.submitIfInProgress(orderId, Instant.now(), request.description().trim(), link);
        if (updated == 0) {
            throw new ConflictException("This order is no longer in progress.");
        }

        log.info("WorkOrder {} IN_PROGRESS -> WORK_SUBMITTED by provider {}", orderId, providerId);

        WorkOrder updatedOrder = workOrderRepository.findByIdAndProviderId(orderId, providerId).orElseThrow();
        notifier.notify(updatedOrder.getRequester().getId(), NotificationType.WORK_SUBMITTED,
                "Work has been submitted for " + updatedOrder.getServiceRequest().getServiceTitleSnapshot() + ".",
                updatedOrder.getId());

        return toResponse(updatedOrder);
    }

    @Transactional
    public OrderResponse completeOrder(UUID requesterId, UUID orderId) {
        WorkOrder order = workOrderRepository.findByIdAndRequesterId(orderId, requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        int updated = workOrderRepository.completeIfSubmitted(orderId, Instant.now());
        if (updated == 0) {
            throw new ConflictException("This order isn't awaiting completion.");
        }

        log.info("WorkOrder {} WORK_SUBMITTED -> COMPLETED by requester {}", orderId, requesterId);

        WorkOrder updatedOrder = workOrderRepository.findByIdAndRequesterId(orderId, requesterId).orElseThrow();
        notifier.notify(updatedOrder.getProvider().getId(), NotificationType.ORDER_COMPLETED,
                "Your work for " + updatedOrder.getServiceRequest().getServiceTitleSnapshot() + " was marked completed.",
                updatedOrder.getId());

        return toResponse(updatedOrder);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID userId, UUID orderId, CancelOrderRequest body) {
        WorkOrder order = workOrderRepository.findByIdAndRequesterIdOrProviderId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        String reason = body == null || body.reason() == null || body.reason().isBlank() ? null : body.reason().trim();

        int updated = workOrderRepository.cancelIfInProgress(orderId, Instant.now(), reason);
        if (updated == 0) {
            throw new ConflictException("This order can no longer be cancelled.");
        }

        log.info("WorkOrder {} IN_PROGRESS -> CANCELLED by {}", orderId, userId);

        WorkOrder updatedOrder = workOrderRepository.findByIdAndRequesterIdOrProviderId(orderId, userId).orElseThrow();
        UUID otherParticipantId = updatedOrder.getRequester().getId().equals(userId)
                ? updatedOrder.getProvider().getId()
                : updatedOrder.getRequester().getId();
        notifier.notify(otherParticipantId, NotificationType.ORDER_CANCELLED,
                "The order for " + updatedOrder.getServiceRequest().getServiceTitleSnapshot() + " was cancelled.",
                updatedOrder.getId());

        return toResponse(updatedOrder);
    }

    // Batch-fetches every distinct requester's and provider's portfolio in (at most) one query
    // each, same idiom as ServiceRequestService.toResponses/ConversationService.listConversations.
    private List<OrderResponse> toResponses(List<WorkOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = new ArrayList<>();
        orders.forEach(o -> {
            userIds.add(o.getRequester().getId());
            userIds.add(o.getProvider().getId());
        });
        Map<UUID, StudentPortfolio> portfolioByUserId = portfolioRepository.findAllByUserIdIn(userIds.stream().distinct().toList())
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        return orders.stream()
                .map(o -> OrderResponse.from(o,
                        portfolioByUserId.get(o.getRequester().getId()),
                        portfolioByUserId.get(o.getProvider().getId())))
                .toList();
    }

    private OrderResponse toResponse(WorkOrder order) {
        StudentPortfolio requesterPortfolio = portfolioRepository.findByUserId(order.getRequester().getId()).orElse(null);
        StudentPortfolio providerPortfolio = portfolioRepository.findByUserId(order.getProvider().getId()).orElse(null);
        return OrderResponse.from(order, requesterPortfolio, providerPortfolio);
    }
}
