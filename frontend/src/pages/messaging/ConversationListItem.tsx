import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import type { ConversationSummaryResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function formatDate(value: string) {
  const date = new Date(value);
  const isToday = date.toDateString() === new Date().toDateString();
  return isToday
    ? date.toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" })
    : date.toLocaleDateString("en-IN", { day: "numeric", month: "short" });
}

interface ConversationListItemProps {
  conversation: ConversationSummaryResponse;
  active: boolean;
  onClick: () => void;
}

export function ConversationListItem({ conversation, active, onClick }: ConversationListItemProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex w-full items-start gap-3 rounded-lg px-3 py-2.5 text-left transition-colors",
        active ? "bg-accent" : "hover:bg-muted/60"
      )}
    >
      <Avatar className="size-10 shrink-0">
        {conversation.otherParticipantProfileImageUrl ? (
          <AvatarImage src={conversation.otherParticipantProfileImageUrl} alt={conversation.otherParticipantName} />
        ) : null}
        <AvatarFallback>{getInitials(conversation.otherParticipantName)}</AvatarFallback>
      </Avatar>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <p className="truncate text-sm font-medium text-foreground">{conversation.serviceTitle}</p>
          {conversation.lastMessageAt ? (
            <span className="shrink-0 text-[11px] text-muted-foreground">{formatDate(conversation.lastMessageAt)}</span>
          ) : null}
        </div>
        <p className="truncate text-xs text-muted-foreground">{conversation.otherParticipantName}</p>
        <div className="mt-0.5 flex items-center justify-between gap-2">
          <p className="truncate text-xs text-muted-foreground">
            {conversation.lastMessagePreview ?? "Start the conversation"}
          </p>
          {conversation.unreadCount > 0 ? (
            <span className="flex h-4.5 min-w-4.5 shrink-0 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold text-primary-foreground">
              {conversation.unreadCount > 99 ? "99+" : conversation.unreadCount}
            </span>
          ) : null}
        </div>
      </div>
    </button>
  );
}
