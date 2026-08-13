-- Phase 6.7: request-linked messaging between the two parties of an ACCEPTED service_request.
CREATE TABLE conversations (
    id                  UUID PRIMARY KEY,
    service_request_id  UUID NOT NULL UNIQUE REFERENCES service_requests(id) ON DELETE CASCADE,
    requester_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- UNIQUE on service_request_id is the DB-level guarantee behind "at most one conversation per
-- request" (see ConversationService.getOrCreateConversation) — a concurrent double-click on
-- "Message Provider" hits this constraint on the loser's insert, which is caught and turned into
-- a re-select of the winner's row rather than a duplicate conversation.
-- requester_id/provider_id are denormalized here the same way service_requests denormalizes
-- provider_id off of services — every authorization/list query becomes a direct column check
-- instead of a join through service_requests.

CREATE INDEX idx_conversations_requester_id ON conversations (requester_id);
CREATE INDEX idx_conversations_provider_id ON conversations (provider_id);

CREATE TABLE messages (
    id               UUID PRIMARY KEY,
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content          TEXT NOT NULL,
    read_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation_id_created_at ON messages (conversation_id, created_at);
