import { Skeleton } from "@/components/ui/skeleton";

/** Matches ConversationListItem's shape closely enough to avoid layout shift once results land —
 * same convention as MarketplaceResultsSkeleton/ServiceRequestsSkeleton. */
export function ConversationsSkeleton({ count = 5 }: { count?: number }) {
  return (
    <div className="space-y-1 p-1" aria-hidden="true">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="flex items-start gap-3 px-2 py-2.5">
          <Skeleton className="size-10 shrink-0 rounded-full" />
          <div className="flex-1 space-y-1.5">
            <Skeleton className="h-3.5 w-2/3" />
            <Skeleton className="h-3 w-1/3" />
            <Skeleton className="h-3 w-3/4" />
          </div>
        </div>
      ))}
    </div>
  );
}
