package com.studen.questionbank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    // Admin management list: every filter is optional (null/blank = "not provided"), combinable,
    // and genuinely backed by DB-level LIMIT/OFFSET pagination (Page<Question>, not an in-memory
    // cap-then-sublist) — required per spec for scaling to 100k+ questions. `search` uses the
    // empty-string sentinel (see ServiceListingRepository.searchCandidates for why a bound null
    // breaks Postgres's type resolution inside lower(concat(...))); the enum/UUID filters are fine
    // as genuine null.
    @Query("""
            select q from Question q
            where (:skillId is null or q.skill.id = :skillId)
              and (:topicId is null or q.topic.id = :topicId)
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:questionType is null or q.questionType = :questionType)
              and (:status is null or q.status = :status)
              and (:search = '' or lower(q.questionText) like lower(concat('%', :search, '%')))
            """)
    Page<Question> search(@Param("skillId") UUID skillId, @Param("topicId") UUID topicId,
            @Param("difficulty") Difficulty difficulty, @Param("questionType") QuestionType questionType,
            @Param("status") QuestionStatus status, @Param("search") String search, Pageable pageable);

    // Learner-facing: PUBLISHED only, never any other status, regardless of caller-supplied filters.
    @Query("""
            select q from Question q
            where q.skill.id = :skillId
              and q.status = com.studen.questionbank.QuestionStatus.PUBLISHED
              and (:topicId is null or q.topic.id = :topicId)
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:questionType is null or q.questionType = :questionType)
            """)
    Page<Question> findPublishedForSkill(@Param("skillId") UUID skillId, @Param("topicId") UUID topicId,
            @Param("difficulty") Difficulty difficulty, @Param("questionType") QuestionType questionType,
            Pageable pageable);

    Optional<Question> findFirstBySkillIdAndNormalizedQuestionTextOrderByCreatedAtAsc(UUID skillId,
            String normalizedQuestionText);

    @Query("select q.status as status, count(q) as total from Question q group by q.status")
    List<StatusCount> countGroupedByStatus();

    interface StatusCount {
        QuestionStatus getStatus();

        long getTotal();
    }
}
