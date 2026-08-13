import { ArrowLeft } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { useUnreadMessages } from "@/features/messaging/useUnreadMessages";
import { getConversation, listMessages, markConversationRead, sendMessage } from "@/lib/api/endpoints/messaging";
import type { MessageResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { MessageBubble } from "@/pages/messaging/MessageBubble";
import { MessageComposer } from "@/pages/messaging/MessageComposer";

const POLL_INTERVAL_MS = 4000;

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

interface ConversationThreadProps {
  conversationId: string;
  onBack?: () => void;
  onMessageActivity?: () => void;
}

export function ConversationThread({ conversationId, onBack, onMessageActivity }: ConversationThreadProps) {
  const { data: conversation, error, loading } = useAsync(() => getConversation(conversationId), [conversationId]);
  const [messages, setMessages] = useState<MessageResponse[] | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const unread = useUnreadMessages();

  useEffect(() => {
    let cancelled = false;
    setMessages(null);

    function poll() {
      listMessages(conversationId)
        .then((data) => {
          if (!cancelled) setMessages(data);
        })
        .catch(() => {
          // Transient poll failures aren't worth surfacing — the next tick retries.
        });
    }

    poll();
    markConversationRead(conversationId)
      .then(() => unread.refetch())
      .catch(() => {
        // Best-effort — the badge just stays stale until the next successful mark-read.
      });

    const interval = window.setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
    // unread.refetch is stable across renders (see UnreadMessagesProvider) and re-running this
    // effect on every render would restart polling constantly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversationId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages?.length]);

  async function handleSend(content: string) {
    const created = await sendMessage(conversationId, content);
    setMessages((prev) => (prev ? [...prev, created] : [created]));
    onMessageActivity?.();
  }

  if (loading) {
    return <LoadingState label="Loading conversation..." />;
  }

  if (error || !conversation) {
    return (
      <div className="p-6">
        <ErrorState title="Conversation not found" message="This conversation doesn't exist, or you don't have access to it." />
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-3 border-b border-border px-4 py-3">
        {onBack ? (
          <button type="button" onClick={onBack} aria-label="Back to conversations" className="text-muted-foreground lg:hidden">
            <ArrowLeft className="size-5" />
          </button>
        ) : null}
        <Avatar className="size-9 shrink-0">
          {conversation.otherParticipantProfileImageUrl ? (
            <AvatarImage src={conversation.otherParticipantProfileImageUrl} alt={conversation.otherParticipantName} />
          ) : null}
          <AvatarFallback>{getInitials(conversation.otherParticipantName)}</AvatarFallback>
        </Avatar>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-foreground">{conversation.serviceTitle}</p>
          <p className="truncate text-xs text-muted-foreground">with {conversation.otherParticipantName}</p>
        </div>
      </div>

      <div className="flex items-center justify-between gap-2 border-b border-border bg-muted/30 px-4 py-2">
        <div className="min-w-0">
          <p className="truncate text-xs font-medium text-foreground">
            {conversation.servicePriceAmount != null ? `₹${conversation.servicePriceAmount.toLocaleString("en-IN")}` : conversation.serviceTitle}
          </p>
          <Badge variant="secondary" className="mt-0.5">
            Accepted Service Request
          </Badge>
        </div>
        <Link
          to={ROUTES.serviceRequestDetail(conversation.serviceRequestId)}
          className="shrink-0 text-xs font-medium text-primary hover:underline"
        >
          View Request
        </Link>
      </div>

      <div className="flex-1 space-y-2 overflow-y-auto px-4 py-3">
        {messages == null ? (
          <LoadingState label="Loading messages..." />
        ) : messages.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-1 text-center text-sm text-muted-foreground">
            <p className="font-medium text-foreground">Start the conversation</p>
            <p>Send a message to discuss the project.</p>
          </div>
        ) : (
          messages.map((message) => <MessageBubble key={message.id} message={message} />)
        )}
        <div ref={bottomRef} />
      </div>

      <MessageComposer onSend={handleSend} />
    </div>
  );
}
