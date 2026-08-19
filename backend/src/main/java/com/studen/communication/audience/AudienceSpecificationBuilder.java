package com.studen.communication.audience;

import com.studen.assessment.Assessment;
import com.studen.assessment.AssessmentLevel;
import com.studen.assessment.AssessmentStatus;
import com.studen.assessment.ScoringProperties;
import com.studen.common.exception.InvalidRequestException;
import com.studen.portfolio.StudentPortfolio;
import com.studen.skill.Skill;
import com.studen.user.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * The one place audience logic lives (spec: "no hardcoded queries for every campaign type"). Every
 * {@link AudienceFilterField} maps to exactly one private builder method here; nothing outside
 * this class ever writes a query against portfolio/skill/assessment tables for campaign purposes.
 * Compiles a whole {@link AudienceFilterNode} tree into a single JPA {@code Specification<User>}
 * that {@code UserRepository} (a {@code JpaSpecificationExecutor<User>}) can count/fetch directly
 * — no SQL string ever touches the admin-facing side of this feature.
 */
@Component
public class AudienceSpecificationBuilder {

    private final ScoringProperties scoringProperties;

    private static final List<AssessmentStatus> TERMINAL_STATUSES =
            List.of(AssessmentStatus.SUBMITTED, AssessmentStatus.EXPIRED);

    public AudienceSpecificationBuilder(ScoringProperties scoringProperties) {
        this.scoringProperties = scoringProperties;
    }

    public Specification<User> build(AudienceFilterNode node) {
        return build(node, false);
    }

    // `excludeMarketingOptOut` is the SAME hard floor CampaignSendService.resolveAndQueue used to
    // apply separately, in Java, after resolve() returned — duplicating this filter risked exactly
    // the drift it's meant to prevent (an audience-preview count that doesn't match who a marketing
    // campaign actually reaches). Building it into the Specification itself means preview/resolve/
    // count all apply the identical exclusion, by construction, never two parallel implementations.
    public Specification<User> build(AudienceFilterNode node, boolean excludeMarketingOptOut) {
        // Permanently-deleted/anonymized accounts (User.deletedAt != null) are never a valid
        // campaign recipient regardless of what the admin's filter matches — their stored name/
        // email are anonymized placeholders, not a real student. This is a hard floor, not
        // something exposed as a filter option the admin could forget to add.
        Specification<User> notDeleted = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        Specification<User> requested = node instanceof AudienceFilterGroup group ? buildGroup(group)
                : node instanceof AudienceFilterCondition condition ? buildCondition(condition)
                        : failUnrecognized();
        Specification<User> combined = notDeleted.and(requested);
        if (excludeMarketingOptOut) {
            combined = combined.and((root, query, cb) -> cb.isFalse(root.get("marketingOptOut")));
        }
        return combined;
    }

    private Specification<User> failUnrecognized() {
        throw new InvalidRequestException("Unrecognized audience filter node");
    }

    private Specification<User> buildGroup(AudienceFilterGroup group) {
        if (group.children().isEmpty()) {
            // Deliberately matches everyone regardless of AND/OR — see AudienceFilterGroup javadoc.
            return (root, query, cb) -> cb.conjunction();
        }
        List<Specification<User>> children = group.children().stream().map(this::build).toList();
        Specification<User> combined = children.get(0);
        for (int i = 1; i < children.size(); i++) {
            combined = group.operator() == AudienceLogicOperator.AND
                    ? combined.and(children.get(i))
                    : combined.or(children.get(i));
        }
        return combined;
    }

