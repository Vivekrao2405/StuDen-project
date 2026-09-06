package com.studen.placement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacementAttemptRepository extends JpaRepository<PlacementAttempt, UUID> {

    List<PlacementAttempt> findByStudentIdOrderByStartedAtDesc(UUID studentId);

    // Guards assessment deletion: an assessment students have already attempted is archived,
    // never deleted, so attempt history survives.
    boolean existsByPlacementAssessmentId(UUID placementAssessmentId);
}
