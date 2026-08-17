package com.studen.user;

import java.util.List;
import org.springframework.data.domain.Page;

// Same shape as questionbank.PageResponse — kept package-local rather than shared, matching this
// codebase's existing convention of not sharing pagination DTOs across feature packages.
public record AdminUserPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> AdminUserPageResponse<T> of(Page<T> page) {
        return new AdminUserPageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
