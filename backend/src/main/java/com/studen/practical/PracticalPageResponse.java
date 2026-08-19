package com.studen.practical;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Same shape as questionbank.PageResponse — kept package-local rather than shared, matching this
// codebase's existing convention of not sharing pagination DTOs across feature packages.
public record PracticalPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PracticalPageResponse<T> of(Page<T> page) {
        return new PracticalPageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    // A zero-result page for eligibility states (NO_PORTFOLIO/NO_SKILLS/NO_MATCHING_ASSESSMENTS)
    // where no query is even run — still carries the real page/size the caller asked for.
    public static <T> PracticalPageResponse<T> empty(Pageable pageable) {
        return new PracticalPageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
    }
}
