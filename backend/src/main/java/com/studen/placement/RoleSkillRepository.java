package com.studen.placement;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleSkillRepository extends JpaRepository<RoleSkill, UUID> {

    Optional<RoleSkill> findByRoleIdAndSkillId(UUID roleId, UUID skillId);

    boolean existsBySkillId(UUID skillId);

    void deleteByRoleIdAndSkillId(UUID roleId, UUID skillId);
}
