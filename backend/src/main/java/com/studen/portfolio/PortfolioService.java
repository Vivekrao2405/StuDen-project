package com.studen.portfolio;

import com.studen.certificate.CertificateRepository;
import com.studen.common.exception.ConflictException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.education.EducationRepository;
import com.studen.share.ProfileShare;
import com.studen.share.ProfileShareRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioService {

    private final StudentPortfolioRepository portfolioRepository;
    private final ProfileShareRepository profileShareRepository;
    private final EducationRepository educationRepository;
    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final SlugGenerator slugGenerator;
    private final String publicProfileBaseUrl;

    public PortfolioService(StudentPortfolioRepository portfolioRepository, ProfileShareRepository profileShareRepository,
            EducationRepository educationRepository, CertificateRepository certificateRepository,
            UserRepository userRepository, SlugGenerator slugGenerator,
            @Value("${app.public-profile.base-url}") String publicProfileBaseUrl) {
        this.portfolioRepository = portfolioRepository;
        this.profileShareRepository = profileShareRepository;
        this.educationRepository = educationRepository;
        this.certificateRepository = certificateRepository;
        this.userRepository = userRepository;
        this.slugGenerator = slugGenerator;
        this.publicProfileBaseUrl = publicProfileBaseUrl;
    }

    @Transactional
    public PortfolioResponse createPortfolio(UUID userId, PortfolioRequest request) {
        if (portfolioRepository.existsByUserId(userId)) {
            throw new ConflictException("A student portfolio already exists for this account");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String slug = slugGenerator.generateUniqueSlug(user.getFullName());

        StudentPortfolio portfolio = new StudentPortfolio(user, request.headline(), slug);
        applyRequest(portfolio, request);
        portfolio = portfolioRepository.save(portfolio);

        profileShareRepository.save(new ProfileShare(portfolio));

        return PortfolioResponse.from(portfolio, publicProfileBaseUrl);
    }

    public PortfolioResponse getMyPortfolio(UUID userId) {
        return PortfolioResponse.from(findOwnPortfolio(userId), publicProfileBaseUrl);
    }

    @Transactional
    public PortfolioResponse updateMyPortfolio(UUID userId, PortfolioRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        applyRequest(portfolio, request);
        return PortfolioResponse.from(portfolio, publicProfileBaseUrl);
    }

    @Transactional
    public void deleteMyPortfolio(UUID userId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        UUID portfolioId = portfolio.getId();

        educationRepository.deleteAll(educationRepository.findAllByPortfolioIdOrderByStartYearDesc(portfolioId));
        certificateRepository.deleteAll(certificateRepository.findAllByPortfolioIdOrderByIssueDateDesc(portfolioId));
        profileShareRepository.findByPortfolioId(portfolioId).ifPresent(profileShareRepository::delete);

        portfolioRepository.delete(portfolio);
    }

    private void applyRequest(StudentPortfolio portfolio, PortfolioRequest request) {
        portfolio.setHeadline(request.headline());
        portfolio.setBio(request.bio());
        portfolio.setExperienceSummary(request.experienceSummary());
        portfolio.setHourlyRate(request.hourlyRate());
        portfolio.setResponseTime(request.responseTime());
        portfolio.setLocation(request.location());
        portfolio.setAvailable(request.available() == null || request.available());
        portfolio.setCoverImageUrl(request.coverImageUrl());
    }

    private StudentPortfolio findOwnPortfolio(UUID userId) {
        return portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student portfolio not found"));
    }
}
