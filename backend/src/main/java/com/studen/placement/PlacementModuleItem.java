package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import com.studen.practical.PracticalAssessment;
import com.studen.questionbank.Question;
import com.studen.resource.Resource;
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
 * One piece of content inside a {@link PlacementModule}. Exactly one of {@code question},
 * {@code practicalAssessment} or {@code resource} is set, selected by {@code itemType} — enforced
 * both by a database CHECK constraint and by the service layer.
 *
 * <p>Three typed nullable foreign keys rather than a polymorphic (type, uuid) pair, so the
 * database itself guarantees the referenced content exists. This is what lets a placement module
 * be assembled entirely out of infrastructure StuDen already has — Question Bank MCQs, practical
 * or coding assessments, and learning resources — instead of a parallel placement-only copy of any
 * of them.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_module_items")
public class PlacementModuleItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private PlacementModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ModuleItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_assessment_id")
    private PracticalAssessment practicalAssessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id")
    private Resource resource;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private boolean required = true;

    public PlacementModuleItem(PlacementModule module, ModuleItemType itemType, int displayOrder, boolean required) {
        this.module = module;
        this.itemType = itemType;
        this.displayOrder = displayOrder;
        this.required = required;
    }
}
