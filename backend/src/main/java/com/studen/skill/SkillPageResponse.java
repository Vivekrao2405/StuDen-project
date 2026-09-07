package com.studen.skill;

import java.util.List;
import org.springframework.data.domain.Page;

// Same thin Page wrapper every other package already uses (PlacementPageResponse,
// ResourcePageResponse, ...), kept package-local by convention.
public record SkillPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> SkillPageResponse<T> of(Page<T> page) {
        return new SkillPageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