    private Specification<User> buildCondition(AudienceFilterCondition condition) {
        return switch (condition.field()) {
            case PORTFOLIO_EXISTS -> (root, query, cb) -> cb.exists(portfolioExistsSubquery(root, query, cb, null));
            case PORTFOLIO_NOT_EXISTS ->
                    (root, query, cb) -> cb.not(cb.exists(portfolioExistsSubquery(root, query, cb, null)));
            case PORTFOLIO_UPDATED_WITHIN_DAYS -> portfolioRecency(condition, true);
            case PORTFOLIO_STALE_SINCE_DAYS -> portfolioRecency(condition, false);

            case SKILL_HAS -> skillMembership(requireUuid(condition, "skillId"), true);
            case SKILL_LACKS -> skillMembership(requireUuid(condition, "skillId"), false);
            case SKILL_HAS_ANY -> skillHasAny(requireUuidList(condition, "skillIds"));
            case SKILL_HAS_ALL -> skillHasAll(requireUuidList(condition, "skillIds"));

            case ASSESSMENT_COMPLETED -> assessmentCompleted(optionalUuid(condition, "skillId"), true);
            case ASSESSMENT_NOT_COMPLETED -> assessmentCompleted(optionalUuid(condition, "skillId"), false);
            case ASSESSMENT_NO_ATTEMPT -> assessmentNoAttempt(optionalUuid(condition, "skillId"));
            case ASSESSMENT_SCORE_GTE -> assessmentScoreRange(optionalUuid(condition, "skillId"),
                    requireInt(condition, "minScore"), 100);
            case ASSESSMENT_SCORE_RANGE -> assessmentScoreRange(optionalUuid(condition, "skillId"),
                    requireInt(condition, "minScore"), requireInt(condition, "maxScore"));

            case SKILL_VERIFICATION_LEVEL -> skillVerificationLevel(requireUuid(condition, "skillId"),
                    requireEnum(condition, "level", AssessmentLevel.class));
            case SKILL_VERIFICATION_UNVERIFIED -> assessmentNoAttempt(requireUuid(condition, "skillId"));

            case ACTIVITY_LAST_ACTIVE_BEFORE_DAYS -> lastActiveBeforeDays(requireInt(condition, "days"));
            case ACTIVITY_REGISTERED_BETWEEN -> registeredBetween(condition);

            case USER_VERIFIED -> (root, query, cb) -> cb.isTrue(root.get("verified"));
            case USER_UNVERIFIED -> (root, query, cb) -> cb.isFalse(root.get("verified"));
            case USER_ACTIVE -> (root, query, cb) -> cb.isTrue(root.get("active"));
            case USER_INACTIVE -> (root, query, cb) -> cb.isFalse(root.get("active"));
            case USER_SPECIFIC_IDS -> {
                List<UUID> ids = requireUuidList(condition, "userIds");
                yield (root, query, cb) -> root.get("id").in(ids);
            }
        };
    }

    // --- Portfolio ---------------------------------------------------------------------------

    private Subquery<UUID> portfolioExistsSubquery(Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            Predicate extra) {
        Subquery<UUID> sub = query.subquery(UUID.class);
        Root<StudentPortfolio> p = sub.from(StudentPortfolio.class);
        Predicate userMatch = cb.equal(p.get("user"), root);
        sub.select(p.get("id")).where(extra == null ? userMatch : cb.and(userMatch, extra));
        return sub;
    }

    private Specification<User> portfolioRecency(AudienceFilterCondition condition, boolean withinDays) {
        int days = requireInt(condition, "days");
        return (root, query, cb) -> {
            Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
            Subquery<UUID> sub = query.subquery(UUID.class);
            Root<StudentPortfolio> p = sub.from(StudentPortfolio.class);
            Predicate userMatch = cb.equal(p.get("user"), root);
            Predicate recency = withinDays ? cb.greaterThanOrEqualTo(p.get("updatedAt"), cutoff)
                    : cb.lessThan(p.get("updatedAt"), cutoff);
            sub.select(p.get("id")).where(cb.and(userMatch, recency));
            return cb.exists(sub);
        };
    }

    // --- Skill ---------------------------------------------------------------------------------

    private Specification<User> skillMembership(UUID skillId, boolean has) {
        return (root, query, cb) -> {
            Predicate exists = cb.exists(portfolioSkillSubquery(root, query, cb, List.of(skillId)));
            return has ? exists : cb.not(exists);
        };
    }

    private Specification<User> skillHasAny(List<UUID> skillIds) {
        return (root, query, cb) -> cb.exists(portfolioSkillSubquery(root, query, cb, skillIds));
    }

    private Specification<User> skillHasAll(List<UUID> skillIds) {
        return (root, query, cb) -> {
            Predicate combined = cb.exists(portfolioSkillSubquery(root, query, cb, List.of(skillIds.get(0))));
            for (int i = 1; i < skillIds.size(); i++) {
                combined = cb.and(combined, cb.exists(portfolioSkillSubquery(root, query, cb, List.of(skillIds.get(i)))));
            }
            return combined;
        };
    }

    private Subquery<UUID> portfolioSkillSubquery(Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            List<UUID> anyOfSkillIds) {
        Subquery<UUID> sub = query.subquery(UUID.class);
        Root<StudentPortfolio> p = sub.from(StudentPortfolio.class);
        Join<StudentPortfolio, Skill> skillJoin = p.join("skills");
        sub.select(p.get("id")).where(cb.and(cb.equal(p.get("user"), root), skillJoin.get("id").in(anyOfSkillIds)));
        return sub;
    }

    // --- Assessment (knowledge) -----------------------------------------------------------------

    private Specification<User> assessmentCompleted(UUID skillId, boolean completed) {
        return (root, query, cb) -> {
            Predicate exists = cb.exists(assessmentSubquery(root, query, cb, skillId, TERMINAL_STATUSES, null, null));
            return completed ? exists : cb.not(exists);
        };
    }

    private Specification<User> assessmentNoAttempt(UUID skillId) {
        return (root, query, cb) -> cb.not(cb.exists(assessmentSubquery(root, query, cb, skillId, null, null, null)));
    }

