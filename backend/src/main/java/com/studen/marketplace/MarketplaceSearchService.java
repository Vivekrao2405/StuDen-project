package com.studen.marketplace;

import com.studen.common.exception.InvalidRequestException;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marketplace discovery: no search index yet (deliberately, per product scope for this phase), so
 * this fetches a capped batch of candidates from each source (students, services), merges them in
 * Java, sorts, and paginates the combined list. StuDen's real data volume is nowhere near the cap
 * for a long time — this is a disclosed v1 tradeoff, not a hidden one. Swapping to true DB-level
 * pagination or a real search index later doesn't require changing the response contract.
 */
@Service
public class MarketplaceSearchService {

    private static final int CANDIDATE_CAP = 500;
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 48;

    private final StudentPortfolioRepository portfolioRepository;
    private final ServiceListingRepository serviceListingRepository;
    private final ServiceMediaRepository serviceMediaRepository;

    public MarketplaceSearchService(StudentPortfolioRepository portfolioRepository,
            ServiceListingRepository serviceListingRepository, ServiceMediaRepository serviceMediaRepository) {
        this.portfolioRepository = portfolioRepository;
        this.serviceListingRepository = serviceListingRepository;
        this.serviceMediaRepository = serviceMediaRepository;
    }

    @Transactional(readOnly = true)
    public MarketplaceSearchResponse search(String rawQ, String rawCategory, String rawLocation,
            String rawAvailability, String rawSkill, String rawType, Integer minPrice, Integer maxPrice,
            Integer maxDeliveryDays, String rawSort, int page, int size) {
        String q = normalize(rawQ);
        String location = normalize(rawLocation);
        String skill = normalize(rawSkill);
        MarketplaceCategory category = parseCategory(rawCategory);
        AvailabilityFilter availability = parseAvailability(rawAvailability);
        ResultTypeFilter type = parseType(rawType);
        MarketplaceSort sort = parseSort(rawSort);

        // Skip querying the excluded source entirely when a tab narrows the search — correct
        // per-tab pagination (not a filter-after-fetch), and a genuine perf win: the discarded
        // source's DB query and candidate mapping never happen at all.
        List<Candidate> candidates = new ArrayList<>();
        if (type != ResultTypeFilter.SERVICE) {
            candidates.addAll(searchStudents(q, location, availability, skill, category));
        }
        if (type != ResultTypeFilter.STUDENT) {
            candidates.addAll(searchServices(q, category, location, skill, availability, minPrice, maxPrice, maxDeliveryDays));
        }

        List<Candidate> sorted = applySort(candidates, sort, q);
        List<MarketplaceResultResponse> responses = sorted.stream().map(Candidate::response).toList();

        return MarketplaceSearchResponse.of(responses, Math.max(page, 0), clampSize(size));
    }

    private List<Candidate> searchStudents(String q, String location, AvailabilityFilter availability,
            String skill, MarketplaceCategory category) {
        boolean availableOnly = availability == AvailabilityFilter.AVAILABLE;
        Pageable cap = PageRequest.of(0, CANDIDATE_CAP);

        List<StudentPortfolio> portfolios = portfolioRepository.searchCandidates(
                nullToEmpty(q), nullToEmpty(location), availableOnly, nullToEmpty(skill), cap);

        return portfolios.stream()
                .filter(p -> availability != AvailabilityFilter.NOT_AVAILABLE || !p.isAvailable())
                .filter(p -> category == null
                        || p.getSkills().stream().anyMatch(sk -> SkillCategoryMapper.map(sk.getCategory()) == category))
                .map(p -> new Candidate(p.getCreatedAt(), p.getUser().getFullName(), StudentResultResponse.from(p)))
                .toList();
    }

    private List<Candidate> searchServices(String q, MarketplaceCategory category, String location, String skill,
            AvailabilityFilter availability, Integer minPrice, Integer maxPrice, Integer maxDeliveryDays) {
        boolean availableOnly = availability == AvailabilityFilter.AVAILABLE;
        boolean unavailableOnly = availability == AvailabilityFilter.NOT_AVAILABLE;
        Pageable cap = PageRequest.of(0, CANDIDATE_CAP);
        List<ServiceListing> listings = serviceListingRepository.searchCandidates(nullToEmpty(q), category,
                nullToEmpty(location), nullToEmpty(skill), availableOnly, unavailableOnly, minPrice, maxPrice,
                maxDeliveryDays, cap);
        if (listings.isEmpty()) {
            return List.of();
        }

        // Batch-fetch cover-image media for every candidate in one query instead of one per
        // listing — same N+1 discipline as the rest of this codebase's recent perf pass.
        List<UUID> listingIds = listings.stream().map(ServiceListing::getId).toList();
        Map<UUID, List<ServiceMedia>> mediaByServiceId = serviceMediaRepository
                .findAllByServiceIdInOrderByDisplayOrderAsc(listingIds).stream()
                .collect(Collectors.groupingBy(m -> m.getService().getId()));

        return listings.stream()
                .map(sl -> new Candidate(sl.getCreatedAt(), sl.getTitle(),
                        ServiceResultResponse.from(sl, mediaByServiceId.getOrDefault(sl.getId(), List.of()))))
                .toList();
    }

    private List<Candidate> applySort(List<Candidate> candidates, MarketplaceSort sort, String q) {
        return switch (sort) {
            case RECOMMENDED -> applyRecommendedSort(candidates);
            case NEWEST -> candidates.stream().sorted(Comparator.comparing(Candidate::createdAt).reversed()).toList();
            case RELEVANT -> candidates.stream()
                    .sorted(Comparator.comparingInt(c -> relevanceRank(c.primaryText(), q)))
                    .toList();
        };
    }

    // Placeholder for a future ranking algorithm (skill match strength, location proximity,
    // activity, etc.) — kept as its own branch specifically so it's a real extension point rather
    // than being folded into NEWEST. For now it surfaces the newest listings first.
    private List<Candidate> applyRecommendedSort(List<Candidate> candidates) {
        return candidates.stream().sorted(Comparator.comparing(Candidate::createdAt).reversed()).toList();
    }

    private int relevanceRank(String text, String q) {
        if (q == null || text == null) return 0;
        String lower = text.toLowerCase(Locale.ROOT);
        String term = q.toLowerCase(Locale.ROOT);
        if (lower.equals(term)) return 0;
        if (lower.startsWith(term)) return 1;
        if (lower.contains(term)) return 2;
        return 3;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // Repository queries use "" (not null) as the not-provided sentinel — see
    // StudentPortfolioRepository.searchCandidates for why. Kept separate from normalize() so
    // relevanceRank() and other Java-side logic still see a real null for "no query text".
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private MarketplaceCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return MarketplaceCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown category: " + raw);
        }
    }

    private AvailabilityFilter parseAvailability(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return AvailabilityFilter.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown availability filter: " + raw);
        }
    }

    private ResultTypeFilter parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ResultTypeFilter.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown result type: " + raw);
        }
    }

    private MarketplaceSort parseSort(String raw) {
        if (raw == null || raw.isBlank()) return MarketplaceSort.RECOMMENDED;
        try {
            return MarketplaceSort.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown sort: " + raw);
        }
    }

    private int clampSize(int size) {
        if (size < 1) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }

    private record Candidate(Instant createdAt, String primaryText, MarketplaceResultResponse response) {
    }
}
