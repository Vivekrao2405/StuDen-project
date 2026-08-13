package com.studen.share;

import com.studen.certificate.CertificateRepository;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.education.EducationRepository;
import com.studen.marketplace.PublicServiceDetailResponse;
import com.studen.marketplace.ServiceCoverMediaResolver;
import com.studen.marketplace.ServiceLinkResponse;
import com.studen.marketplace.ServiceListing;
import com.studen.marketplace.ServiceListingRepository;
import com.studen.marketplace.ServiceMedia;
import com.studen.marketplace.ServiceMediaRepository;
import com.studen.marketplace.ServiceMediaResponse;
import com.studen.marketplace.ServiceProjectSummaryResponse;
import com.studen.marketplace.ServiceStatus;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import com.studen.showcase.Project;
import com.studen.showcase.ProjectLinkResponse;
import com.studen.showcase.ProjectMedia;
import com.studen.showcase.ProjectMediaRepository;
import com.studen.showcase.ProjectMediaResponse;
import com.studen.showcase.ProjectRepository;
import com.studen.showcase.ProjectVisibility;
import com.studen.showcase.PublicProjectDetailResponse;
import com.studen.showcase.PublicProjectSummaryResponse;
import com.studen.skill.SkillResponse;
import com.studen.user.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareService {

    private final StudentPortfolioRepository portfolioRepository;
    private final ProfileShareRepository profileShareRepository;
    private final ProfileCardRepository profileCardRepository;
    private final EducationRepository educationRepository;
    private final CertificateRepository certificateRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMediaRepository projectMediaRepository;
    private final ServiceListingRepository serviceListingRepository;
    private final ServiceMediaRepository serviceMediaRepository;
    private final String publicProfileBaseUrl;

    public ShareService(StudentPortfolioRepository portfolioRepository, ProfileShareRepository profileShareRepository,
            ProfileCardRepository profileCardRepository, EducationRepository educationRepository,
            CertificateRepository certificateRepository, ProjectRepository projectRepository,
            ProjectMediaRepository projectMediaRepository, ServiceListingRepository serviceListingRepository,
            ServiceMediaRepository serviceMediaRepository,
            @Value("${app.public-profile.base-url}") String publicProfileBaseUrl) {
        this.portfolioRepository = portfolioRepository;
        this.profileShareRepository = profileShareRepository;
        this.profileCardRepository = profileCardRepository;
        this.educationRepository = educationRepository;
        this.certificateRepository = certificateRepository;
        this.projectRepository = projectRepository;
        this.projectMediaRepository = projectMediaRepository;
        this.serviceListingRepository = serviceListingRepository;
        this.serviceMediaRepository = serviceMediaRepository;
        this.publicProfileBaseUrl = publicProfileBaseUrl;
    }

    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(String slug) {
        StudentPortfolio portfolio = portfolioRepository.findByPublicSlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Public profile not found"));

        User user = portfolio.getUser();
        if (!user.isActive()) {
            throw new ResourceNotFoundException("Public profile not found");
        }

        ProfileShare share = profileShareRepository.findByPortfolioId(portfolio.getId()).orElse(null);
        boolean showLocation = share != null && share.isShowContact();

        List<PublicEducationItem> education = educationRepository
                .findAllByPortfolioIdOrderByStartYearDesc(portfolio.getId()).stream()
                .map(PublicEducationItem::from)
                .toList();

        List<PublicCertificateItem> certificates = certificateRepository
                .findAllByPortfolioIdOrderByIssueDateDesc(portfolio.getId()).stream()
                .map(PublicCertificateItem::from)
                .toList();

        List<SkillResponse> skills = portfolio.getSkills().stream()
                .map(SkillResponse::from)
                .toList();

        List<Project> publicProjects = projectRepository
                .findAllByPortfolioIdAndVisibilityOrderByCreatedAtDesc(portfolio.getId(), ProjectVisibility.PUBLIC);

        // Batch-fetch media for every public project in one query instead of one per project —
        // this response is served to unauthenticated public traffic, so it's the highest-impact
        // place to avoid an N+1.
        Map<UUID, List<ProjectMedia>> mediaByProjectId = publicProjects.isEmpty()
                ? Map.of()
                : projectMediaRepository
                        .findAllByProjectIdInOrderByDisplayOrderAsc(publicProjects.stream().map(Project::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(m -> m.getProject().getId()));

        List<PublicProjectSummaryResponse> showcase = publicProjects.stream()
                .map(p -> PublicProjectSummaryResponse.from(p, mediaByProjectId.getOrDefault(p.getId(), List.of())))
                .toList();

        return new PublicProfileResponse(
                portfolio.getPublicSlug(),
                buildProfileUrl(portfolio.getPublicSlug()),
                user.getFullName(),
                user.getProfileImageUrl(),
                portfolio.getCoverImageUrl(),
                portfolio.getHeadline(),
                portfolio.getBio(),
                showLocation ? portfolio.getLocation() : null,
                portfolio.isAvailable(),
                skills,
                education,
                certificates,
                showcase,
                List.of());
    }

    @Transactional(readOnly = true)
    public PublicProjectDetailResponse getPublicProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        // 404, not 403, on a private project — same "don't reveal existence" convention as every
        // other ownership/visibility check in this codebase.
        if (project.getVisibility() != ProjectVisibility.PUBLIC) {
            throw new ResourceNotFoundException("Project not found");
        }

        StudentPortfolio portfolio = project.getPortfolio();
        User user = portfolio.getUser();
        if (!user.isActive()) {
            throw new ResourceNotFoundException("Project not found");
        }

        return new PublicProjectDetailResponse(
                project.getId(),
                project.getTitle(),
                project.getShortDescription(),
                project.getDescription(),
                project.getSkills().stream().map(SkillResponse::from).toList(),
                project.getMedia().stream().map(ProjectMediaResponse::from).toList(),
                project.getLinks().stream().map(ProjectLinkResponse::from).toList(),
                user.getFullName(),
                user.getProfileImageUrl(),
                portfolio.getPublicSlug());
    }

    @Transactional(readOnly = true)
    public PublicServiceDetailResponse getPublicService(UUID serviceId) {
        ServiceListing service = serviceListingRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        // 404, not 403, for a draft/inactive service — same "don't reveal existence" convention
        // as getPublicProject.
        if (service.getStatus() != ServiceStatus.ACTIVE) {
            throw new ResourceNotFoundException("Service not found");
        }

        StudentPortfolio portfolio = service.getPortfolio();
        User user = portfolio.getUser();
        if (!user.isActive()) {
            throw new ResourceNotFoundException("Service not found");
        }

        List<ServiceMedia> media = serviceMediaRepository.findAllByServiceIdOrderByDisplayOrderAsc(service.getId());
        List<ServiceProjectSummaryResponse> linkedProjects = resolveLinkedProjectSummaries(service.getLinkedProjects());

        return new PublicServiceDetailResponse(
                service.getId(),
                service.getTitle(),
                service.getDescription(),
                service.getCategory(),
                service.getLocation(),
                service.isAvailable(),
                service.getPriceAmount(),
                service.getCurrency(),
                service.getDeliveryDays(),
                service.getSkills().stream().map(SkillResponse::from).toList(),
                // Force this lazy @ElementCollection to materialize now, inside the transaction
                // — see ServiceResponse.from's comment for why the raw getter would otherwise
                // throw LazyInitializationException during response serialization.
                service.getWhatYoullReceive().stream().toList(),
                media.stream().map(ServiceMediaResponse::from).toList(),
                service.getLinks().stream().map(ServiceLinkResponse::from).toList(),
                linkedProjects,
                ServiceCoverMediaResolver.resolve(media),
                user.getFullName(),
                portfolio.getHeadline(),
                user.getProfileImageUrl(),
                portfolio.getPublicSlug());
    }

    // Small, bounded set (one service's linked projects) — still batched in one query rather
    // than one findAllByProjectId per linked project, same discipline as getPublicProfile.
    //
    // Filters to currently-PUBLIC projects only: a project can be linked while public and later
    // flipped to PRIVATE by its owner, and this public-facing response must reflect the
    // project's *current* visibility, not what it was at link time. The owner-facing equivalent
    // in ServiceListingService intentionally does NOT apply this filter — the owner should still
    // see everything they linked, regardless of a project's current visibility.
    private List<ServiceProjectSummaryResponse> resolveLinkedProjectSummaries(Set<Project> linkedProjects) {
        List<Project> publicProjects = linkedProjects.stream()
                .filter(p -> p.getVisibility() == ProjectVisibility.PUBLIC)
                .toList();
        if (publicProjects.isEmpty()) {
            return List.of();
        }
        List<UUID> projectIds = publicProjects.stream().map(Project::getId).toList();
        Map<UUID, List<ProjectMedia>> mediaByProjectId = projectMediaRepository
                .findAllByProjectIdInOrderByDisplayOrderAsc(projectIds).stream()
                .collect(Collectors.groupingBy(m -> m.getProject().getId()));
        return publicProjects.stream()
                .map(p -> ServiceProjectSummaryResponse.from(p, mediaByProjectId.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShareMetadataResponse getMyShareMetadata(UUID userId) {
        StudentPortfolio portfolio = portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student portfolio not found"));

        ProfileShare share = profileShareRepository.findByPortfolioId(portfolio.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Share metadata not found"));

        String cardDownloadUrl = profileCardRepository.findFirstByShareIdOrderByGeneratedAtDesc(share.getId())
                .map(ProfileCard::getFileUrl)
                .orElse(null);

        return new ShareMetadataResponse(
                portfolio.getPublicSlug(),
                buildProfileUrl(portfolio.getPublicSlug()),
                cardDownloadUrl);
    }

    private String buildProfileUrl(String slug) {
        return publicProfileBaseUrl + "/" + slug;
    }
}
