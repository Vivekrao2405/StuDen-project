package com.studen.communication;

import java.util.List;
import org.springframework.data.domain.Page;

// Same shape as user.AdminUserPageResponse — kept package-local rather than shared, matching this
// codebase's existing convention of not sharing pagination DTOs across feature packages (raw
// Page<T> is intentionally never returned directly from a controller here).
public record CampaignPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> CampaignPageResponse<T> of(Page<T> page) {
        return new CampaignPageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
