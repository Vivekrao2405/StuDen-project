package com.studen.assessment;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Standard authentication only (SecurityConfig's anyRequest().authenticated()) — no ADMIN gate,
// same posture as LearnerQuestionController. Every lookup is scoped to the calling user inside
// AssessmentService; this controller never trusts a client-supplied ownership claim.
@RestController
@RequestMapping("/api/v1/assessments")
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final SkillResultService skillResultService;

    public AssessmentController(AssessmentService assessmentService, SkillResultService skillResultService) {
        this.assessmentService = assessmentService;
        this.skillResultService = skillResultService;
    }

    @GetMapping("/skills")
    public List<AssessableSkillResponse> listAssessableSkills() {
        return assessmentService.listAssessableSkills();
    }

    @PostMapping
    public ResponseEntity<AssessmentDetailResponse> start(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StartAssessmentRequest request) {
        AssessmentDetailResponse response = assessmentService.startOrResume(principal.getId(), request.skillId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Returns AssessmentDetailResponse while IN_PROGRESS, AssessmentResultResponse once terminal —
    // see AssessmentService.getAssessment.
    @GetMapping("/{assessmentId}")
    public Object get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID assessmentId) {
        return assessmentService.getAssessment(principal.getId(), assessmentId);
    }

    @PatchMapping("/{assessmentId}/questions/{assessmentQuestionId}/answer")
    public AnswerResponse saveAnswer(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID assessmentId,
            @PathVariable UUID assessmentQuestionId, @Valid @RequestBody AnswerRequest request) {
        return assessmentService.saveAnswer(principal.getId(), assessmentId, assessmentQuestionId, request.selectedOptionIds());
    }

    @PostMapping("/{assessmentId}/submit")
    public AssessmentResultResponse submit(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID assessmentId) {
        return assessmentService.submit(principal.getId(), assessmentId);
    }

    // Phase 7.3: the scored/leveled/topic-broken-down summary — see AssessmentResultSummaryResponse
    // for why this is deliberately a separate shape from the GET /{assessmentId} review payload.
    // 409s (via SkillResultService) if the assessment is still IN_PROGRESS.
    @GetMapping("/{assessmentId}/result")
    public AssessmentResultSummaryResponse getResult(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID assessmentId) {
        return skillResultService.getResult(principal.getId(), assessmentId);
    }

    // "Latest" = most recently submitted, across every skill — backs the Dashboard's "latest
    // assessment result" tile. 204 (not 404) when the student has never completed one, since
    // "no result yet" is a normal empty state, not an error.
    @GetMapping("/latest-result")
    public ResponseEntity<AssessmentResultSummaryResponse> latestResult(@AuthenticationPrincipal UserPrincipal principal) {
        return skillResultService.latestForUser(principal.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Per-skill "latest" (spec §15) — deliberately distinct from latestForUser's "best result"-
    // shaped future extension point; kept separate so a later "best result" query never gets
    // confused with this one.
    @GetMapping("/skills/{skillId}/latest-result")
    public ResponseEntity<AssessmentResultSummaryResponse> latestResultForSkill(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID skillId) {
        return skillResultService.latestForSkill(principal.getId(), skillId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
