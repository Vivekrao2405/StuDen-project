package com.studen.placement;

import com.studen.practical.PracticalAttemptStatus;
import com.studen.practical.PracticalType;
import com.studen.questionbank.Difficulty;
import com.studen.questionbank.QuestionType;
import com.studen.resource.ResourceType;
import java.util.List;
import java.util.UUID;

/**
 * The full content of one placement module item — the shape branches on {@code itemType} exactly
 * like {@link PlacementAttemptQuestionView} already does for the readiness engine, so only the
 * fields for the actual type are populated.
 *
 * <p>QUESTION carries the question inline (there is no other "take one question" surface to reuse,
 * so this is Phase 5's one genuinely new piece of student-facing content). PRACTICAL_ASSESSMENT and
 * RESOURCE deliberately stay thin pointers — the student takes the practical through the existing
 * {@code /practical-attempts/:id} workspace and views the resource through the existing
 * {@code /resources/:id} flow, never a second copy of either here.
 */
public record PlacementModuleItemDetailResponse(
        UUID id,
        UUID moduleId,
        UUID seriesId,
        ModuleItemType itemType,
        boolean required,
        PlacementProgressStatus status,
        String skillName,
        // Phase 6: lets the frontend show a "Role · Skill · Placement Preparation" breadcrumb when
        // deep-linking into the shared /practical-attempts/:id workspace, without a second round trip.
        String seriesTitle,
        String roleName,
        // QUESTION
        String questionText,
        QuestionType questionType,
        Difficulty difficulty,
        List<PlacementAttemptOptionView> options,
        List<UUID> correctOptionIds,
        String explanation,
        // PRACTICAL_ASSESSMENT
        UUID practicalAssessmentId,
        String practicalAssessmentTitle,
        PracticalType practicalType,
        UUID practicalAttemptId,
        PracticalAttemptStatus practicalAttemptStatus,
        // RESOURCE
        UUID resourceId,
        String resourceTitle,
        ResourceType resourceType) {
}
