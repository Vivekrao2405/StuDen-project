package com.studen.integrity;

import com.studen.common.exception.ResourceNotFoundException;
import com.studen.integrity.AdminIntegrityTimelineEntry.TimelineCategory;
import com.studen.practical.PracticalAttempt;
import com.studen.practical.PracticalAttemptRepository;
import com.studen.practical.execution.ExecutionJob;
import com.studen.practical.execution.ExecutionJobKind;
import com.studen.practical.execution.ExecutionJobRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Admin-facing integrity review -- assumes ADMIN already checked by {@code @PreAuthorize} on the
 * controller (same posture as every other admin service in this codebase, e.g.
 * {@code AdminPracticalAttemptService}).
 */
@Service
public class AdminIntegrityService {

    private final PracticalAttemptRepository attemptRepository;
    private final AssessmentIntegrityEventRepository eventRepository;
    private final ExecutionJobRepository executionJobRepository;
    private final UserRepository userRepository;
    private final IntegritySummaryFactory summaryFactory;
    private final ObjectMapper objectMapper;

    public AdminIntegrityService(PracticalAttemptRepository attemptRepository,
            AssessmentIntegrityEventRepository eventRepository, ExecutionJobRepository executionJobRepository,
            UserRepository userRepository, IntegritySummaryFactory summaryFactory, ObjectMapper objectMapper) {
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.executionJobRepository = executionJobRepository;
        this.userRepository = userRepository;
        this.summaryFactory = summaryFactory;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminIntegrityTimelineEntry> getTimeline(UUID attemptId) {
        PracticalAttempt attempt = findAttempt(attemptId);
        List<AdminIntegrityTimelineEntry> entries = new ArrayList<>();

        entries.add(new AdminIntegrityTimelineEntry(attempt.getStartedAt(), TimelineCategory.LIFECYCLE,
                "Assessment started", null, null));
        if (attempt.getSubmittedAt() != null) {
            entries.add(new AdminIntegrityTimelineEntry(attempt.getSubmittedAt(), TimelineCategory.LIFECYCLE,
                    "Submitted", null, null));
        }
        if (attempt.getEvaluatedAt() != null) {
            entries.add(new AdminIntegrityTimelineEntry(attempt.getEvaluatedAt(), TimelineCategory.LIFECYCLE,
                    "Evaluated", null, null));
        }

        for (ExecutionJob job : executionJobRepository.findAllByPracticalAttemptIdOrderByCreatedAtAsc(attemptId)) {
            Instant timestamp = job.getCompletedAt() != null ? job.getCompletedAt() : job.getCreatedAt();
            entries.add(new AdminIntegrityTimelineEntry(timestamp, TimelineCategory.EXECUTION,
                    executionLabel(job), executionDetail(job), null));
        }

        for (AssessmentIntegrityEvent event : eventRepository.findAllByPracticalAttemptIdOrderByOccurredAtAsc(attemptId)) {
            entries.add(new AdminIntegrityTimelineEntry(event.getOccurredAt(), TimelineCategory.INTEGRITY,
                    integrityLabel(event), integrityDetail(event), event.getSeverity()));
        }

        entries.sort(Comparator.comparing(AdminIntegrityTimelineEntry::timestamp));
        return entries;
    }

    @Transactional
    public IntegritySummaryResponse override(UUID attemptId, UUID adminId, IntegrityOverrideRequest request) {
        PracticalAttempt attempt = findAttempt(attemptId);
        User admin = userRepository.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        attempt.setIntegrityOverrideStatus(request.status());
        attempt.setIntegrityOverrideReason(request.reason());
        attempt.setIntegrityOverrideBy(admin);
        attempt.setIntegrityOverriddenAt(Instant.now());
        attemptRepository.save(attempt);

        return summaryFactory.build(attempt);
    }

    private String executionLabel(ExecutionJob job) {
        return job.getKind() == ExecutionJobKind.RUN ? "Code execution — Run" : "Code execution — Submit";
    }

    private String executionDetail(ExecutionJob job) {
        return switch (job.getStatus()) {
            case COMPLETED -> job.getTestsTotal() != null
                    ? job.getTestsPassed() + "/" + job.getTestsTotal() + " tests passed" : "Completed";
            case COMPILATION_ERROR -> "Compilation error";
            case RUNTIME_ERROR -> "Runtime error";
            case TIMEOUT -> "Timed out";
            case MEMORY_LIMIT -> "Memory limit exceeded";
            case OUTPUT_LIMIT -> "Output limit exceeded";
            case SECURITY_ERROR -> "Rejected by sandbox guard";
            case SYSTEM_ERROR -> "System error (infrastructure)";
            case CANCELLED -> "Cancelled";
            case QUEUED, RUNNING -> "In progress";
        };
    }

    private String integrityLabel(AssessmentIntegrityEvent event) {
        return switch (event.getEventType()) {
            case TAB_HIDDEN -> "Tab hidden";
            case TAB_VISIBLE -> "Tab visible";
            case WINDOW_BLUR -> "Window lost focus";
            case WINDOW_FOCUS -> "Window regained focus";
            case COPY_ATTEMPT -> "Copy attempt";
            case PASTE_ATTEMPT -> "Paste attempt";
            case CUT_ATTEMPT -> "Cut attempt";
            case FULLSCREEN_ENTERED -> "Entered fullscreen";
            case FULLSCREEN_EXITED -> "Exited fullscreen";
            case NAVIGATION_VIOLATION -> "Unexpected navigation";
            case MULTIPLE_SESSION -> "Multiple active sessions detected";
        };
    }

    private String integrityDetail(AssessmentIntegrityEvent event) {
        if (event.getMetadata() == null || event.getMetadata().isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(event.getMetadata());
            JsonNode away = node.get("awayDurationMs");
            if (away != null && away.isNumber()) {
                return "Away " + (away.asLong() / 1000) + "s";
            }
        } catch (Exception ignored) {
            // Best-effort formatting only -- fall through to the raw string below.
        }
        return event.getMetadata();
    }

    private PracticalAttempt findAttempt(UUID id) {
        return attemptRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Practical attempt not found"));
    }
}
