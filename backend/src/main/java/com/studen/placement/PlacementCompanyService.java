package com.studen.placement;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.ResourceNotFoundException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-managed company catalog. No company-specific preparation content is generated anywhere in
 * this service: the spec is explicit that company preparation must use configured, verified
 * content and must never invent claims about what a company asks.
 *
 * <p>The list/get methods also back the student-facing catalog endpoints, where a company is
 * searched and multi-selected during onboarding.
 */
@Service
public class PlacementCompanyService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlacementCompanyRepository companyRepository;
    private final PlacementProfileRepository profileRepository;
    private final PlacementSeriesRepository seriesRepository;

    public PlacementCompanyService(PlacementCompanyRepository companyRepository,
            PlacementProfileRepository profileRepository, PlacementSeriesRepository seriesRepository) {
        this.companyRepository = companyRepository;
        this.profileRepository = profileRepository;
        this.seriesRepository = seriesRepository;
    }

    @Transactional(readOnly = true)
    public PlacementPageResponse<PlacementCompanyResponse> list(CompanyType companyType,
            PlacementCatalogStatus status, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.ASC, "name"));
        String normalizedSearch = search == null ? "" : search.trim();
        Page<PlacementCompany> result = companyRepository.search(companyType, status, normalizedSearch, pageable);
        return PlacementPageResponse.of(result.map(PlacementCompanyResponse::from));
    }

    @Transactional(readOnly = true)
    public PlacementCompanyResponse get(UUID id) {
        return PlacementCompanyResponse.from(findCompany(id));
    }

    @Transactional
    public PlacementCompanyResponse create(PlacementCompanyRequest request) {
        String name = request.name().trim();
        String normalized = normalize(name);
        if (companyRepository.existsByNormalizedName(normalized)) {
            throw new ConflictException("A company with this name already exists");
        }
        PlacementCompany company = new PlacementCompany(name, normalized, request.companyType());
        company.setDescription(trimToNull(request.description()));
        company.setLogoUrl(trimToNull(request.logoUrl()));
        return PlacementCompanyResponse.from(companyRepository.save(company));
    }

    @Transactional
    public PlacementCompanyResponse update(UUID id, PlacementCompanyRequest request) {
        PlacementCompany company = findCompany(id);
        String name = request.name().trim();
        String normalized = normalize(name);
        companyRepository.findByNormalizedName(normalized).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ConflictException("A company with this name already exists");
            }
        });
        company.setName(name);
        company.setNormalizedName(normalized);
        company.setCompanyType(request.companyType());
        company.setDescription(trimToNull(request.description()));
        company.setLogoUrl(trimToNull(request.logoUrl()));
        return PlacementCompanyResponse.from(company);
    }

    @Transactional
    public void delete(UUID id) {
        PlacementCompany company = findCompany(id);
        if (profileRepository.existsByTargetCompanyId(id)) {
            throw new ConflictException("Students are already targeting this company — deactivate it instead");
        }
        if (seriesRepository.existsByCompanyId(id)) {
            throw new ConflictException("A placement series belongs to this company — deactivate it instead");
        }
        companyRepository.delete(company);
    }

    @Transactional
    public PlacementCompanyResponse setStatus(UUID id, PlacementCatalogStatus status) {
        PlacementCompany company = findCompany(id);
        company.setStatus(status);
        return PlacementCompanyResponse.from(company);
    }

    private PlacementCompany findCompany(UUID id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
