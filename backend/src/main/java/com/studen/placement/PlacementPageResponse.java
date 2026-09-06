package com.studen.placement;

import java.util.List;
import org.springframework.data.domain.Page;

// Same thin Page wrapper the question bank and resources packages already use, kept package-local
// so placement endpoints do not import another feature DTO purely for pagination.
public record PlacementPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PlacementPageResponse<T> of(Page<T> page) {
        return new PlacementPageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
