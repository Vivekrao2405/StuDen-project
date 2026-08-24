package com.studen.resource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentResourceProgressRepository extends JpaRepository<StudentResourceProgress, UUID> {

    Optional<StudentResourceProgress> findByStudentIdAndResourceId(UUID studentId, UUID resourceId);

    List<StudentResourceProgress> findAllByStudentIdAndResourceIdIn(UUID studentId, Collection<UUID> resourceIds);

    // Admin delete guard — mirrors PracticalAttemptRepository.existsByPracticalAssessmentId.
    boolean existsByResourceId(UUID resourceId);
}
