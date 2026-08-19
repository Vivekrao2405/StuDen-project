package com.studen.integrity;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.practical.PracticalAttempt;
import com.studen.practical.PracticalAttemptRepository;
import com.studen.practical.PracticalAttemptStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student-facing integrity event ingestion for one {@link PracticalAttempt} -- ownership is
 * proven the same way every other learner-facing lookup in this codebase proves it
 * ({@link PracticalAttemptRepository#findByIdAndUserId}, never a bare {@code findById}).
 * Never trusts the client for severity, score, or attempt ownership (goal #24); the only thing
 * accepted verbatim is the event type, an idempotency key, a reported timestamp (clamped), an
 * opaque session id, and a small metadata string (goal #25 -- never clipboard contents).
 */
@Service
public class IntegrityEventService {

    // Final-submission flush grace window (goal #29 -- the frontend's last batch, sent right as
    // Submit resolves, must not 409 just because the attempt flipped out of IN_PROGRESS a moment
    // earlier). Also covers a heartbeat landing in the same narrow window.
    private static final long GRACE_SECONDS = 30;
    private static final long CLOCK_SKEW_TOLERANCE_SECONDS = 5;
    private static final long MULTI_SESSION_PRESENCE_WINDOW_SECONDS = 45;
    private static final long MULTI_SESSION_COOLDOWN_SECONDS = 60;

    private final PracticalAttemptRepository attemptRepository;
    private final AssessmentIntegrityEventRepository eventRepository;
    private final PracticalAttemptSessionRepository sessionRepository;
    private final IntegrityScoringService scoringService;
    private final IntegrityEventClassifier classifier;
    private final IntegritySummaryFactory summaryFactory;

    public IntegrityEventService(PracticalAttemptRepository attemptRepository,
            AssessmentIntegrityEventRepository eventRepository, PracticalAttemptSessionRepository sessionRepository,
            IntegrityScoringService scoringService, IntegrityEventClassifier classifier,
            IntegritySummaryFactory summaryFactory) {
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.scoringService = scoringService;
        this.classifier = classifier;
        this.summaryFactory = summaryFactory;
    }

    @Transactional
    public IntegritySummaryResponse recordBatch(UUID userId, UUID attemptId, List<IntegrityEventRequest> requests) {
        PracticalAttempt attempt = findOwnAttempt(userId, attemptId);
        assertAcceptingEvents(attempt);

        Instant now = Instant.now();
        Instant clampMin = attempt.getStartedAt();
        Instant clampMax = now.plusSeconds(CLOCK_SKEW_TOLERANCE_SECONDS);

        boolean persistedAny = false;
        for (IntegrityEventRequest request : requests) {
            if (request.eventType() == IntegrityEventType.MULTIPLE_SESSION) {
                // Server-synthesized only -- see #heartbeat. A client can never know about
                // another tab's session, so this can only be a forged/buggy report.
                throw new InvalidRequestException("MULTIPLE_SESSION cannot be reported by a client");
            }
            if (eventRepository.existsByPracticalAttemptIdAndClientEventId(attemptId, request.clientEventId())) {
                continue; // idempotent no-op: duplicate delivery of an already-recorded event
            }
            Instant occurredAt = clamp(request.occurredAt(), clampMin, clampMax);
            AssessmentIntegrityEvent event = new AssessmentIntegrityEvent(attempt, request.clientEventId(),
                    request.eventType(), IntegritySeverity.INFO, occurredAt, request.sessionId(), request.metadata());
            eventRepository.save(event);
            persistedAny = true;
        }

        if (persistedAny) {
            scoringService.recompute(attempt);
        }
        return summaryFactory.build(attempt);
    }

    @Transactional
    public IntegritySummaryResponse heartbeat(UUID userId, UUID attemptId, String sessionId) {
        PracticalAttempt attempt = findOwnAttempt(userId, attemptId);
        assertAcceptingEvents(attempt);

        Instant now = Instant.now();
        PracticalAttemptSession session = sessionRepository.findByPracticalAttemptIdAndSessionId(attemptId, sessionId)
                .orElseGet(() -> new PracticalAttemptSession(attempt, sessionId, now));
        session.setLastSeenAt(now);
        sessionRepository.save(session);

        Instant presenceWindowStart = now.minusSeconds(MULTI_SESSION_PRESENCE_WINDOW_SECONDS);
        long distinctActiveSessions = sessionRepository
                .findAllByPracticalAttemptIdAndLastSeenAtAfter(attemptId, presenceWindowStart).stream()
                .map(PracticalAttemptSession::getSessionId).distinct().count();

        if (distinctActiveSessions > 1) {
            Instant cooldownStart = now.minusSeconds(MULTI_SESSION_COOLDOWN_SECONDS);
            boolean recentlyFlagged = eventRepository.existsByPracticalAttemptIdAndEventTypeAndOccurredAtAfter(attemptId,
                    IntegrityEventType.MULTIPLE_SESSION, cooldownStart);
            if (!recentlyFlagged) {
                AssessmentIntegrityEvent event = new AssessmentIntegrityEvent(attempt, UUID.randomUUID(),
                        IntegrityEventType.MULTIPLE_SESSION, classifier.classifyMultipleSession(), now, sessionId, null);
                eventRepository.save(event);
                scoringService.recompute(attempt);
            }
        }
        return summaryFactory.build(attempt);
    }

    @Transactional(readOnly = true)
    public IntegritySummaryResponse getSummary(UUID userId, UUID attemptId) {
        return summaryFactory.build(findOwnAttempt(userId, attemptId));
    }

    private void assertAcceptingEvents(PracticalAttempt attempt) {
        if (attempt.getStatus() == PracticalAttemptStatus.IN_PROGRESS) {
            return;
        }
        Instant reference = attempt.getSubmittedAt() != null ? attempt.getSubmittedAt() : attempt.getDeadline();
        if (reference != null && Instant.now().isBefore(reference.plusSeconds(GRACE_SECONDS))) {
            return;
        }
        throw new ConflictException("This attempt is no longer accepting integrity events");
    }

    private Instant clamp(Instant value, Instant min, Instant max) {
        if (min != null && value.isBefore(min)) {
            return min;
        }
        if (value.isAfter(max)) {
            return max;
        }
        return value;
    }

    private PracticalAttempt findOwnAttempt(UUID userId, UUID attemptId) {
        return attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Practical attempt not found"));
    }
}
