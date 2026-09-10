package com.studen.aicoach;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Nested under the existing PracticalAttemptController resource shape (/practical-attempts/{id}/
// questions/{questionId}/...) rather than a new top-level resource — matches the existing
// run/executions endpoints exactly. No class-level @PreAuthorize: same non-admin convention as
// PracticalAttemptController (ownership is enforced in AiCoachService, not a role check).
@RestController
@RequestMapping("/api/v1/practical-attempts/{attemptId}/questions/{attemptQuestionId}/ai-coach")
public class AiCoachController {

    private final AiCoachService aiCoachService;

    public AiCoachController(AiCoachService aiCoachService) {
        this.aiCoachService = aiCoachService;
    }

    @PostMapping
    public AiCoachInteractionResponse request(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID attemptId, @PathVariable UUID attemptQuestionId,
            @Valid @RequestBody AiCoachRequest request) {
        return aiCoachService.requestInteraction(principal.getId(), attemptId, attemptQuestionId,
                request.actionType(), request.topic());
    }

    @GetMapping("/history")
    public List<AiCoachInteractionResponse> history(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID attemptId, @PathVariable UUID attemptQuestionId) {
        return aiCoachService.getHistory(principal.getId(), attemptId, attemptQuestionId);
    }
}
