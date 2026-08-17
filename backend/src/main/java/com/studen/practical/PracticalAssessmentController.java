package com.studen.practical;

import com.studen.questionbank.Difficulty;
import com.studen.security.UserPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Standard authentication only — no ADMIN gate, same posture as
// com.studen.assessment.AssessmentController. Every lookup is scoped to PUBLISHED assessments
// (service layer), never trusts a client-supplied ownership/visibility claim.
@RestController
@RequestMapping("/api/v1/practical-assessments")
public class PracticalAssessmentController {

    private final PracticalAssessmentService assessmentService;
    private final PracticalAttemptService attemptService;

    public PracticalAssessmentController(PracticalAssessmentService assessmentService, PracticalAttemptService attemptService) {
        this.assessmentService = assessmentService;
        this.attemptService = attemptService;
    }

    @GetMapping
    public PracticalPageResponse<PracticalAssessmentSummaryResponse> list(
            @RequestParam(required = false) UUID skillId,
            @RequestParam(required = false) PracticalType practicalType,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return assessmentService.list(skillId, practicalType, difficulty, search, page, size);
    }

    @GetMapping("/{id}")
    public StudentPracticalAssessmentResponse get(@PathVariable UUID id) {
        return assessmentService.get(id);
    }

    @PostMapping("/{id}/attempts")
    public ResponseEntity<PracticalAttemptResponse> startAttempt(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        PracticalAttemptResponse response = attemptService.startOrResume(principal.getId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
