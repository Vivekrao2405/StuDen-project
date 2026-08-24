package com.studen.resource;

import com.studen.common.entity.BaseEntity;
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

// One row per (student, resource) the student has started or completed — absence of a row means
// NOT_STARTED (see ResourceProgressStatus). Never created merely because a resource was listed.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_resource_progress")
public class StudentResourceProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceProgressStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public StudentResourceProgress(User student, Resource resource, ResourceProgressStatus status) {
        this.student = student;
        this.resource = resource;
        this.status = status;
    }
}
