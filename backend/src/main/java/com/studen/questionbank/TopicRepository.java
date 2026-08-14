package com.studen.questionbank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    List<Topic> findBySkillIdOrderByDisplayOrderAscNameAsc(UUID skillId);

    Optional<Topic> findBySkillIdAndNormalizedName(UUID skillId, String normalizedName);

    Optional<Topic> findByIdAndSkillId(UUID id, UUID skillId);
}
