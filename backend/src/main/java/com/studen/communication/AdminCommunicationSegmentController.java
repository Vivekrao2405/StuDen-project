package com.studen.communication;

import com.studen.security.UserPrincipal;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RestController;

// Segments store a filter DEFINITION only — /preview always re-resolves live against current
// data, never a cached/stored recipient list (spec: a segment's count must change automatically
// as underlying data changes).
@RestController
@RequestMapping("/api/v1/admin/communications/segments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCommunicationSegmentController {

    private final CommunicationService communicationService;

    public AdminCommunicationSegmentController(CommunicationService communicationService) {
        this.communicationService = communicationService;
    }

    @GetMapping
    public List<SegmentResponse> list() {
        return communicationService.listSegments();
    }

    @PostMapping
    public SegmentResponse create(@AuthenticationPrincipal UserPrincipal principal, @RequestBody SegmentRequest request) {
        return communicationService.createSegment(principal.getId(), request);
    }

    @PatchMapping("/{id}")
    public SegmentResponse update(@PathVariable UUID id, @RequestBody SegmentRequest request) {
        return communicationService.updateSegment(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        communicationService.deleteSegment(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/preview")
    public AudiencePreviewResponse preview(@PathVariable UUID id) {
        return communicationService.previewSegment(id);
    }
}
