package com.studen.communication;

import com.studen.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/communications/templates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommunicationTemplateController {

    private final CommunicationService communicationService;

    public AdminCommunicationTemplateController(CommunicationService communicationService) {
        this.communicationService = communicationService;
    }

    @GetMapping
    public List<TemplateResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return communicationService.listTemplates(includeArchived);
    }

    @GetMapping("/{id}")
    public TemplateResponse get(@PathVariable UUID id) {
        return communicationService.getTemplate(id);
    }

    @PostMapping
    public TemplateResponse create(@AuthenticationPrincipal UserPrincipal principal, @RequestBody TemplateRequest request) {
        return communicationService.createTemplate(principal.getId(), request);
    }

    @PatchMapping("/{id}")
    public TemplateResponse update(@PathVariable UUID id, @RequestBody TemplateRequest request) {
        return communicationService.updateTemplate(id, request);
    }

    @PostMapping("/{id}/duplicate")
    public TemplateResponse duplicate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return communicationService.duplicateTemplate(id, principal.getId());
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        communicationService.archiveTemplate(id);
        return ResponseEntity.noContent().build();
    }
}
