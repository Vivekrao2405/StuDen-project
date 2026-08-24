package com.studen.resource;

import java.util.List;
import org.springframework.data.domain.Page;

// Same shape as practical.PracticalPageResponse/questionbank.PageResponse — kept package-local
// rather than shared, matching this codebase's existing convention of not sharing pagination
// DTOs across feature packages.
public record ResourcePageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> ResourcePageResponse<T> of(Page<T> page) {
        return new ResourcePageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
