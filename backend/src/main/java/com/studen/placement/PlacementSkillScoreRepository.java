package com.studen.placement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacementSkillScoreRepository extends JpaRepository<PlacementSkillScore, UUID> {

    // Newest first, so the first element for a given skill is that skill current score. The
    // (student_id, skill_id, recorded_at DESC) index backs both of these.
    List<PlacementSkillScore> findByStudentIdOrderByRecordedAtDesc(UUID studentId);

    List<PlacementSkillScore> findByStudentIdAndSkillIdOrderByRecordedAtDesc(UUID studentId, UUID skillId);
}
