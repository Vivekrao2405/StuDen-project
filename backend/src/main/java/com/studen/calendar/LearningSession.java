package com.studen.calendar;

import com.studen.common.entity.BaseEntity;
import com.studen.resource.Resource;
import com.studen.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A student-scheduled study session, always tied to a real resource (never auto-created — see
// CalendarService). `topic` is the weak-topic label the session was scheduled against (nullable —
// a resource can match more than one topic, or a session can be scheduled directly against a
// resource with no specific topic), kept here rather than re-derived, since the roadmap's own
// topic ordering can change over time as new assessments/resources come in.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "learning_sessions")
public class LearningSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    // Nullable only for a generated study plan's resource-less "Practice / Revision" slot — every
    // other session (scheduled directly from the roadmap or a resource's own detail page) always
    // carries a real resource.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id")
    private Resource resource;

    @Column
    private String topic;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LearningSessionStatus status = LearningSessionStatus.SCHEDULED;

    @Column(name = "completed_at")
    private Instant completedAt;

    public LearningSession(User student, Resource resource, String topic, Instant scheduledStart,
            Integer durationMinutes) {
        this.student = student;
        this.resource = resource;
        this.topic = topic;
        this.scheduledStart = scheduledStart;
        this.durationMinutes = durationMinutes;
    }
}
