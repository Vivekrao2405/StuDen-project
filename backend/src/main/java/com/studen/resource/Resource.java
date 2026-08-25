package com.studen.resource;

import com.studen.common.entity.BaseEntity;
import com.studen.questionbank.Difficulty;
import com.studen.skill.Skill;
import com.studen.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin-curated learning resource (Phase 7.7) — PDF/DOCUMENT (Cloudinary-uploaded file),
 * EXTERNAL_LINK/VIDEO (a URL), or NOTES (plain text, same fenced-```lang```-code convention as
 * {@code com.studen.questionbank.Question}'s explanation field — no markdown pipeline exists in
 * this codebase, see frontend {@code QuestionContent}). {@code tags} is the primary
 * personalization signal matched against a student's weak MCQ topic tags by
 * {@code ResourceMatchingService} — deliberately the same free-form {@code Set<String>} shape as
 * {@code Question.tags}, not a second tagging system.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "resources")
public class Resource extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column
    private Difficulty difficulty;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    // PDF/DOCUMENT only — Cloudinary secure_url plus the public_id needed to delete/replace it.
    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_public_id")
    private String filePublicId;

    // The validated upload's real content type/filename (e.g. "application/pdf", "notes.pdf") —
    // preserved so the file-serving endpoint can set correct headers regardless of what Cloudinary's
    // extension-less "raw" delivery would otherwise guess.
    @Column(name = "file_content_type")
    private String fileContentType;

    @Column(name = "file_name")
    private String fileName;

    // EXTERNAL_LINK/VIDEO only.
    @Column(name = "external_url")
    private String externalUrl;

    // NOTES only.
    @Column(name = "notes_content", columnDefinition = "TEXT")
    private String notesContent;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "resource_tags", joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "tag")
    private Set<String> tags = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status = ResourceStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    public Resource(String title, ResourceType resourceType, Skill skill, User createdBy) {
        this.title = title;
        this.resourceType = resourceType;
        this.skill = skill;
        this.createdBy = createdBy;
    }
}
