package com.studen.marketplace;

import com.studen.common.entity.BaseEntity;
import com.studen.showcase.ProjectMediaType;
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

/** Mirrors {@code com.studen.showcase.ProjectMedia} exactly. Reuses {@link ProjectMediaType}
 * directly rather than duplicating an IMAGE/VIDEO enum — it's a pure storage/technical
 * classification, not a domain concept specific to either module. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "service_media")
public class ServiceMedia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceListing service;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private ProjectMediaType mediaType;

    @Column(nullable = false)
    private String url;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "public_id", nullable = false)
    private String publicId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_cover", nullable = false)
    private boolean cover;

    public ServiceMedia(ServiceListing service, ProjectMediaType mediaType, String url, String publicId,
            int displayOrder) {
        this.service = service;
        this.mediaType = mediaType;
        this.url = url;
        this.publicId = publicId;
        this.displayOrder = displayOrder;
    }
}
