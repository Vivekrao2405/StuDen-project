package com.studen.placement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentSeriesProgressRepository extends JpaRepository<StudentSeriesProgress, UUID> {

    Optional<StudentSeriesProgress> findByStudentIdAndSeriesId(UUID studentId, UUID seriesId);

    List<StudentSeriesProgress> findByStudentId(UUID studentId);

    // Guards series deletion, the same way AdminResourceService guards a resource that students
    // have already started: archive it, never delete it out from under their progress.
    boolean existsBySeriesId(UUID seriesId);
}
