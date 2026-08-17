package com.studen.practical;

import java.util.UUID;

public record PracticalRubricCriterionResponse(UUID id, String criterion, int maxPoints, int displayOrder) {

    public static PracticalRubricCriterionResponse from(PracticalRubricCriterion entity) {
        return new PracticalRubricCriterionResponse(entity.getId(), entity.getCriterion(), entity.getMaxPoints(),
                entity.getDisplayOrder());
    }
}
