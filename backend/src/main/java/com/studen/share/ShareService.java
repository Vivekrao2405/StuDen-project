package com.studen.share;

import com.studen.certificate.CertificateRepository;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.education.EducationRepository;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
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
    private final String publicProfileBaseUrl;

    public ShareService(StudentPortfolioRepository portfolioRepository, ProfileShareRepository profileShareRepository,
            ProfileCardRepository profileCardRepository, EducationRepository educationRepository,
            CertificateRepository certificateRepository,
            @Value("${app.public-profile.base-url}") String publicProfileBaseUrl) {
        this.portfolioRepository = portfolioRepository;
        this.profileShareRepository = profileShareRepository;
        this.profileCardRepository = profileCardRepository;
        this.educationRepository = educationRepository;
        this.certificateRepository = certificateRepository;
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
                List.of(),
                List.of());
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
