package com.studen.marketplace;

import com.studen.portfolio.AvailabilityOption;
import com.studen.portfolio.PortfolioRequest;
import com.studen.portfolio.PortfolioService;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Development-only demo data for the marketplace (Phase 6.1). Off by default: only runs when the
 * "dev" Spring profile is explicitly active (SPRING_PROFILES_ACTIVE=dev) — Spring's active
 * profile is otherwise empty, so this never runs in production or a normal local run. Idempotent:
 * no-ops once any service listing exists, so it never re-seeds or duplicates on restart.
 * Deliberately spans non-tech domains too — StuDen isn't a tech-only platform.
 */
@Component
@Profile("dev")
public class DevMarketplaceSeeder implements CommandLineRunner {

    private static final String SEED_PASSWORD = "DevSeed123!";

    private static final List<DemoStudent> DEMO_STUDENTS = List.of(
            new DemoStudent("Priya Sharma", "priya.sharma.seed@studen.dev", "Frontend Developer", "Hyderabad",
                    "I build clean, responsive web interfaces with React.", true,
                    List.of("React", "JavaScript", "CSS"),
                    "React Component Development", "Reusable, accessible React components built to spec.",
                    MarketplaceCategory.TECHNOLOGY, List.of("React", "JavaScript"), 2000, 5),
            new DemoStudent("Vivek Rao", "vivek.rao.seed@studen.dev", "Data Analyst", "Hyderabad",
                    "Turning data into clear insights and smarter decisions.", true,
                    List.of("Power BI", "Excel", "SQL", "Python"),
                    "Power BI Dashboard Development", "Interactive dashboards for business reporting.",
                    MarketplaceCategory.DATA_ANALYTICS, List.of("Power BI", "Excel"), 1500, 3),
            new DemoStudent("Ananya Iyer", "ananya.iyer.seed@studen.dev", "Graphic Designer", "Bengaluru",
                    "I design brand identities and visuals that stand out.", true,
                    List.of("Figma", "Graphic Design", "Adobe Photoshop"),
                    "Logo Design", "Custom logo design with 3 initial concepts.",
                    MarketplaceCategory.DESIGN_CREATIVE, List.of("Graphic Design", "Figma"), 800, 2),
            new DemoStudent("Rahul Mehta", "rahul.mehta.seed@studen.dev", "Video Editor", "Mumbai",
                    "Editing videos that tell a story and keep viewers hooked.", true,
                    List.of("Video Editing", "Photography"),
                    "Promo Video Editing", "Short-form promo and social video editing.",
                    MarketplaceCategory.VIDEO_MEDIA, List.of("Video Editing"), 1200, 3),
            new DemoStudent("Sneha Kapoor", "sneha.kapoor.seed@studen.dev", "Content Writer", "Delhi",
                    "Writing content that informs, persuades and ranks.", false,
                    List.of("Content Writing", "Copywriting"),
                    "Blog & SEO Content Writing", "SEO-optimized blog posts and web copy.",
                    MarketplaceCategory.WRITING_CONTENT, List.of("Content Writing"), 600, 2),
            new DemoStudent("Arjun Nair", "arjun.nair.seed@studen.dev", "Marketing Student", "Chennai",
                    "Helping brands grow through social media and digital campaigns.", true,
                    List.of("Marketing", "Digital Marketing", "Social Media Marketing"),
                    "Social Media Marketing Campaigns", "Campaign planning and social content calendars.",
                    MarketplaceCategory.MARKETING, List.of("Digital Marketing"), 2500, 7),
            new DemoStudent("Kavya Reddy", "kavya.reddy.seed@studen.dev", "Tutor", "Pune",
                    "Making math simple, clear and understandable.", true,
                    List.of("Mathematics", "Tutoring"),
                    "Mathematics Tutoring", "One-on-one tutoring for school and college-level math.",
                    MarketplaceCategory.EDUCATION_TUTORING, List.of("Mathematics", "Tutoring"), 500, 1),
            new DemoStudent("Ishaan Malhotra", "ishaan.malhotra.seed@studen.dev", "UI/UX Designer", "Hyderabad",
                    "Designing intuitive, user-first mobile and web experiences.", false,
                    List.of("UI/UX Design", "Figma"),
                    "Mobile App UI/UX Design", "End-to-end UI/UX design for mobile apps.",
                    MarketplaceCategory.DESIGN_CREATIVE, List.of("UI/UX Design"), 3000, 7));

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PortfolioService portfolioService;
    private final StudentPortfolioRepository studentPortfolioRepository;
    private final SkillRepository skillRepository;
    private final ServiceListingRepository serviceListingRepository;

    public DevMarketplaceSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
            PortfolioService portfolioService, StudentPortfolioRepository studentPortfolioRepository,
            SkillRepository skillRepository, ServiceListingRepository serviceListingRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.portfolioService = portfolioService;
        this.studentPortfolioRepository = studentPortfolioRepository;
        this.skillRepository = skillRepository;
        this.serviceListingRepository = serviceListingRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (serviceListingRepository.count() > 0) {
            return;
        }

        DEMO_STUDENTS.forEach(this::seedOne);
    }

    private void seedOne(DemoStudent demo) {
        if (userRepository.existsByEmail(demo.email())) {
            return;
        }

        User user = new User(demo.fullName(), demo.email(), passwordEncoder.encode(SEED_PASSWORD));
        user = userRepository.save(user);

        PortfolioRequest request = new PortfolioRequest(
                demo.headline(), demo.bio(), null, null, demo.location(), demo.available(),
                resolveSkillIds(demo.skillNames()),
                Set.of(AvailabilityOption.FREELANCE_PROJECTS, AvailabilityOption.COLLABORATIONS));
        portfolioService.createPortfolio(user.getId(), request);

        StudentPortfolio portfolio = studentPortfolioRepository.findByUserId(user.getId()).orElseThrow();

        ServiceListing listing = new ServiceListing(portfolio, demo.serviceTitle(), demo.serviceCategory());
        listing.setDescription(demo.serviceDescription());
        listing.setLocation(demo.location());
        listing.setSkills(new LinkedHashSet<>(resolveSkills(demo.serviceSkillNames())));
        listing.setPriceAmount(demo.priceAmount());
        listing.setDeliveryDays(demo.deliveryDays());
        listing.setAvailable(demo.available());
        // ServiceListing.status now defaults to DRAFT (Phase 6.3) — these seed listings need to
        // be immediately discoverable in marketplace search, so publish them explicitly.
        listing.setStatus(ServiceStatus.ACTIVE);
        serviceListingRepository.save(listing);
    }

    private Set<UUID> resolveSkillIds(List<String> names) {
        return names.stream()
                .map(this::findSkill)
                .flatMap(Optional::stream)
                .map(Skill::getId)
                .collect(Collectors.toSet());
    }

    private Set<Skill> resolveSkills(List<String> names) {
        return names.stream()
                .map(this::findSkill)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
    }

    private Optional<Skill> findSkill(String name) {
        return skillRepository.findByNormalizedName(name.toLowerCase(Locale.ROOT));
    }

    private record DemoStudent(
            String fullName, String email, String headline, String location, String bio, boolean available,
            List<String> skillNames, String serviceTitle, String serviceDescription,
            MarketplaceCategory serviceCategory, List<String> serviceSkillNames, int priceAmount, int deliveryDays) {
    }
}
