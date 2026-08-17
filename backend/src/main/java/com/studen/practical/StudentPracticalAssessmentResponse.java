package com.studen.practical;

import com.studen.questionbank.Difficulty;
import java.util.List;
import java.util.UUID;

// Student-facing shape — PUBLISHED assessments only, hidden test cases structurally excluded
// (StudentTestCaseView has no `hidden` field and is only ever built from non-hidden rows; see
// PracticalAttemptService.toStudentResponse). No admin-only fields (createdBy/reviewedBy/version
// internals) are exposed either.
public record StudentPracticalAssessmentResponse(
        UUID id,
        String title,
        UUID skillId,
        String skillName,
        PracticalType practicalType,
        WorkspaceType workspaceType,
        Difficulty difficulty,
        int timeLimitMinutes,
        String instructions,
        String requirements,
        String constraints,
        String configurationJson,
        List<PracticalCodingLanguageResponse> languages,
        List<StudentTestCaseView> publicTestCases,
        List<PracticalRubricCriterionResponse> rubricCriteria) {
}
