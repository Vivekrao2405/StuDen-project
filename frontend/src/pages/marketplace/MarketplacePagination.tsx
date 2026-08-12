import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";

interface MarketplacePaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function MarketplacePagination({ page, totalPages, onPageChange }: MarketplacePaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-center gap-3 pt-2">
      <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
        <ChevronLeft className="size-4" /> Prev
      </Button>
      <span className="text-sm text-muted-foreground">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
        Next <ChevronRight className="size-4" />
      </Button>
    </div>
  );
}
