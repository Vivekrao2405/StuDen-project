package com.studen.portfolio;

import com.studen.certificate.CertificateRepository;
import com.studen.common.exception.ConflictException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.education.EducationRepository;
import com.studen.share.ProfileShare;
import com.studen.share.ProfileShareRepository;
import com.studen.showcase.ProjectService;
import com.studen.skill.Skill;
import com.studen.skill.SkillLevel;
import com.studen.skill.SkillRepository;
import com.studen.storage.ImageValidator;
import com.studen.storage.MediaStorageService;
import com.studen.user.User;
import com.studen.user.UserRepository;
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
public class PortfolioService {

    private final StudentPortfolioRepository portfolioRepository;
    private final ProfileShareRepository profileShareRepository;
    private final EducationRepository educationRepository;
    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final PortfolioSkillLevelRepository portfolioSkillLevelRepository;
    private final SlugGenerator slugGenerator;
    private final ImageValidator imageValidator;
    private final MediaStorageService mediaStorageService;
    private final ProjectService projectService;
    private final String publicProfileBaseUrl;

    public PortfolioService(StudentPortfolioRepository portfolioRepository, ProfileShareRepository profileShareRepository,
            EducationRepository educationRepository, CertificateRepository certificateRepository,
            UserRepository userRepository, SkillRepository skillRepository,
            PortfolioSkillLevelRepository portfolioSkillLevelRepository, SlugGenerator slugGenerator,
            ImageValidator imageValidator, MediaStorageService mediaStorageService, ProjectService projectService,
            @Value("${app.public-profile.base-url}") String publicProfileBaseUrl) {
        this.portfolioRepository = portfolioRepository;
        this.profileShareRepository = profileShareRepository;
        this.educationRepository = educationRepository;
        this.certificateRepository = certificateRepository;
        this.userRepository = userRepository;
        this.skillRepository = skillRepository;
        this.portfolioSkillLevelRepository = portfolioSkillLevelRepository;
        this.slugGenerator = slugGenerator;
        this.imageValidator = imageValidator;
        this.mediaStorageService = mediaStorageService;
        this.projectService = projectService;
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

        return toResponse(portfolio);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getMyPortfolio(UUID userId) {
        return toResponse(findOwnPortfolio(userId));
    }

    @Transactional
    public PortfolioResponse updateMyPortfolio(UUID userId, PortfolioRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        applyRequest(portfolio, request);
        return toResponse(portfolio);
    }

    @Transactional
    public PortfolioResponse uploadCoverImage(UUID userId, MultipartFile file) {
        imageValidator.validate(file);
        StudentPortfolio portfolio = findOwnPortfolio(userId);

        String secureUrl = mediaStorageService.upload(coverImagePublicId(portfolio.getId()), file);
        portfolio.setCoverImageUrl(secureUrl);

        return toResponse(portfolio);
    }

    @Transactional
    public PortfolioResponse removeCoverImage(UUID userId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);

        if (portfolio.getCoverImageUrl() != null) {
            mediaStorageService.delete(coverImagePublicId(portfolio.getId()));
            portfolio.setCoverImageUrl(null);
        }

        return toResponse(portfolio);
    }

    @Transactional
    public PortfolioResponse updateSkillLevel(UUID userId, UUID skillId, SkillLevel level) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);

        Skill skill = portfolio.getSkills().stream()
                .filter(s -> s.getId().equals(skillId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("This skill is not on your portfolio"));

        PortfolioSkillLevel entry = portfolioSkillLevelRepository
                .findByPortfolioIdAndSkillId(portfolio.getId(), skillId)
                .orElseGet(() -> new PortfolioSkillLevel(portfolio, skill, level));
        entry.setLevel(level);
        portfolioSkillLevelRepository.save(entry);

        return toResponse(portfolio);
    }

    private PortfolioResponse toResponse(StudentPortfolio portfolio) {
        Map<UUID, SkillLevel> levelsBySkillId = portfolioSkillLevelRepository.findAllByPortfolioId(portfolio.getId())
                .stream()
                .collect(Collectors.toMap(entry -> entry.getSkill().getId(), PortfolioSkillLevel::getLevel));
        return PortfolioResponse.from(portfolio, publicProfileBaseUrl, levelsBySkillId);
    }

    private String coverImagePublicId(UUID portfolioId) {
        return "studen/portfolios/" + portfolioId + "/cover";
    }

    @Transactional
    public void deleteMyPortfolio(UUID userId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        UUID portfolioId = portfolio.getId();

        educationRepository.deleteAll(educationRepository.findAllByPortfolioIdOrderByStartYearDesc(portfolioId));
        certificateRepository.deleteAll(certificateRepository.findAllByPortfolioIdOrderByIssueDateDesc(portfolioId));
        projectService.deleteAllForPortfolio(portfolioId);
        profileShareRepository.findByPortfolioId(portfolioId).ifPresent(profileShareRepository::delete);

        portfolioRepository.delete(portfolio);
    }

    private void applyRequest(StudentPortfolio portfolio, PortfolioRequest request) {
        portfolio.setHeadline(request.headline());
        portfolio.setBio(request.bio());
        portfolio.setExperienceSummary(request.experienceSummary());
        portfolio.setResponseTime(request.responseTime());
        portfolio.setLocation(request.location());
        portfolio.setAvailable(request.available() == null || request.available());

        Set<UUID> skillIds = request.skillIds() == null ? Set.of() : request.skillIds();
        List<Skill> resolvedSkills = skillRepository.findAllByIdIn(skillIds);
        portfolio.setSkills(new LinkedHashSet<>(resolvedSkills));

        portfolio.setAvailableFor(request.availableFor() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(request.availableFor()));
    }

    private StudentPortfolio findOwnPortfolio(UUID userId) {
        return portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student portfolio not found"));
    }
}
