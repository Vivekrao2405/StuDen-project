package com.studen.messaging;

import com.studen.booking.ServiceRequest;
import com.studen.common.entity.BaseEntity;
import com.studen.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The single conversation for one {@link ServiceRequest} once it reaches ACCEPTED. Not a general
 * social chat entity — {@code requester}/{@code provider} are always derived server-side from the
 * request at creation time (see {@code ConversationService.getOrCreateConversation}), never from
 * client input. A request has at most one conversation, enforced by a unique constraint on
 * {@code service_request_id} (V11 migration), not just an application-level check.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversations")
public class Conversation extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_request_id", nullable = false, unique = true)
    private ServiceRequest serviceRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    public Conversation(ServiceRequest serviceRequest, User requester, User provider) {
        this.serviceRequest = serviceRequest;
        this.requester = requester;
        this.provider = provider;
    }
}
