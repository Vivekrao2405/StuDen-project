package com.studen.practical;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A language-neutral input/expected-output pair for a CODING (or SQL) {@link PracticalAssessment}
 * — the same row is used regardless of which language the student picks (spec §7). {@code hidden}
 * rows are the actual evaluation cases: every student-facing DTO mapping (see
 * {@code StudentPracticalAssessmentResponse}) filters them out before serialization — never sent
 * to a student under any circumstance.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "practical_test_cases")
public class PracticalTestCase extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_assessment_id", nullable = false)
    private PracticalAssessment practicalAssessment;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String input;

    @Column(name = "expected_output", nullable = false, columnDefinition = "TEXT")
    private String expectedOutput;

    @Column(nullable = false)
    private boolean hidden = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public PracticalTestCase(PracticalAssessment practicalAssessment, String input, String expectedOutput,
            boolean hidden, int displayOrder) {
        this.practicalAssessment = practicalAssessment;
        this.input = input;
        this.expectedOutput = expectedOutput;
        this.hidden = hidden;
        this.displayOrder = displayOrder;
    }
}
