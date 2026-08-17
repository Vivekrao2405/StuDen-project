package com.studen.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Admin User Management list (com.studen.user.AdminUserService). `search` uses the same
    // empty-string sentinel as QuestionRepository.search — a bound null breaks Postgres's type
    // resolution inside lower(concat(...)) even though the ":search = '' or ..." branch would
    // short-circuit it logically at runtime; the query still has to type-check as a whole.
    // LEFT JOIN because a user without a StudentPortfolio yet (e.g. never completed onboarding)
    // must still be findable/listable.
    @Query("""
            select distinct u from User u
            left join StudentPortfolio p on p.user = u
            where (:search = ''
                or lower(u.fullName) like lower(concat('%', :search, '%'))
                or lower(u.email) like lower(concat('%', :search, '%'))
                or lower(p.publicSlug) like lower(concat('%', :search, '%')))
            """)
    Page<User> search(@Param("search") String search, Pageable pageable);
}
