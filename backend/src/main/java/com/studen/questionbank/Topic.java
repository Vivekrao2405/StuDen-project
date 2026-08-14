package com.studen.questionbank;

import com.studen.common.entity.BaseEntity;
import com.studen.skill.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A topic always belongs to exactly one skill (e.g. Python -> "Functions") — never shared across
// skills, unlike Skill itself. Question.topic is nullable: not every skill's questions need topic
// granularity.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "topics")
public class Topic extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(nullable = false)
    private String name;

    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    public Topic(Skill skill, String name, String normalizedName) {
        this.skill = skill;
        this.name = name;
        this.normalizedName = normalizedName;
    }
}
