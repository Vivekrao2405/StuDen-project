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
 * A student roll-up row for one {@link PlacementSeries} — the enrolment/continue-where-you-left-off
 * record. One row per (student, series), enforced by a unique constraint, mirroring
 * {@code StudentResourceProgress}.
 *
 * <p>Paired with {@link StudentModuleItemProgress}, which holds the per-item detail. The pair is
 * two tables rather than one table with nullable module/item columns because Postgres does not
 * deduplicate NULLs inside a UNIQUE constraint, so a single table could silently accumulate
 * duplicate series-level rows. Module-level progress is derived from the item rows.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "student_series_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_student_series_progress",
                columnNames = {"student_id", "series_id"}))
public class StudentSeriesProgress extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private PlacementSeries series;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlacementProgressStatus status = PlacementProgressStatus.NOT_STARTED;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public StudentSeriesProgress(User student, PlacementSeries series, PlacementProgressStatus status) {
        this.student = student;
        this.series = series;
        this.status = status;
    }
}
