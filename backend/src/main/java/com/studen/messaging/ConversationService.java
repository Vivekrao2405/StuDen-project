package com.studen.messaging;

import com.studen.booking.ServiceRequest;
import com.studen.booking.ServiceRequestRepository;
import com.studen.booking.ServiceRequestStatus;
import com.studen.common.exception.ConflictException;
import com.studen.common.exception.RateLimitExceededException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.notification.NotificationType;
import com.studen.notification.Notifier;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final StudentPortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final Notifier notifier;
    private final int maxMessagesPerWindow;
    private final int rateLimitWindowMinutes;

    public ConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository,
            ServiceRequestRepository serviceRequestRepository, StudentPortfolioRepository portfolioRepository,
            UserRepository userRepository, Notifier notifier,
            @Value("${app.messaging.max-messages-per-window}") int maxMessagesPerWindow,
            @Value("${app.messaging.rate-limit-window-minutes}") int rateLimitWindowMinutes) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.portfolioRepository = portfolioRepository;
        this.userRepository = userRepository;
        this.notifier = notifier;
        this.maxMessagesPerWindow = maxMessagesPerWindow;
        this.rateLimitWindowMinutes = rateLimitWindowMinutes;
    }

    // Deliberately NOT @Transactional. ensureConversationExists's insert and the final
    // findByServiceRequestId read below each run in their own short, independent transaction
    // (Spring Data's default per-repository-call behavior). That's what makes the concurrent-
    // double-click race safe: a losing concurrent insert rolls back only its own tiny
    // transaction, and the subsequent read (a fresh transaction) always sees the winner's
    // already-committed row — whether this call won or lost the race. Wrapping this whole method
    // in one @Transactional would instead mark that single transaction rollback-only the instant
    // the insert throws, poisoning the fallback read too.
    public ConversationResponse getOrCreateConversation(UUID userId, UUID serviceRequestId) {
        ServiceRequest request = serviceRequestRepository.findByIdAndRequesterIdOrProviderId(serviceRequestId, userId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Service request not found"));

        if (request.getStatus() != ServiceRequestStatus.ACCEPTED) {
            throw new ConflictException("Messaging is only available for accepted requests.");
        }

        ensureConversationExists(request);

        Conversation conversation = conversationRepository.findByServiceRequestId(serviceRequestId)
                .orElseThrow(() -> new IllegalStateException("Conversation should exist after ensureConversationExists"));
        return toResponse(conversation, userId);
    }

    private void ensureConversationExists(ServiceRequest request) {
        if (conversationRepository.findByServiceRequestId(request.getId()).isPresent()) {
            return;
        }
        ServiceRequest requestRef = serviceRequestRepository.getReferenceById(request.getId());
        User requesterRef = userRepository.getReferenceById(request.getRequester().getId());
        User providerRef = userRepository.getReferenceById(request.getProvider().getId());
        try {
            conversationRepository.save(new Conversation(requestRef, requesterRef, providerRef));
        } catch (DataIntegrityViolationException ignored) {
            // Lost the race to a concurrent "Message Provider" click — the winner's row already
            // exists (enforced by the unique constraint on service_request_id), and the caller's
            // subsequent findByServiceRequestId picks it up.
        }
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listConversations(UUID userId) {
        List<Conversation> conversations = conversationRepository.findAllByRequesterIdOrProviderId(userId);
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();

        // Batch-fetch every conversation's messages once, reduced to "the latest one per
        // conversation" in Java — the same batch-then-reduce idiom already used for
        // media-by-service-id/portfolio-by-userId elsewhere in this codebase. Message volume per
        // conversation is small at this app's current scale; a GROUP BY MAX aggregate would be
        // the next step if that ever changes.
        Map<UUID, Message> lastMessageByConversationId = messageRepository
                .findAllByConversationIdInOrderByCreatedAtAsc(conversationIds).stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), m -> m, (first, second) -> second));

        Map<UUID, Long> unreadByConversationId = messageRepository.countUnreadByConversationIds(conversationIds, userId)
                .stream()
                .collect(Collectors.toMap(UnreadCountProjection::getConversationId, UnreadCountProjection::getCount));

        List<UUID> otherUserIds = conversations.stream().map(c -> otherParticipant(c, userId).getId()).distinct().toList();
        Map<UUID, StudentPortfolio> portfolioByUserId = portfolioRepository.findAllByUserIdIn(otherUserIds).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        return conversations.stream()
                .sorted(Comparator.<Conversation, Instant>comparing(
                        c -> {
                            Message last = lastMessageByConversationId.get(c.getId());
                            return last != null ? last.getCreatedAt() : c.getCreatedAt();
                        })
                        .reversed())
                .map(c -> {
                    Message last = lastMessageByConversationId.get(c.getId());
                    User other = otherParticipant(c, userId);
                    StudentPortfolio otherPortfolio = portfolioByUserId.get(other.getId());
                    ServiceRequest request = c.getServiceRequest();
                    return new ConversationSummaryResponse(
                            c.getId(),
                            request.getId(),
                            request.getServiceTitleSnapshot(),
                            request.getServicePriceSnapshot(),
                            request.getServiceCurrencySnapshot(),
                            other.getFullName(),
                            other.getProfileImageUrl(),
                            otherPortfolio == null ? null : otherPortfolio.getPublicSlug(),
                            otherPortfolio == null ? null : otherPortfolio.getHeadline(),
                            last == null ? null : last.getContent(),
                            last == null ? null : last.getCreatedAt(),
                            unreadByConversationId.getOrDefault(c.getId(), 0L),
                            c.getCreatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID userId, UUID conversationId) {
        Conversation conversation = findOwnConversation(userId, conversationId);
        return toResponse(conversation, userId);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> listMessages(UUID userId, UUID conversationId, Instant before) {
        findOwnConversation(userId, conversationId);

        List<Message> messages = before == null
                ? messageRepository.findTop30ByConversationIdOrderByCreatedAtDesc(conversationId)
                : messageRepository.findTop30ByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(conversationId, before);

        return messages.stream()
                .sorted(Comparator.comparing(Message::getCreatedAt))
                .map(m -> MessageResponse.from(m, userId))
                .toList();
    }

    @Transactional
    public MessageResponse sendMessage(UUID userId, UUID conversationId, CreateMessageRequest request) {
        Conversation conversation = findOwnConversation(userId, conversationId);

        // Defense-in-depth: re-verify the linked request is still ACCEPTED at send time, not just
        // at conversation-creation time. Status can never regress off ACCEPTED in this data model
        // (Phase 6.6 makes it terminal), so this can't currently fail in practice — kept anyway
        // per spec so the check exists independent of that invariant holding forever.
        if (conversation.getServiceRequest().getStatus() != ServiceRequestStatus.ACCEPTED) {
            throw new ConflictException("This conversation is no longer active.");
        }

        if (messageRepository.countBySenderIdAndCreatedAtAfter(userId,
                Instant.now().minus(Duration.ofMinutes(rateLimitWindowMinutes))) >= maxMessagesPerWindow) {
            throw new RateLimitExceededException("You're sending messages too quickly. Please wait a bit and try again.");
        }

        // request.content() is already guaranteed non-blank by @NotBlank on CreateMessageRequest
        // (checked before this method is even called) — trim() here is just normalizing the
        // stored value, not re-validating.
        String content = request.content().trim();

        User sender = userRepository.getReferenceById(userId);
        Message message = messageRepository.save(new Message(conversation, sender, content));

        UUID recipientId = otherParticipant(conversation, userId).getId();
        String senderName = conversation.getRequester().getId().equals(userId)
                ? conversation.getRequester().getFullName()
                : conversation.getProvider().getFullName();
        notifier.notify(recipientId, NotificationType.NEW_MESSAGE,
                "New message from " + senderName + " — " + conversation.getServiceRequest().getServiceTitleSnapshot(),
                conversation.getId());

        log.info("Message {} sent in conversation {} by {}", message.getId(), conversationId, userId);

        return MessageResponse.from(message, userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID conversationId) {
        findOwnConversation(userId, conversationId);
        messageRepository.markReadForRecipient(conversationId, userId, Instant.now());
    }

    private Conversation findOwnConversation(UUID userId, UUID conversationId) {
        return conversationRepository.findByIdAndRequesterIdOrProviderId(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    private User otherParticipant(Conversation conversation, UUID userId) {
        return conversation.getRequester().getId().equals(userId) ? conversation.getProvider() : conversation.getRequester();
    }

    private ConversationResponse toResponse(Conversation conversation, UUID userId) {
        User other = otherParticipant(conversation, userId);
        StudentPortfolio otherPortfolio = portfolioRepository.findByUserId(other.getId()).orElse(null);
        ServiceRequest request = conversation.getServiceRequest();
        return new ConversationResponse(
                conversation.getId(),
                request.getId(),
                request.getServiceTitleSnapshot(),
                request.getServicePriceSnapshot(),
                request.getServiceCurrencySnapshot(),
                other.getFullName(),
                other.getProfileImageUrl(),
                otherPortfolio == null ? null : otherPortfolio.getPublicSlug(),
                otherPortfolio == null ? null : otherPortfolio.getHeadline(),
                conversation.getCreatedAt());
    }
}
