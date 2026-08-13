package com.studen.marketplace;

import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import com.studen.showcase.Project;
import com.studen.showcase.ProjectMedia;
import com.studen.showcase.ProjectMediaRepository;
import com.studen.showcase.ProjectMediaType;
import com.studen.showcase.ProjectRepository;
import com.studen.showcase.ProjectVisibility;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.storage.ImageValidator;
import com.studen.storage.MediaStorageService;
import com.studen.storage.VideoUploadResult;
import com.studen.storage.VideoValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ServiceListingService {

    private final ServiceListingRepository serviceListingRepository;
    private final ServiceMediaRepository serviceMediaRepository;
    private final StudentPortfolioRepository portfolioRepository;
    private final SkillRepository skillRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMediaRepository projectMediaRepository;
    private final ImageValidator imageValidator;
    private final VideoValidator videoValidator;
    private final MediaStorageService mediaStorageService;
    private final int maxServicesPerStudent;
    private final int maxImagesPerService;
    private final int maxVideosPerService;

    public ServiceListingService(ServiceListingRepository serviceListingRepository,
            ServiceMediaRepository serviceMediaRepository, StudentPortfolioRepository portfolioRepository,
            SkillRepository skillRepository, ProjectRepository projectRepository,
            ProjectMediaRepository projectMediaRepository, ImageValidator imageValidator,
            VideoValidator videoValidator, MediaStorageService mediaStorageService,
            @Value("${app.service.max-services-per-student}") int maxServicesPerStudent,
            @Value("${app.service.max-images-per-service}") int maxImagesPerService,
            @Value("${app.service.max-videos-per-service}") int maxVideosPerService) {
        this.serviceListingRepository = serviceListingRepository;
        this.serviceMediaRepository = serviceMediaRepository;
        this.portfolioRepository = portfolioRepository;
        this.skillRepository = skillRepository;
        this.projectRepository = projectRepository;
        this.projectMediaRepository = projectMediaRepository;
        this.imageValidator = imageValidator;
        this.videoValidator = videoValidator;
        this.mediaStorageService = mediaStorageService;
        this.maxServicesPerStudent = maxServicesPerStudent;
        this.maxImagesPerService = maxImagesPerService;
        this.maxVideosPerService = maxVideosPerService;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> listMyServices(UUID userId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        List<ServiceListing> services = serviceListingRepository.findAllByPortfolioIdOrderByCreatedAtDesc(portfolio.getId());
        if (services.isEmpty()) {
            return List.of();
        }

        List<UUID> serviceIds = services.stream().map(ServiceListing::getId).toList();
        Map<UUID, List<ServiceMedia>> mediaByServiceId = serviceMediaRepository
                .findAllByServiceIdInOrderByDisplayOrderAsc(serviceIds).stream()
                .collect(Collectors.groupingBy(m -> m.getService().getId()));

        List<UUID> allProjectIds = services.stream()
                .flatMap(s -> s.getLinkedProjects().stream().map(Project::getId))
                .distinct()
                .toList();
        Map<UUID, List<ProjectMedia>> projectMediaByProjectId = allProjectIds.isEmpty()
                ? Map.of()
                : projectMediaRepository.findAllByProjectIdInOrderByDisplayOrderAsc(allProjectIds).stream()
                        .collect(Collectors.groupingBy(m -> m.getProject().getId()));

        return services.stream()
                .map(service -> {
                    List<ServiceMedia> media = mediaByServiceId.getOrDefault(service.getId(), List.of());
                    List<ServiceProjectSummaryResponse> linkedProjects = service.getLinkedProjects().stream()
                            .map(p -> ServiceProjectSummaryResponse.from(p, projectMediaByProjectId.getOrDefault(p.getId(), List.of())))
                            .toList();
                    return ServiceResponse.from(service, media, linkedProjects);
                })
                .toList();
    }

    @Transactional
    public ServiceResponse createService(UUID userId, ServiceRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);

        if (serviceListingRepository.countByPortfolioId(portfolio.getId()) >= maxServicesPerStudent) {
            throw new InvalidRequestException(
                    "You've reached the maximum number of services (" + maxServicesPerStudent + ")");
        }

        ServiceListing service = new ServiceListing(portfolio, request.title(), request.category());
        applyRequest(service, request);
        service = serviceListingRepository.save(service);

        return toResponse(service);
    }

    @Transactional
    public ServiceResponse updateService(UUID userId, UUID serviceId, ServiceRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        ServiceListing service = findOwnService(portfolio.getId(), serviceId);
        boolean wasActive = service.getStatus() == ServiceStatus.ACTIVE;

        applyRequest(service, request);

        // An edit to an already-live listing must not silently leave it in an invalid state
        // (e.g. clearing the price) — re-run the same required-field check /publish uses.
        if (wasActive) {
            validateForPublish(service);
        }

        return toResponse(service);
    }

    @Transactional
    public ServiceResponse publishService(UUID userId, UUID serviceId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        ServiceListing service = findOwnService(portfolio.getId(), serviceId);

        validateForPublish(service);
        service.setStatus(ServiceStatus.ACTIVE);

        return toResponse(service);
    }

    @Transactional
    public ServiceResponse deactivateService(UUID userId, UUID serviceId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        ServiceListing service = findOwnService(portfolio.getId(), serviceId);

        if (service.getStatus() == ServiceStatus.DRAFT) {
            throw new InvalidRequestException("Publish this service before deactivating it");
        }
        service.setStatus(ServiceStatus.INACTIVE);

        return toResponse(service);
    }

    @Transactional
    public ServiceResponse reactivateService(UUID userId, UUID serviceId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        ServiceListing service = findOwnService(portfolio.getId(), serviceId);

        if (service.getStatus() != ServiceStatus.INACTIVE) {
            throw new InvalidRequestException("Only an inactive service can be reactivated — a draft must be published instead");
        }
        service.setStatus(ServiceStatus.ACTIVE);

        return toResponse(service);
    }

    @Transactional
    public ServiceResponse setAvailability(UUID userId, UUID serviceId, boolean available) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        ServiceListing service = findOwnService(portfolio.getId(), serviceId);
        service.setAvailable(available);
        return toResponse(service);
    }

    @Transactional
    public void deleteService(UUID userId, UUID serviceId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        ServiceListing service = findOwnService(portfolio.getId(), serviceId);

        service.getMedia().forEach(this::deleteFromStorage);
        serviceListingRepository.delete(service);
    }

    /** Called by {@code PortfolioService} when the whole portfolio is deleted, so this student's
     * service media doesn't leak on Cloudinary after the DB rows cascade-delete. Not exposed via
     * any controller — {@code portfolioId} is already verified-owned by the caller. */
    @Transactional
    public void deleteAllForPortfolio(UUID portfolioId) {
        List<ServiceListing> services = serviceListingRepository.findAllByPortfolioIdOrderByCreatedAtDesc(portfolioId);
        services.forEach(service -> service.getMedia().forEach(this::deleteFromStorage));
        serviceListingRepository.deleteAll(services);
    }

    @Transactional
    public ServiceMediaResponse uploadMedia(UUID userId, UUID serviceId, MultipartFile file) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        ServiceListing service = findOwnService(portfolio.getId(), serviceId);

        ProjectMediaType mediaType = detectMediaType(file);
        enforceMediaLimit(service.getId(), mediaType);

        String publicId = mediaPublicId(portfolio.getId(), service.getId());
        int nextOrder = serviceMediaRepository.findAllByServiceIdOrderByDisplayOrderAsc(service.getId()).size();

        ServiceMedia media;
        if (mediaType == ProjectMediaType.IMAGE) {
            imageValidator.validate(file);
            String url = mediaStorageService.upload(publicId, file);
            media = new ServiceMedia(service, ProjectMediaType.IMAGE, url, publicId, nextOrder);
        } else {
            videoValidator.validate(file);
            VideoUploadResult result = mediaStorageService.uploadVideo(publicId, file);
            media = new ServiceMedia(service, ProjectMediaType.VIDEO, result.secureUrl(), publicId, nextOrder);
            media.setThumbnailUrl(result.thumbnailUrl());
        }

        if (nextOrder == 0) {
            media.setCover(true);
        }
        media = serviceMediaRepository.save(media);

        return ServiceMediaResponse.from(media);
    }

    @Transactional
    public ServiceResponse removeMedia(UUID userId, UUID serviceId, UUID mediaId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        ServiceListing service = findOwnService(portfolio.getId(), serviceId);
        ServiceMedia media = findOwnMedia(service.getId(), mediaId);

        deleteFromStorage(media);
        serviceMediaRepository.delete(media);

        return toResponse(service);
    }

    @Transactional
    public ServiceResponse reorderMedia(UUID userId, UUID serviceId, UpdateServiceMediaOrderRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        ServiceListing service = findOwnService(portfolio.getId(), serviceId);

        List<ServiceMedia> existing = serviceMediaRepository.findAllByServiceIdOrderByDisplayOrderAsc(service.getId());
        Set<UUID> existingIds = existing.stream().map(ServiceMedia::getId).collect(Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(request.mediaIds());

        if (!existingIds.equals(requestedIds)) {
            throw new InvalidRequestException("The media order must include exactly this service's own media items");
        }

        Map<UUID, ServiceMedia> byId = existing.stream()
                .collect(Collectors.toMap(ServiceMedia::getId, m -> m));
        List<UUID> order = request.mediaIds();
        for (int i = 0; i < order.size(); i++) {
            byId.get(order.get(i)).setDisplayOrder(i);
        }

        return toResponse(service);
    }

    @Transactional
    public ServiceResponse setCoverMedia(UUID userId, UUID serviceId, UUID mediaId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        ServiceListing service = findOwnService(portfolio.getId(), serviceId);
        findOwnMedia(service.getId(), mediaId);

        serviceMediaRepository.findAllByServiceIdOrderByDisplayOrderAsc(service.getId())
                .forEach(m -> m.setCover(m.getId().equals(mediaId)));

        return toResponse(service);
    }

    private void enforceMediaLimit(UUID serviceId, ProjectMediaType mediaType) {
        long count = serviceMediaRepository.countByServiceIdAndMediaType(serviceId, mediaType);
        int max = mediaType == ProjectMediaType.IMAGE ? maxImagesPerService : maxVideosPerService;
        if (count >= max) {
            String label = mediaType == ProjectMediaType.IMAGE ? "images" : "videos";
            throw new InvalidRequestException("You've reached the maximum number of " + label + " (" + max + ") for this service");
        }
    }

    private ProjectMediaType detectMediaType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("A media file is required");
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return ProjectMediaType.IMAGE;
        }
        if (contentType != null && contentType.toLowerCase().startsWith("video/")) {
            return ProjectMediaType.VIDEO;
        }
        throw new InvalidRequestException("Unsupported file type — only images and videos are allowed");
    }

    private void deleteFromStorage(ServiceMedia media) {
        if (media.getMediaType() == ProjectMediaType.VIDEO) {
            mediaStorageService.deleteVideo(media.getPublicId());
        } else {
            mediaStorageService.delete(media.getPublicId());
        }
    }

    private String mediaPublicId(UUID portfolioId, UUID serviceId) {
        return "studen/portfolios/" + portfolioId + "/services/" + serviceId + "/media/" + UUID.randomUUID();
    }

    private void validateForPublish(ServiceListing service) {
        if (service.getDescription() == null || service.getDescription().isBlank()) {
            throw new InvalidRequestException("Add a description before publishing this service");
        }
        if (service.getPriceAmount() == null) {
            throw new InvalidRequestException("Set a starting price before publishing this service");
        }
        if (service.getDeliveryDays() == null) {
            throw new InvalidRequestException("Set an estimated delivery time before publishing this service");
        }
    }

    private void applyRequest(ServiceListing service, ServiceRequest request) {
        service.setTitle(request.title().trim());
        service.setDescription(blankToNull(request.description()));
        service.setCategory(request.category());
        service.setLocation(blankToNull(request.location()));
        service.setCurrency(request.currency() == null ? ServiceCurrency.INR : request.currency());
        service.setPriceAmount(request.priceAmount());
        service.setDeliveryDays(request.deliveryDays());
        service.setAvailable(request.available() == null || request.available());

        Set<UUID> skillIds = request.skillIds() == null ? Set.of() : request.skillIds();
        List<Skill> resolvedSkills = skillRepository.findAllByIdIn(skillIds);
        service.setSkills(new LinkedHashSet<>(resolvedSkills));

        service.setWhatYoullReceive(request.whatYoullReceive() == null
                ? new ArrayList<>()
                : new ArrayList<>(request.whatYoullReceive()));

        List<ServiceLink> links = request.links() == null
                ? new ArrayList<>()
                : request.links().stream()
                        .map(l -> new ServiceLink(l.label().trim(), l.url().trim()))
                        .collect(Collectors.toCollection(ArrayList::new));
        service.setLinks(links);

        applyLinkedProjects(service, request.linkedProjectIds() == null ? Set.of() : request.linkedProjectIds());
    }

    // The IDOR/privacy guard for showcase-project linking: a single query verifies every
    // requested project both belongs to this student's own portfolio and is publicly visible. If
    // the resolved count doesn't match the requested count, at least one id failed one of those
    // checks (another student's project, your own private project, or a nonexistent id).
    private void applyLinkedProjects(ServiceListing service, Set<UUID> requestedProjectIds) {
        if (requestedProjectIds.isEmpty()) {
            service.setLinkedProjects(new LinkedHashSet<>());
            return;
        }
        List<Project> resolved = projectRepository.findAllByIdInAndPortfolioIdAndVisibility(
                new ArrayList<>(requestedProjectIds), service.getPortfolio().getId(), ProjectVisibility.PUBLIC);
        if (resolved.size() != requestedProjectIds.size()) {
            throw new InvalidRequestException("One or more selected showcase projects couldn't be linked");
        }
        service.setLinkedProjects(new LinkedHashSet<>(resolved));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private ServiceResponse toResponse(ServiceListing service) {
        List<ServiceMedia> media = serviceMediaRepository.findAllByServiceIdOrderByDisplayOrderAsc(service.getId());
        List<ServiceProjectSummaryResponse> linkedProjects = resolveLinkedProjectSummaries(service.getLinkedProjects());
        return ServiceResponse.from(service, media, linkedProjects);
    }

    private List<ServiceProjectSummaryResponse> resolveLinkedProjectSummaries(Set<Project> linkedProjects) {
        if (linkedProjects.isEmpty()) {
            return List.of();
        }
        List<UUID> projectIds = linkedProjects.stream().map(Project::getId).toList();
        Map<UUID, List<ProjectMedia>> mediaByProjectId = projectMediaRepository
                .findAllByProjectIdInOrderByDisplayOrderAsc(projectIds).stream()
                .collect(Collectors.groupingBy(m -> m.getProject().getId()));
        return linkedProjects.stream()
                .map(p -> ServiceProjectSummaryResponse.from(p, mediaByProjectId.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    private StudentPortfolio findOwnPortfolio(UUID userId) {
        return portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student portfolio not found"));
    }

    private ServiceListing findOwnService(UUID portfolioId, UUID serviceId) {
        return serviceListingRepository.findByIdAndPortfolioId(serviceId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
    }

    private ServiceMedia findOwnMedia(UUID serviceId, UUID mediaId) {
        return serviceMediaRepository.findByIdAndServiceId(mediaId, serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service media not found"));
    }
}
