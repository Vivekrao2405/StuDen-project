package com.studen.education;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/education")
public class EducationController {

    private final EducationService educationService;

    public EducationController(EducationService educationService) {
        this.educationService = educationService;
    }

    @GetMapping
    public List<EducationResponse> getMyEducation(@AuthenticationPrincipal UserPrincipal principal) {
        return educationService.getMyEducation(principal.getId());
    }

    @PostMapping
    public ResponseEntity<EducationResponse> createEducation(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody EducationRequest request) {
        EducationResponse response = educationService.createEducation(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{educationId}")
    public EducationResponse updateEducation(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID educationId, @Valid @RequestBody EducationRequest request) {
        return educationService.updateEducation(principal.getId(), educationId, request);
    }

    @DeleteMapping("/{educationId}")
    public ResponseEntity<Void> deleteEducation(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID educationId) {
        educationService.deleteEducation(principal.getId(), educationId);
        return ResponseEntity.noContent().build();
    }
}
