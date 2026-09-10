package com.studen.aicoach;

import com.studen.common.entity.BaseEntity;
import com.studen.practical.PracticalAttemptQuestion;
import com.studen.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One AI Coach request/response pair, scoped to one student's one {@link PracticalAttemptQuestion}.
 * {@code hintLevel} is derived server-side from prior HINT rows for this (user, attemptQuestion)
 * pair — never client-supplied — and is null for every other action type. {@code requestTopic}
 * is only ever populated for EXPLAIN_CONCEPT.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ai_coach_interactions")
public class AiCoachInteraction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_attempt_question_id", nullable = false)
    private PracticalAttemptQuestion practicalAttemptQuestion;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private AiCoachActionType actionType;

    @Column(name = "hint_level")
    private Integer hintLevel;

    @Column(name = "request_topic")
    private String requestTopic;

    @Column(name = "response_text", nullable = false, columnDefinition = "TEXT")
    private String responseText;

    @Column(nullable = false)
    private String model;

    public AiCoachInteraction(User user, PracticalAttemptQuestion practicalAttemptQuestion,
            AiCoachActionType actionType, Integer hintLevel, String requestTopic, String responseText, String model) {
        this.user = user;
        this.practicalAttemptQuestion = practicalAttemptQuestion;
        this.actionType = actionType;
        this.hintLevel = hintLevel;
        this.requestTopic = requestTopic;
        this.responseText = responseText;
        this.model = model;
    }
}
