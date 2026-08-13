package com.studen.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// No senderId/conversationId field here — the sender is always the authenticated principal and
// the conversation is always the {id} path variable, both derived server-side, never accepted
// from the client. See ConversationService.sendMessage.
public record CreateMessageRequest(

        @NotBlank(message = "Message can't be empty")
        @Size(max = 2000, message = "Message must be at most 2000 characters")
        String content) {
}
