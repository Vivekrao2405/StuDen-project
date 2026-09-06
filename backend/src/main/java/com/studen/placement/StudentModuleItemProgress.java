package com.studen.placement;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student progress row for one {@link PlacementModuleItem} — the atomic unit of placement
 * progress (a question, a practical/coding assessment, or a learning resource inside a module).
 * One row per (student, module item).
 *
 * <p>Because the item belongs to a module and the module to a series, this single table covers the
 * whole Series -> Modules -> activities progression the spec asks for; the series roll-up lives in
 * {@link StudentSeriesProgress}. Phase 0 adds no progress-calculation engine, only this shape.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_module_item_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_student_module_item_progress",
                columnNames = {"student_id", "module_item_id"}))
public class StudentModuleItemProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_item_id", nullable = false)
    private PlacementModuleItem moduleItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlacementProgressStatus status = PlacementProgressStatus.NOT_STARTED;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public StudentModuleItemProgress(User student, PlacementModuleItem moduleItem, PlacementProgressStatus status) {
        this.student = student;
        this.moduleItem = moduleItem;
        this.status = status;
    }
}
