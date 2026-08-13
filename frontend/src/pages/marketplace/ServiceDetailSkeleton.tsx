import { Skeleton } from "@/components/ui/skeleton";

/** Skeleton layout shown while a service detail page is loading, matching ServiceDetailView's
 * general shape (cover, title/price row, provider chip, description) to avoid a blank screen. */
export function ServiceDetailSkeleton() {
  return (
    <div className="space-y-4" aria-hidden="true">
      <Skeleton className="aspect-[16/9] w-full rounded-xl" />
      <div className="space-y-2">
        <Skeleton className="h-5 w-28 rounded-full" />
        <Skeleton className="h-6 w-2/3" />
        <div className="flex gap-3">
          <Skeleton className="h-5 w-20" />
          <Skeleton className="h-5 w-24" />
        </div>
      </div>
      <Skeleton className="h-9 w-40 rounded-lg" />
      <div className="flex flex-wrap gap-2">
        <Skeleton className="h-5 w-16 rounded-full" />
        <Skeleton className="h-5 w-16 rounded-full" />
        <Skeleton className="h-5 w-16 rounded-full" />
      </div>
      <div className="space-y-2">
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-3/4" />
      </div>
    </div>
  );
}
