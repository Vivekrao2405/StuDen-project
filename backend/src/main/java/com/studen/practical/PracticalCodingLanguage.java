package com.studen.practical;

import com.studen.common.entity.BaseEntity;
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
 * One supported language + its starter code for a CODING {@link PracticalAssessment}. The problem
 * statement, constraints, and test cases live once on the assessment itself and are shared by
 * every language row here — this is exactly what keeps "one coding problem = one problem
 * definition" true (spec §5/§36) instead of four near-duplicate questions.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "practical_coding_languages")
public class PracticalCodingLanguage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_assessment_id", nullable = false)
    private PracticalAssessment practicalAssessment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodingLanguage language;

    @Column(name = "starter_code", columnDefinition = "TEXT")
    private String starterCode;

    public PracticalCodingLanguage(PracticalAssessment practicalAssessment, CodingLanguage language, String starterCode) {
        this.practicalAssessment = practicalAssessment;
        this.language = language;
        this.starterCode = starterCode;
    }
}
