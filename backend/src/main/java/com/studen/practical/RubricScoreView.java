package com.studen.practical;

import java.util.UUID;

public record RubricScoreView(UUID criterionId, String criterion, int maxPoints, int pointsAwarded) {
}
