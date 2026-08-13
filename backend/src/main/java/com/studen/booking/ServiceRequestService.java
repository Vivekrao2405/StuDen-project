package com.studen.booking;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.RateLimitExceededException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.marketplace.ServiceListing;
import com.studen.marketplace.ServiceListingRepository;
import com.studen.marketplace.ServiceStatus;
import com.studen.notification.NotificationType;
import com.studen.notification.Notifier;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceRequestService {

    private static final Logger log = LoggerFactory.getLogger(ServiceRequestService.class);

    private final ServiceRequestRepository serviceRequestRepository;
    private final ServiceListingRepository serviceListingRepository;
    private final StudentPortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final Notifier notifier;
    private final int maxRequestsPerWindow;
    private final int rateLimitWindowMinutes;

    public ServiceRequestService(ServiceRequestRepository serviceRequestRepository,
            ServiceListingRepository serviceListingRepository, StudentPortfolioRepository portfolioRepository,
            UserRepository userRepository, Notifier notifier,
            @Value("${app.booking.max-requests-per-window}") int maxRequestsPerWindow,
            @Value("${app.booking.rate-limit-window-minutes}") int rateLimitWindowMinutes) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.serviceListingRepository = serviceListingRepository;
        this.portfolioRepository = portfolioRepository;
        this.userRepository = userRepository;
        this.notifier = notifier;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.rateLimitWindowMinutes = rateLimitWindowMinutes;
    }

    @Transactional
    public ServiceRequestResponse createRequest(UUID requesterId, CreateServiceRequestRequest request) {
        ServiceListing service = serviceListingRepository.findById(request.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        // 404, not 403/400, for a draft/inactive/unavailable service — same "don't reveal
        // existence" convention as ShareService.getPublicService. A service this requester
        // couldn't otherwise see on the public detail page must behave identically here.
        StudentPortfolio providerPortfolio = service.getPortfolio();
        User provider = providerPortfolio.getUser();
        if (service.getStatus() != ServiceStatus.ACTIVE || !service.isAvailable() || !provider.isActive()) {
            throw new ResourceNotFoundException("Service not found");
        }

        if (provider.getId().equals(requesterId)) {
            throw new InvalidRequestException("You can't request your own service.");
        }

        if (serviceRequestRepository.countByRequesterIdAndCreatedAtAfter(requesterId,
                Instant.now().minus(Duration.ofMinutes(rateLimitWindowMinutes))) >= maxRequestsPerWindow) {
            throw new RateLimitExceededException("You're sending requests too quickly. Please wait a bit and try again.");
        }

        if (serviceRequestRepository.existsByServiceIdAndRequesterIdAndStatus(service.getId(), requesterId,
                ServiceRequestStatus.PENDING)) {
            throw new ConflictException("You already have a pending request for this service.");
        }

        User requester = userRepository.getReferenceById(requesterId);

        ServiceRequest serviceRequest = new ServiceRequest(service, requester, provider, request.description().trim());
        serviceRequest.setRequestedDeliveryDate(request.requestedDeliveryDate());
        serviceRequest.setProposedBudget(request.proposedBudget());
        serviceRequest.setLinks(toLinkEntities(request.links()));
        serviceRequest = serviceRequestRepository.save(serviceRequest);

        notifier.notify(provider.getId(), NotificationType.NEW_SERVICE_REQUEST,
                "New service request for " + service.getTitle(), serviceRequest.getId());
        // Self-confirmation to the requester deep-links back to the same request they just sent —
        // there's no distinct MVP type for "you sent this", so it reuses NEW_SERVICE_REQUEST.
        notifier.notify(requesterId, NotificationType.NEW_SERVICE_REQUEST,
                "Your service request has been sent.", serviceRequest.getId());

        StudentPortfolio requesterPortfolio = portfolioRepository.findByUserId(requesterId).orElse(null);
        return ServiceRequestResponse.from(serviceRequest, requesterPortfolio, providerPortfolio);
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> listMyRequests(UUID userId) {
        List<ServiceRequest> requests = serviceRequestRepository.findAllByRequesterIdOrderByCreatedAtDesc(userId);
        return toResponses(requests);
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> listIncomingRequests(UUID userId) {
        List<ServiceRequest> requests = serviceRequestRepository.findAllByProviderIdOrderByCreatedAtDesc(userId);
        return toResponses(requests);
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse getRequest(UUID userId, UUID requestId) {
        ServiceRequest request = serviceRequestRepository.findByIdAndRequesterIdOrProviderId(requestId, userId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Service request not found"));
        return toResponses(List.of(request)).get(0);
    }

    @Transactional
    public ServiceRequestResponse acceptRequest(UUID providerId, UUID requestId) {
        ServiceRequest request = findOwnIncomingRequest(providerId, requestId);

        // Only accept still-eligible services — never let a stale PENDING request against a
        // deleted/draft/deactivated listing be "accepted" into a commitment the provider can't
        // honor. Reject has no equivalent gate: closing out a request against a dead service is
        // always safe.
        if (request.getService() == null || request.getService().getStatus() != ServiceStatus.ACTIVE) {
            throw new ConflictException("This service is no longer available to accept requests.");
        }

        int updated = serviceRequestRepository.acceptIfPending(requestId, Instant.now());
        if (updated == 0) {
            // Someone else already resolved it (or it never was PENDING) — caught here rather than
            // earlier so the check is against the database's current truth, not a value read
            // moments ago in this same method, closing the race window described in the plan.
            throw new ConflictException("This request is no longer pending.");
        }

        log.info("ServiceRequest {} PENDING -> ACCEPTED by provider {}", requestId, providerId);

        ServiceRequest updatedRequest = serviceRequestRepository.findById(requestId).orElseThrow();
        notifier.notify(updatedRequest.getRequester().getId(), NotificationType.REQUEST_ACCEPTED,
                "Your request for " + updatedRequest.getServiceTitleSnapshot() + " was accepted by "
                        + updatedRequest.getProvider().getFullName() + ".",
                updatedRequest.getId());

        return toResponses(List.of(updatedRequest)).get(0);
    }

    @Transactional
    public ServiceRequestResponse rejectRequest(UUID providerId, UUID requestId, RejectServiceRequestRequest body) {
        ServiceRequest request = findOwnIncomingRequest(providerId, requestId);
        String reason = body == null || body.reason() == null || body.reason().isBlank() ? null : body.reason().trim();

        int updated = serviceRequestRepository.rejectIfPending(requestId, Instant.now(), reason);
        if (updated == 0) {
            throw new ConflictException("This request is no longer pending.");
        }

        log.info("ServiceRequest {} PENDING -> REJECTED by provider {}", requestId, providerId);

        ServiceRequest updatedRequest = serviceRequestRepository.findById(requestId).orElseThrow();
        notifier.notify(updatedRequest.getRequester().getId(), NotificationType.REQUEST_REJECTED,
                "Your request for " + updatedRequest.getServiceTitleSnapshot() + " was not accepted by "
                        + updatedRequest.getProvider().getFullName() + ".",
                updatedRequest.getId());

        return toResponses(List.of(updatedRequest)).get(0);
    }

    // Covers "request doesn't exist", "you're not this request's provider", and "you're the
    // requester trying to manage your own request" all with one scoped query — an id that exists
    // but isn't yours returns empty, same as ServiceListingService.findOwnService, so the caller
    // maps it straight to a 404 without leaking which case applied.
    private ServiceRequest findOwnIncomingRequest(UUID providerId, UUID requestId) {
        return serviceRequestRepository.findByIdAndProviderId(requestId, providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Service request not found"));
    }

    // Batch-fetches every distinct requester's and provider's portfolio in (at most) one query
    // each, instead of one findByUserId per request row.
    private List<ServiceRequestResponse> toResponses(List<ServiceRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = new ArrayList<>();
        requests.forEach(r -> {
            userIds.add(r.getRequester().getId());
            userIds.add(r.getProvider().getId());
        });
        Map<UUID, StudentPortfolio> portfolioByUserId = portfolioRepository.findAllByUserIdIn(userIds.stream().distinct().toList())
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        return requests.stream()
                .map(r -> ServiceRequestResponse.from(r,
                        portfolioByUserId.get(r.getRequester().getId()),
                        portfolioByUserId.get(r.getProvider().getId())))
                .toList();
    }

    private List<ServiceRequestLink> toLinkEntities(List<ServiceRequestLinkRequest> links) {
        if (links == null) {
            return new ArrayList<>();
        }
        return links.stream()
                .map(l -> new ServiceRequestLink(l.label().trim(), l.url().trim()))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
