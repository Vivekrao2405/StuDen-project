package com.studen.questionbank;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Bulk Markdown import: upload → parse (no DB writes) → admin reviews/edits the Preview
// client-side → confirm persists everything in one transaction. See QuestionImportService.
@RestController
@RequestMapping("/api/v1/admin/questions/import")
@PreAuthorize("hasRole('ADMIN')")
public class AdminQuestionImportController {

    private final QuestionImportService importService;

    public AdminQuestionImportController(QuestionImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/parse")
    public ImportParseResponse parse(@RequestParam("file") MultipartFile file) {
        return importService.parse(file);
    }

    @PostMapping("/confirm")
    public ResponseEntity<ImportConfirmResponse> confirm(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ImportConfirmRequest request) {
        ImportConfirmResponse response = importService.confirmImport(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Publishes a batch of just-imported (DRAFT) questions in one click — best-effort per
    // question, see QuestionImportService.publishImported.
    @PostMapping("/publish")
    public ImportPublishResponse publish(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ImportPublishRequest request) {
        return importService.publishImported(principal.getId(), request.questionIds());
    }
}
