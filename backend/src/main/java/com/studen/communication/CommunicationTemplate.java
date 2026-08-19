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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "communication_templates")
public class CommunicationTemplate extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommunicationCategory category;

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

    @Column(nullable = false)
    private boolean archived = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    public CommunicationTemplate(String name, CommunicationCategory category, User createdBy) {
        this.name = name;
        this.category = category;
        this.createdBy = createdBy;
    }
}
