package com.studen.share;

import com.studen.certificate.CertificateRepository;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.education.EducationRepository;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import com.studen.showcase.Project;
import com.studen.showcase.ProjectLinkResponse;
import com.studen.showcase.ProjectMediaResponse;
import com.studen.showcase.ProjectRepository;
import com.studen.showcase.ProjectVisibility;
import com.studen.showcase.PublicProjectDetailResponse;
import com.studen.showcase.PublicProjectSummaryResponse;
import com.studen.skill.SkillResponse;
import com.studen.user.User;
import java.util.List;
import java.util.UUID;
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
    private final String publicProfileBaseUrl;

    public ShareService(StudentPortfolioRepository portfolioRepository, ProfileShareRepository profileShareRepository,
            ProfileCardRepository profileCardRepository, EducationRepository educationRepository,
            CertificateRepository certificateRepository, ProjectRepository projectRepository,
            @Value("${app.public-profile.base-url}") String publicProfileBaseUrl) {
        this.portfolioRepository = portfolioRepository;
        this.profileShareRepository = profileShareRepository;
        this.profileCardRepository = profileCardRepository;
        this.educationRepository = educationRepository;
        this.certificateRepository = certificateRepository;
        this.projectRepository = projectRepository;
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

        List<PublicProjectSummaryResponse> showcase = projectRepository
                .findAllByPortfolioIdAndVisibilityOrderByCreatedAtDesc(portfolio.getId(), ProjectVisibility.PUBLIC)
                .stream()
                .map(PublicProjectSummaryResponse::from)
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
