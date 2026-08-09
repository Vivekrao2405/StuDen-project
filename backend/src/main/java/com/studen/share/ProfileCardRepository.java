package com.studen.share;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileCardRepository extends JpaRepository<ProfileCard, UUID> {

    Optional<ProfileCard> findFirstByShareIdOrderByGeneratedAtDesc(UUID shareId);
}