    private Specification<User> assessmentScoreRange(UUID skillId, int minScore, int maxScore) {
        return (root, query, cb) -> cb.exists(
                assessmentSubquery(root, query, cb, skillId, TERMINAL_STATUSES, minScore, maxScore));
    }

    private Specification<User> skillVerificationLevel(UUID skillId, AssessmentLevel level) {
        int min = switch (level) {
            case BEGINNER -> 0;
            case DEVELOPING -> scoringProperties.getDevelopingMin();
            case INTERMEDIATE -> scoringProperties.getIntermediateMin();
            case ADVANCED -> scoringProperties.getAdvancedMin();
            case EXPERT -> scoringProperties.getExpertMin();
        };
        int max = switch (level) {
            case BEGINNER -> scoringProperties.getDevelopingMin() - 1;
            case DEVELOPING -> scoringProperties.getIntermediateMin() - 1;
            case INTERMEDIATE -> scoringProperties.getAdvancedMin() - 1;
            case ADVANCED -> scoringProperties.getExpertMin() - 1;
            case EXPERT -> 100;
        };
        return assessmentScoreRange(skillId, min, max);
    }

    private Subquery<UUID> assessmentSubquery(Root<User> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            UUID skillId, List<AssessmentStatus> statuses, Integer minScore, Integer maxScore) {
        Subquery<UUID> sub = query.subquery(UUID.class);
        Root<Assessment> a = sub.from(Assessment.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(a.get("user"), root));
        if (skillId != null) {
            predicates.add(cb.equal(a.get("skill").get("id"), skillId));
        }
        if (statuses != null) {
            predicates.add(a.get("status").in(statuses));
        }
        if (minScore != null) {
            predicates.add(cb.greaterThanOrEqualTo(a.get("scorePercentage"), minScore));
        }
        if (maxScore != null) {
            predicates.add(cb.lessThanOrEqualTo(a.get("scorePercentage"), maxScore));
        }
        sub.select(a.get("id")).where(cb.and(predicates.toArray(new Predicate[0])));
        return sub;
    }

    // --- Activity / User ---------------------------------------------------------------------

    private Specification<User> lastActiveBeforeDays(int days) {
        return (root, query, cb) -> {
            Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
            // Never-logged-in-since-this-column-existed counts as inactive too.
            return cb.or(cb.isNull(root.get("lastLoginAt")), cb.lessThan(root.get("lastLoginAt"), cutoff));
        };
    }

    private Specification<User> registeredBetween(AudienceFilterCondition condition) {
        Instant from = requireInstant(condition, "from");
        Instant to = requireInstant(condition, "to");
        return (root, query, cb) -> cb.between(root.get("createdAt"), from, to);
    }

    // --- Param parsing (defensive: a bad value is a 400, never a silently-wrong audience) ------

    private UUID requireUuid(AudienceFilterCondition condition, String key) {
        try {
            return UUID.fromString(condition.param(key));
        } catch (Exception e) {
            throw new InvalidRequestException("Audience filter " + condition.field() + " requires a valid " + key);
        }
    }

    private UUID optionalUuid(AudienceFilterCondition condition, String key) {
        String raw = condition.param(key);
        return (raw == null || raw.isBlank()) ? null : requireUuid(condition, key);
    }

    private List<UUID> requireUuidList(AudienceFilterCondition condition, String key) {
        String raw = condition.param(key);
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("Audience filter " + condition.field() + " requires " + key);
        }
        List<UUID> ids;
        try {
            ids = List.of(raw.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString)
                    .toList();
        } catch (Exception e) {
            throw new InvalidRequestException("Audience filter " + condition.field() + " has an invalid " + key);
        }
        if (ids.isEmpty()) {
            throw new InvalidRequestException("Audience filter " + condition.field() + " requires at least one " + key);
        }
        return ids;
    }

    private int requireInt(AudienceFilterCondition condition, String key) {
        try {
            return Integer.parseInt(condition.param(key));
        } catch (Exception e) {
            throw new InvalidRequestException("Audience filter " + condition.field() + " requires a numeric " + key);
        }
    }

    private Instant requireInstant(AudienceFilterCondition condition, String key) {
        try {
            return Instant.parse(condition.param(key));
        } catch (Exception e) {
            throw new InvalidRequestException(
                    "Audience filter " + condition.field() + " requires an ISO-8601 timestamp for " + key);
        }
    }

    private <E extends Enum<E>> E requireEnum(AudienceFilterCondition condition, String key, Class<E> type) {
        try {
            return Enum.valueOf(type, condition.param(key));
        } catch (Exception e) {
            throw new InvalidRequestException("Audience filter " + condition.field() + " has an invalid " + key);
        }
    }
}
