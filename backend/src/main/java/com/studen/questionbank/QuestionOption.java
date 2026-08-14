package com.studen.questionbank;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "question_options")
public class QuestionOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_correct", nullable = false)
    private boolean correct = false;

    public QuestionOption(Question question, String optionText, int displayOrder, boolean correct) {
        this.question = question;
        this.optionText = optionText;
        this.displayOrder = displayOrder;
        this.correct = correct;
    }
}
