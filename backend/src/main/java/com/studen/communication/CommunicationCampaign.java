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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code filterJson}/message/channel fields stay mutable only while {@link CampaignStatus#DRAFT}
 * (enforced in {@code CommunicationService}, not here) — once recipients are resolved, this row
 * plus its {@link CommunicationRecipient} rows together ARE the frozen snapshot of what was sent,
 * to whom, and how; later template/segment edits never rewrite it because nothing here is a live
 * reference back into those tables at read time.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "communication_campaigns")
public class CommunicationCampaign extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommunicationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status = CampaignStatus.DRAFT;

    // Marketing sends check User.marketingOptOut in CampaignSendService; transactional-style
    // campaigns (product updates, assessment reminders, etc.) never do — this flag is the only
    // thing that distinguishes the two, and an admin cannot bypass it once set.
    @Column(name = "is_marketing", nullable = false)
    private boolean marketing = false;

    @Column(name = "filter_json", nullable = false, columnDefinition = "TEXT")
    private String filterJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private CommunicationTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id")
    private CommunicationSegment segment;

    @Column(name = "send_email", nullable = false)
    private boolean sendEmail;

    @Column(name = "send_push", nullable = false)
    private boolean sendPush;

    @Column(name = "send_inapp", nullable = false)
    private boolean sendInapp;

    @Column(name = "email_subject")
    private String emailSubject;

    @Column(name = "email_body_html", columnDefinition = "TEXT")
    private String emailBodyHtml;

    @Column(name = "push_title")
    private String pushTitle;

    @Column(name = "push_body")
    private String pushBody;

    @Column(name = "inapp_title")
    private String inappTitle;

    @Column(name = "inapp_body")
    private String inappBody;

    @Column(name = "cta_text")
    private String ctaText;

    @Column(name = "cta_url")
    private String ctaUrl;

    @Column(name = "resolved_recipient_count")
    private Integer resolvedRecipientCount;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    public CommunicationCampaign(String name, CommunicationCategory category, String filterJson, User createdBy) {
        this.name = name;
        this.category = category;
        this.filterJson = filterJson;
        this.createdBy = createdBy;
    }
}
