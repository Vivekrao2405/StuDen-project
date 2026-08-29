package com.studen.calendar;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.resource.Resource;
import com.studen.resource.ResourceCardResponse;
import com.studen.resource.ResourceProgressStatus;
import com.studen.resource.ResourceRepository;
import com.studen.resource.ResourceService;
import com.studen.resource.ResourceStatus;
import com.studen.resource.RoadmapItemResponse;
import com.studen.resource.RoadmapResponse;
import com.studen.resource.RoadmapService;
import com.studen.resource.StudentResourceProgress;
import com.studen.resource.StudentResourceProgressRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student-scheduled study sessions, always ownership-scoped (see every method's {@code studentId}
 * parameter and {@link LearningSessionRepository#findByIdAndStudentId}). Sessions are created only
 * by an explicit student action (schedule / save-study-plan) — nothing here ever auto-fills the
 * calendar. Completion is never a second progress system: {@link #complete} reuses
 * {@link ResourceService#complete} so {@code StudentResourceProgress} stays the single source of
 * truth, and the reverse direction (a resource completed directly, not via the calendar) is handled
 * by {@link ResourceSessionSyncListener}.
 */
@Service
public class CalendarService {

    private final LearningSessionRepository learningSessionRepository;
    private final ResourceRepository resourceRepository;
    private final StudentResourceProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final ResourceService resourceService;
    private final RoadmapService roadmapService;

    public CalendarService(LearningSessionRepository learningSessionRepository, ResourceRepository resourceRepository,
            StudentResourceProgressRepository progressRepository, UserRepository userRepository,
            ResourceService resourceService, RoadmapService roadmapService) {
        this.learningSessionRepository = learningSessionRepository;
        this.resourceRepository = resourceRepository;
        this.progressRepository = progressRepository;
        this.userRepository = userRepository;
        this.resourceService = resourceService;
        this.roadmapService = roadmapService;
    }

    @Transactional(readOnly = true)
    public List<LearningSessionResponse> sessions(UUID studentId, Instant from, Instant to) {
        return learningSessionRepository
                .findAllByStudentIdAndScheduledStartBetweenOrderByScheduledStartAsc(studentId, from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LearningSessionResponse schedule(UUID studentId, ScheduleSessionRequest request) {
        if (request.resourceId() == null && (request.topic() == null || request.topic().isBlank())) {
            throw new InvalidRequestException("Either a resource or a topic is required");
        }
        User student = findUser(studentId);
        Resource resource = request.resourceId() == null ? null : findPublishedResource(request.resourceId());
        LearningSession session = new LearningSession(student, resource, request.topic(), request.scheduledStart(),
                request.durationMinutes());
        session.setCategory(request.category() != null ? request.category() : LearningSessionCategory.LEARNING);
        return toResponse(learningSessionRepository.save(session));
    }

    @Transactional
    public LearningSessionResponse update(UUID studentId, UUID sessionId, UpdateSessionRequest request) {
        LearningSession session = findOwn(studentId, sessionId);
        if (session.getStatus() != LearningSessionStatus.SCHEDULED) {
            throw new ConflictException("Only a scheduled session can be edited");
        }
        session.setScheduledStart(request.scheduledStart());
        session.setDurationMinutes(request.durationMinutes());
        return toResponse(session);
    }

    @Transactional
    public void delete(UUID studentId, UUID sessionId) {
        LearningSession session = findOwn(studentId, sessionId);
        learningSessionRepository.delete(session);
    }

    @Transactional
    public LearningSessionResponse complete(UUID studentId, UUID sessionId) {
        LearningSession session = findOwn(studentId, sessionId);
        if (session.getStatus() == LearningSessionStatus.CANCELLED) {
            throw new ConflictException("A cancelled session cannot be completed");
        }
        if (session.getStatus() != LearningSessionStatus.COMPLETED) {
            session.setStatus(LearningSessionStatus.COMPLETED);
            session.setCompletedAt(Instant.now());
            if (session.getResource() != null) {
                // Reuses the existing idempotent progress mutator — never a second completion
                // system. This may itself invoke ResourceCompletionListener (incl.
                // ResourceSessionSyncListener), which is safe: this session's own status is already
                // flushed to COMPLETED above, so it's excluded from that listener's SCHEDULED lookup.
                resourceService.complete(studentId, session.getResource().getId());
            }
        }
        return toResponse(session);
    }

    // Pure computation — never writes a row. Walks the roadmap's not-yet-completed items in the
    // same priority order RoadmapService uses for "next up", one per available day over the next 7
    // days from startDate; once the queue is exhausted, remaining days get a resource-less
    // "Practice / Revision" slot (spec's own Friday example) rather than repeating an earlier day.
    @Transactional(readOnly = true)
    public StudyPlanSuggestionResponse previewStudyPlan(UUID studentId, StudyPlanRequest request) {
        RoadmapResponse roadmap = roadmapService.computeRoadmap(studentId);
        List<RoadmapItemResponse> queue = roadmap.groups().stream()
                .flatMap(g -> g.items().stream())
                .filter(i -> i.status() != ResourceProgressStatus.COMPLETED)
                .sorted(RoadmapItemResponse.PRIORITY_ORDER)
                .toList();

        List<StudyPlanSessionSuggestion> sessions = new ArrayList<>();
        int queueIndex = 0;
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = request.startDate().plusDays(offset);
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (!request.availableDays().contains(dayOfWeek)) {
                continue;
            }
            if (queueIndex < queue.size()) {
                RoadmapItemResponse item = queue.get(queueIndex++);
                sessions.add(new StudyPlanSessionSuggestion(date, dayOfWeek, item.skillId(), item.skillName(),
                        item.topic(), item.resource(), request.durationMinutesPerDay(), LearningSessionCategory.LEARNING));
            } else {
                sessions.add(new StudyPlanSessionSuggestion(date, dayOfWeek, null, null, "Practice / Revision", null,
                        request.durationMinutesPerDay(), LearningSessionCategory.PRACTICE));
            }
        }
        return new StudyPlanSuggestionResponse(sessions);
    }

    @Transactional
    public SaveStudyPlanResponse saveStudyPlan(UUID studentId, SaveStudyPlanRequest request) {
        User student = findUser(studentId);
        List<LearningSessionResponse> created = new ArrayList<>();
        List<Integer> skipped = new ArrayList<>();
        List<StudyPlanSessionToSave> toSave = request.sessions();
        for (int i = 0; i < toSave.size(); i++) {
            StudyPlanSessionToSave entry = toSave.get(i);
            Resource resource = entry.resourceId() == null ? null : findPublishedResource(entry.resourceId());
            if (resource != null && learningSessionRepository.existsByStudentIdAndResourceIdAndScheduledStartAndStatus(
                    studentId, resource.getId(), entry.scheduledStart(), LearningSessionStatus.SCHEDULED)) {
                skipped.add(i);
                continue;
            }
            LearningSession session = new LearningSession(student, resource, entry.topic(), entry.scheduledStart(),
                    entry.durationMinutes());
            session.setCategory(entry.category() != null ? entry.category() : LearningSessionCategory.LEARNING);
            created.add(toResponse(learningSessionRepository.save(session)));
        }
        return new SaveStudyPlanResponse(created, skipped);
    }

    private LearningSession findOwn(UUID studentId, UUID sessionId) {
        return learningSessionRepository.findByIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
    }

    private Resource findPublishedResource(UUID id) {
        return resourceRepository.findByIdAndStatus(id, ResourceStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private LearningSessionResponse toResponse(LearningSession session) {
        ResourceCardResponse card = null;
        if (session.getResource() != null) {
            Resource resource = session.getResource();
            StudentResourceProgress progress = progressRepository
                    .findByStudentIdAndResourceId(session.getStudent().getId(), resource.getId()).orElse(null);
            ResourceProgressStatus status = progress != null ? progress.getStatus() : ResourceProgressStatus.NOT_STARTED;
            card = ResourceCardResponse.from(resource, status, progress != null ? progress.getStartedAt() : null,
                    progress != null ? progress.getCompletedAt() : null);
        }
        return new LearningSessionResponse(session.getId(), session.getTopic(), card, session.getScheduledStart(),
                session.getDurationMinutes(), session.getStatus(), session.getCompletedAt(), session.getCategory());
    }
}
