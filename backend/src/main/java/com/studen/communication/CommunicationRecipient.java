package com.studen.communication;

import com.studen.common.entity.BaseEntity;
import com.studen.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per (campaign, user, channel) — the DB unique constraint on that triple (V24) is the
 * entire duplicate-prevention mechanism described in the spec: no matter how an AND/OR filter, a
 * scheduler retry, or a resolve-again produces a candidate send, only one row can ever exist for
 * a given student on a given channel for a given campaign. {@code recipientEmail} is a snapshot
 * for audit/history display only — {@code EmailService} always sends to the live
 * {@code User.email} at dispatch time, never to this stored copy.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "communication_recipients",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "user_id", "channel"}))
public class CommunicationRecipient extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private CommunicationCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipientChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecipientStatus status = RecipientStatus.QUEUED;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "clicked_at")
    private Instant clickedAt;

    public CommunicationRecipient(CommunicationCampaign campaign, User user, RecipientChannel channel,
            String recipientEmail) {
        this.campaign = campaign;
        this.user = user;
        this.channel = channel;
        this.recipientEmail = recipientEmail;
    }
}
