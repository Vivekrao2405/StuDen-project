package com.studen.practical;

import java.util.UUID;

// Admin-only shape — includes `hidden`. Never returned to a student; see StudentTestCaseView for
// the shape that's actually safe to serialize on a student-facing response.
public record PracticalTestCaseResponse(UUID id, String input, String expectedOutput, boolean hidden, int displayOrder,
        OutputComparisonMode comparisonMode) {

    public static PracticalTestCaseResponse from(PracticalTestCase entity) {
        return new PracticalTestCaseResponse(entity.getId(), entity.getInput(), entity.getExpectedOutput(),
                entity.isHidden(), entity.getDisplayOrder(), entity.getComparisonMode());
    }
}
