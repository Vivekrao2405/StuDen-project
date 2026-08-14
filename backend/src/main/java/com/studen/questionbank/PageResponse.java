package com.studen.questionbank;

import java.util.List;
import org.springframework.data.domain.Page;

// Thin wrapper around Spring Data's Page<> for the two paginated Question Bank list endpoints
// (admin + learner) — a real DB-backed page (see QuestionRepository), not the marketplace's
// in-memory-cap-then-sublist pattern (which exists there because it merges two different entity
// types into one ranked list; this package has no such need).
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
