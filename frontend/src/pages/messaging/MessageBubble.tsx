import type { MessageResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" });
}

// message.content is rendered as plain text via normal JSX interpolation — React escapes it by
// default, so a message containing "<script>" or any other markup is displayed literally rather
// than interpreted as HTML. Never swap this for dangerouslySetInnerHTML.
export function MessageBubble({ message }: { message: MessageResponse }) {
  return (
    <div className={cn("flex", message.mine ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[80%] rounded-2xl px-3.5 py-2 text-sm break-words whitespace-pre-wrap sm:max-w-[70%]",
          message.mine ? "bg-primary text-primary-foreground" : "bg-muted text-foreground"
        )}
      >
        {message.content}
        <div className={cn("mt-1 text-[10px]", message.mine ? "text-primary-foreground/70" : "text-muted-foreground")}>
          {formatTime(message.createdAt)}
        </div>
      </div>
    </div>
  );
}
