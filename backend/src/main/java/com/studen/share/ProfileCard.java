package com.studen.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A generated card is a write-once artifact: unlike the rest of the ERD's entities it has no
 * updated_at column, so it does not extend BaseEntity.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "profile_cards")
public class ProfileCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_id", nullable = false)
    private ProfileShare share;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfileCardType type;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProfileCard(ProfileShare share, ProfileCardType type, String fileUrl) {
        this.share = share;
        this.type = type;
        this.fileUrl = fileUrl;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        if (generatedAt == null) {
            generatedAt = now;
        }
    }
}
