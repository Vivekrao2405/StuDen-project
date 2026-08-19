package com.studen.questionbank;

import java.util.Collection;
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

    // Phase 7.2 (Assessment Engine): lightweight id+difficulty projection, not full entities — the
    // selection algorithm only needs to bucket-and-sample IDs by difficulty, then hydrate just the
    // selected subset. Avoids ever loading a skill's whole published question bank into memory.
    @Query("""
            select q.id as id, q.difficulty as difficulty from Question q
            where q.skill.id = :skillId and q.status = com.studen.questionbank.QuestionStatus.PUBLISHED
            """)
    List<IdDifficultyProjection> findPublishedIdAndDifficultyForSkill(@Param("skillId") UUID skillId);

    interface IdDifficultyProjection {
        UUID getId();

        Difficulty getDifficulty();
    }

    // Per-skill published counts, for the "which skills are assessable" listing — one grouped
    // query rather than a per-skill count-then-loop.
    @Query("""
            select q.skill.id as skillId, count(q) as total from Question q
            where q.status = com.studen.questionbank.QuestionStatus.PUBLISHED
            group by q.skill.id
            """)
    List<SkillPublishedCount> countPublishedGroupedBySkill();

    interface SkillPublishedCount {
        UUID getSkillId();

        long getTotal();
    }

    // Tag-wise assessment analysis: batch-fetches every (questionId, tag) pair for a selected
    // question set in one query, same "avoid N+1 across the whole question set" reasoning as the
    // topic-name batch-fetch in AssessmentService.startOrResume — one lazy Question.tags load per
    // question would otherwise fire for every question in a generated assessment.
    @Query("select q.id as questionId, t as tag from Question q join q.tags t where q.id in :questionIds")
    List<QuestionTagProjection> findTagsForQuestionIds(@Param("questionIds") Collection<UUID> questionIds);

    interface QuestionTagProjection {
        UUID getQuestionId();

        String getTag();
    }
}
