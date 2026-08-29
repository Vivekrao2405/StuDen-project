package com.studen.resource;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentResourceProgressRepository extends JpaRepository<StudentResourceProgress, UUID> {

    Optional<StudentResourceProgress> findByStudentIdAndResourceId(UUID studentId, UUID resourceId);

    List<StudentResourceProgress> findAllByStudentIdAndResourceIdIn(UUID studentId, Collection<UUID> resourceIds);

    // Admin delete guard — mirrors PracticalAttemptRepository.existsByPracticalAssessmentId.
    boolean existsByResourceId(UUID resourceId);

    // Real completion timestamps for RoadmapService's "current streak" calculation — never a
    // second progress store, just the existing completedAt column.
    @Query("select p.completedAt from StudentResourceProgress p where p.student.id = :studentId and p.completedAt is not null")
    List<Instant> findCompletedDates(@Param("studentId") UUID studentId);
}
