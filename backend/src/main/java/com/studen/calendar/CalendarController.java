package com.studen.calendar;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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

// No class-level @PreAuthorize — same authenticated-by-default posture as ResourceController.
// Every method resolves the session/resource through principal.getId(); a session id belonging to
// another student 404s via CalendarService.findOwn rather than leaking existence.
@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/sessions")
    public List<LearningSessionResponse> sessions(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam Instant from, @RequestParam Instant to) {
        return calendarService.sessions(principal.getId(), from, to);
    }

    @PostMapping("/sessions")
    public LearningSessionResponse schedule(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ScheduleSessionRequest request) {
        return calendarService.schedule(principal.getId(), request);
    }

    @PatchMapping("/sessions/{id}")
    public LearningSessionResponse update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody UpdateSessionRequest request) {
        return calendarService.update(principal.getId(), id, request);
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        calendarService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{id}/complete")
    public LearningSessionResponse complete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return calendarService.complete(principal.getId(), id);
    }

    @PostMapping("/study-plan/preview")
    public StudyPlanSuggestionResponse previewStudyPlan(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody StudyPlanRequest request) {
        return calendarService.previewStudyPlan(principal.getId(), request);
    }

    @PostMapping("/study-plan/save")
    public SaveStudyPlanResponse saveStudyPlan(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SaveStudyPlanRequest request) {
        return calendarService.saveStudyPlan(principal.getId(), request);
    }
}
