package com.studen.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // clearAutomatically is required, not cosmetic: a bulk JPQL UPDATE writes straight to the DB
    // without touching Hibernate's first-level cache, so any RefreshToken already loaded into this
    // transaction's persistence context (e.g. the row just created by an earlier issue() call in
    // the same request) would keep returning its stale in-memory revokedAt=null on a subsequent
    // findByTokenHash within that same transaction, silently defeating the reuse-detection
    // revocation below.
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :revokedAt where t.user.id = :userId and t.revokedAt is null")
    void revokeAllForUser(UUID userId, Instant revokedAt);
}
