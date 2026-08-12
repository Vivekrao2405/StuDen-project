package com.studen.showcase;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project_media")
public class ProjectMedia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private ProjectMediaType mediaType;

    @Column(nullable = false)
    private String url;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    // Cloudinary's public_id — generated as a random UUID before upload (decoupled from this
    // row's own id, so there's no chicken-and-egg needing the DB id before the upload can happen).
    @Column(name = "public_id", nullable = false)
    private String publicId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // Field named without the "is" prefix, DB column with it — matches the existing convention
    // (StudentPortfolio.available -> is_available, User.verified -> is_verified); Lombok still
    // generates isCover().
    @Column(name = "is_cover", nullable = false)
    private boolean cover;

    public ProjectMedia(Project project, ProjectMediaType mediaType, String url, String publicId, int displayOrder) {
        this.project = project;
        this.mediaType = mediaType;
        this.url = url;
        this.publicId = publicId;
        this.displayOrder = displayOrder;
    }
}
