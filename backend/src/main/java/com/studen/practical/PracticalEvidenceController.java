package com.studen.practical;

import com.studen.security.UserPrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Lives in the practical package (not skill) even though the route is under /api/v1/skills — the
// evidence this exposes is entirely practical-assessment data; keeping it here avoids the skill
// package needing to depend on practical at all (spec §30: 7.4 provides evidence, it doesn't
// become a dependency the core skill system needs).
@RestController
@RequestMapping("/api/v1/skills")
public class PracticalEvidenceController {

    private final PracticalEvidenceService evidenceService;

    public PracticalEvidenceController(PracticalEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping("/{skillId}/practical-evidence")
    public ResponseEntity<PracticalEvidenceResponse> practicalEvidence(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID skillId) {
        return evidenceService.latestForSkill(principal.getId(), skillId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
