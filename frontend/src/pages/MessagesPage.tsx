import { MessageCircle } from "lucide-react";
import { useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { useUnreadMessages } from "@/features/messaging/useUnreadMessages";
import { listConversations } from "@/lib/api/endpoints/messaging";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { ConversationListItem } from "@/pages/messaging/ConversationListItem";
import { ConversationsSkeleton } from "@/pages/messaging/ConversationsSkeleton";
import { ConversationThread } from "@/pages/messaging/ConversationThread";

const LIST_POLL_INTERVAL_MS = 15000;

export function MessagesPage() {
  const { conversationId } = useParams<{ conversationId?: string }>();
  const navigate = useNavigate();
  const unread = useUnreadMessages();
  const { data: conversations, error, loading, refetch } = useAsync(listConversations, []);

  useEffect(() => {
    const interval = window.setInterval(refetch, LIST_POLL_INTERVAL_MS);
    return () => window.clearInterval(interval);
  }, [refetch]);

  // Refreshes the nav badge once when leaving the Messages page — see UnreadMessagesProvider for
  // why this is a targeted refresh rather than background polling.
  useEffect(() => {
    return () => unread.refetch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="flex h-[70vh] min-h-[420px] overflow-hidden rounded-xl border border-border bg-card sm:h-[75vh] lg:h-[80vh]">
      <div
        className={cn(
          "w-full flex-col border-border lg:flex lg:w-80 lg:shrink-0 lg:border-r",
          conversationId ? "hidden lg:flex" : "flex"
        )}
      >
        <div className="border-b border-border px-4 py-3">
          <h1 className="text-lg font-semibold text-foreground">Messages</h1>
        </div>
        <div className="flex-1 overflow-y-auto p-2">
          {loading ? (
            <ConversationsSkeleton />
          ) : error ? (
            <ErrorState message={error.message} onRetry={refetch} />
          ) : conversations && conversations.length > 0 ? (
            <div className="space-y-1">
              {conversations.map((conversation) => (
                <ConversationListItem
                  key={conversation.id}
                  conversation={conversation}
                  active={conversation.id === conversationId}
                  onClick={() => navigate(ROUTES.conversationDetail(conversation.id))}
                />
              ))}
            </div>
          ) : (
            <EmptyState icon={MessageCircle} title="No conversations yet" />
          )}
        </div>
      </div>

      <div className={cn("min-w-0 flex-1 flex-col", conversationId ? "flex" : "hidden lg:flex")}>
        {conversationId ? (
          <ConversationThread
            conversationId={conversationId}
            onBack={() => navigate(ROUTES.messages)}
            onMessageActivity={refetch}
          />
        ) : (
          <div className="flex h-full items-center justify-center px-6 text-center text-sm text-muted-foreground">
            Select a conversation to start messaging.
          </div>
        )}
      </div>
    </div>
  );
}
