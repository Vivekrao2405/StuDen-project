package com.studen.communication;

import com.studen.common.entity.BaseEntity;
import com.studen.user.User;
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
 * Stores a filter DEFINITION, never a resolved recipient list — {@code filterJson} is re-parsed
 * and re-resolved by {@code AudienceService} every time this segment is previewed or a campaign is
 * built from it, so its membership/count always reflects current data.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "communication_segments")
public class CommunicationSegment extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "filter_json", nullable = false, columnDefinition = "TEXT")
    private String filterJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    public CommunicationSegment(String name, String description, String filterJson, User createdBy) {
        this.name = name;
        this.description = description;
        this.filterJson = filterJson;
        this.createdBy = createdBy;
    }
}
