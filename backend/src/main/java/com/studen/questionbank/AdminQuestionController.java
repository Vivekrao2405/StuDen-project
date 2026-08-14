package com.studen.questionbank;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Every method here requires ADMIN — a STUDENT calling any of these gets 403 via
// JwtAccessDeniedHandler (already wired app-wide), enforced server-side regardless of what the
// frontend shows. This is the first admin-gated surface in the codebase; see SecurityConfig for
// the @EnableMethodSecurity that makes @PreAuthorize take effect.
@RestController
@RequestMapping("/api/v1/admin/questions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestionController {

    private final QuestionBankService questionBankService;

    public AdminQuestionController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    @GetMapping
    public PageResponse<QuestionSummaryResponse> list(
            @RequestParam(required = false) UUID skillId,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return questionBankService.list(skillId, topicId, difficulty, type, status, search, page, size);
    }

    @GetMapping("/stats")
    public QuestionBankStatsResponse stats() {
        return questionBankService.stats();
    }

    @GetMapping("/check-duplicate")
    public ResponseEntity<DuplicateWarningResponse> checkDuplicate(@RequestParam UUID skillId, @RequestParam String text) {
        DuplicateWarningResponse warning = questionBankService.checkDuplicate(skillId, text);
        return warning == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(warning);
    }

    @GetMapping("/{id}")
    public QuestionResponse get(@PathVariable UUID id) {
        return questionBankService.get(id);
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> create(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody QuestionRequest request) {
        QuestionResponse response = questionBankService.create(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public QuestionResponse update(@PathVariable UUID id, @Valid @RequestBody QuestionRequest request) {
        return questionBankService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        questionBankService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit-review")
    public QuestionResponse submitForReview(@PathVariable UUID id) {
        return questionBankService.submitForReview(id);
    }

    @PostMapping("/{id}/publish")
    public QuestionResponse publish(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return questionBankService.publish(id, principal.getId());
    }

    @PostMapping("/{id}/archive")
    public QuestionResponse archive(@PathVariable UUID id) {
        return questionBankService.archive(id);
    }

    @PostMapping("/{id}/new-version")
    public ResponseEntity<QuestionResponse> createNewVersion(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        QuestionResponse response = questionBankService.createNewVersion(id, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
