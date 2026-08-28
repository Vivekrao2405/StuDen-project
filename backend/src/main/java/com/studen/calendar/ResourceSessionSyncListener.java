package com.studen.calendar;

import com.studen.resource.ResourceCompletionListener;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

// Reacts when a resource is completed directly (e.g. the resource detail page's own "Mark
// Completed" action, not via the calendar) by auto-completing any still-SCHEDULED session for that
// same (student, resource) pair — the other half of the sync CalendarService.complete() already
// does explicitly when a session is completed first. Never marks CANCELLED sessions, and never
// touches a session already COMPLETED.
@Component
public class ResourceSessionSyncListener implements ResourceCompletionListener {

    private final LearningSessionRepository learningSessionRepository;

    public ResourceSessionSyncListener(LearningSessionRepository learningSessionRepository) {
        this.learningSessionRepository = learningSessionRepository;
    }

    @Override
    public void onCompleted(UUID studentId, UUID resourceId) {
        List<LearningSession> scheduled = learningSessionRepository
                .findAllByStudentIdAndResourceIdAndStatus(studentId, resourceId, LearningSessionStatus.SCHEDULED);
        Instant now = Instant.now();
        for (LearningSession session : scheduled) {
            session.setStatus(LearningSessionStatus.COMPLETED);
            session.setCompletedAt(now);
        }
    }
}
