package com.studen.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    // Most-recent-first pages; the service reverses to chronological order before returning.
    List<Message> findTop30ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

    List<Message> findTop30ByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(UUID conversationId, Instant before);

    // Batch "last message per conversation" for the conversation-list summary view — one query for
    // however many conversations the user has (bounded, small at this app's current scale) rather
    // than one findTop1By... per row; the service groups this down to the single latest message
    // per conversation in Java, the same "batch-fetch then reduce in Java" idiom already used for
    // media-by-service-id/portfolio-by-userId elsewhere in this codebase.
    List<Message> findAllByConversationIdInOrderByCreatedAtAsc(List<UUID> conversationIds);

    // A real GROUP BY aggregate (unlike the above) since JPQL expresses this cleanly without a
    // window function — one query for every conversation's unread-by-me count.
    @Query("""
            select m.conversation.id as conversationId, count(m) as count
            from Message m
            where m.conversation.id in :conversationIds and m.sender.id <> :userId and m.readAt is null
            group by m.conversation.id
            """)
    List<UnreadCountProjection> countUnreadByConversationIds(@Param("conversationIds") List<UUID> conversationIds,
            @Param("userId") UUID userId);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(UUID conversationId, UUID userId);

    long countBySenderIdAndCreatedAtAfter(UUID senderId, Instant after);

    @Modifying(clearAutomatically = true)
    @Query("update Message m set m.readAt = :now where m.conversation.id = :conversationId and m.sender.id <> :userId and m.readAt is null")
    int markReadForRecipient(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId, @Param("now") Instant now);
}
