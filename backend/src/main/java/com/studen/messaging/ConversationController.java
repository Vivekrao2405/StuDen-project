package com.studen.messaging;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// No class-level @RequestMapping: getOrCreateConversation is a sub-resource of
// /api/v1/service-requests (owned by com.studen.booking's ServiceRequestController elsewhere, but
// implemented here since it's the messaging feature's own get-or-create logic), while everything
// else lives under /api/v1/messages/conversations — the two path families don't share a prefix.
@RestController
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/api/v1/service-requests/{requestId}/conversation")
    public ConversationResponse getOrCreateConversation(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID requestId) {
        return conversationService.getOrCreateConversation(principal.getId(), requestId);
    }

    @GetMapping("/api/v1/messages/conversations")
    public List<ConversationSummaryResponse> listConversations(@AuthenticationPrincipal UserPrincipal principal) {
        return conversationService.listConversations(principal.getId());
    }

    @GetMapping("/api/v1/messages/conversations/{conversationId}")
    public ConversationResponse getConversation(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId) {
        return conversationService.getConversation(principal.getId(), conversationId);
    }

    @GetMapping("/api/v1/messages/conversations/{conversationId}/messages")
    public List<MessageResponse> listMessages(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId, @RequestParam(required = false) Instant before) {
        return conversationService.listMessages(principal.getId(), conversationId, before);
    }

    @PostMapping("/api/v1/messages/conversations/{conversationId}/messages")
    public ResponseEntity<MessageResponse> sendMessage(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId, @Valid @RequestBody CreateMessageRequest request) {
        MessageResponse response = conversationService.sendMessage(principal.getId(), conversationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/v1/messages/conversations/{conversationId}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId) {
        conversationService.markRead(principal.getId(), conversationId);
        return ResponseEntity.noContent().build();
    }
}
