import { MessageCircle } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/EmptyState";
import { listConversations } from "@/lib/api/endpoints/messaging";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";

const PREVIEW_COUNT = 2;

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

/** Own useAsync call rather than folding into DashboardPage's Promise.all — keeps this card's
 * fetch independent of (and non-blocking for) the rest of the dashboard's initial load, same
 * pattern as ServiceRequestsCard. */
export function RecentMessages() {
  const navigate = useNavigate();
  const { data, loading } = useAsync(listConversations, []);

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Recent Messages</CardTitle>
        <button
          type="button"
          onClick={() => navigate(ROUTES.messages)}
          className="text-sm font-medium text-primary hover:underline"
        >
          View all
        </button>
      </CardHeader>
      <CardContent>
        {loading ? null : data && data.length > 0 ? (
          <div className="space-y-2">
            {data.slice(0, PREVIEW_COUNT).map((conversation) => (
              <button
                key={conversation.id}
                type="button"
                onClick={() => navigate(ROUTES.conversationDetail(conversation.id))}
                className="flex w-full items-center gap-3 rounded-lg border border-border p-3 text-left hover:bg-muted/50"
              >
                <Avatar className="size-8 shrink-0">
                  {conversation.otherParticipantProfileImageUrl ? (
                    <AvatarImage src={conversation.otherParticipantProfileImageUrl} alt={conversation.otherParticipantName} />
                  ) : null}
                  <AvatarFallback className="text-xs">{getInitials(conversation.otherParticipantName)}</AvatarFallback>
                </Avatar>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-foreground">{conversation.serviceTitle}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {conversation.lastMessagePreview ?? `with ${conversation.otherParticipantName}`}
                  </p>
                </div>
                {conversation.unreadCount > 0 ? (
                  <Badge variant="default" className="shrink-0">
                    {conversation.unreadCount}
                  </Badge>
                ) : null}
              </button>
            ))}
          </div>
        ) : (
          <EmptyState icon={MessageCircle} title="No messages yet" />
        )}
      </CardContent>
    </Card>
  );
}
