package com.studen.booking;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.RateLimitExceededException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.marketplace.ServiceListing;
import com.studen.marketplace.ServiceListingRepository;
import com.studen.marketplace.ServiceStatus;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceRequestService {

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

        notifier.notify(provider.getId(), "New service request for " + service.getTitle());
        notifier.notify(requesterId, "Your service request has been sent.");

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
