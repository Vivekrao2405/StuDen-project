import { X } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { MARKETPLACE_AVAILABILITY_OPTIONS, MARKETPLACE_CATEGORY_OPTIONS, MARKETPLACE_LOCATION_OPTIONS } from "@/lib/marketplaceOptions";
import { cn } from "@/lib/utils";
import { EMPTY_FILTERS, type MarketplaceFilterState } from "@/pages/marketplace/filterState";
import { MARKETPLACE_SELECT_CLASS } from "@/pages/marketplace/marketplaceSelectClass";

interface MarketplaceFilterSheetProps {
  open: boolean;
  onClose: () => void;
  filters: MarketplaceFilterState;
  onApply: (filters: MarketplaceFilterState) => void;
  // Price/delivery filters only make sense for services — Student results have neither field, so
  // they're hidden rather than forced onto a tab they don't apply to.
  showServiceFilters?: boolean;
}

/** Mobile-only bottom sheet, built the same way MobileNavDrawer.tsx already is (custom
 * fixed-position panel + backdrop) — there's no Sheet/Drawer primitive in this codebase, and
 * overriding Dialog's centered-popup styling would fight its own defaults more than reusing this
 * proven technique. */
export function MarketplaceFilterSheet({ open, onClose, filters, onApply, showServiceFilters }: MarketplaceFilterSheetProps) {
  const [draft, setDraft] = useState(filters);

  useEffect(() => {
    if (open) setDraft(filters);
  }, [open, filters]);

  useEffect(() => {
    if (!open) return;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  function update<K extends keyof MarketplaceFilterState>(key: K, value: MarketplaceFilterState[K]) {
    setDraft((prev) => ({ ...prev, [key]: value }));
  }

  function handleApply() {
    onApply(draft);
    onClose();
  }

  return (
    <div className={cn("fixed inset-0 z-50 lg:hidden", open ? "" : "pointer-events-none")} aria-hidden={!open}>
      <div
        className={cn("absolute inset-0 bg-black/40 transition-opacity", open ? "opacity-100" : "opacity-0")}
        onClick={onClose}
      />
      <div
        className={cn(
          "absolute inset-x-0 bottom-0 flex max-h-[80vh] flex-col gap-4 rounded-t-2xl bg-card p-5 shadow-xl transition-transform duration-200",
          open ? "translate-y-0" : "translate-y-full"
        )}
      >
        <div className="flex shrink-0 items-center justify-between">
          <h2 className="text-base font-semibold text-foreground">Filters</h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close filters"
            className="rounded-full p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            <X className="size-5" />
          </button>
        </div>

        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto">
          <div className="space-y-1.5">
            <label className="text-sm font-medium text-foreground">Category</label>
            <select
              value={draft.category}
              onChange={(e) => update("category", e.target.value as MarketplaceFilterState["category"])}
              className={MARKETPLACE_SELECT_CLASS}
            >
              <option value="">All categories</option>
              {MARKETPLACE_CATEGORY_OPTIONS.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <label className="text-sm font-medium text-foreground">Availability</label>
            <select
              value={draft.availability}
              onChange={(e) => update("availability", e.target.value as MarketplaceFilterState["availability"])}
              className={MARKETPLACE_SELECT_CLASS}
            >
              <option value="">Any availability</option>
              {MARKETPLACE_AVAILABILITY_OPTIONS.map((a) => (
                <option key={a.value} value={a.value}>
                  {a.label}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <label className="text-sm font-medium text-foreground">Location</label>
            <select
              value={draft.location}
              onChange={(e) => update("location", e.target.value)}
              className={MARKETPLACE_SELECT_CLASS}
            >
              <option value="">Any location</option>
              {MARKETPLACE_LOCATION_OPTIONS.map((loc) => (
                <option key={loc} value={loc}>
                  {loc}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <label className="text-sm font-medium text-foreground">Skill</label>
            <Input
              value={draft.skill}
              onChange={(e) => update("skill", e.target.value)}
              placeholder="e.g. React"
              className="h-9"
            />
          </div>

          {showServiceFilters ? (
            <>
              <div className="space-y-1.5">
                <label className="text-sm font-medium text-foreground">Price range (₹)</label>
                <div className="flex items-center gap-2">
                  <Input
                    type="number"
                    min={0}
                    value={draft.minPrice}
                    onChange={(e) => update("minPrice", e.target.value)}
                    placeholder="Min"
                    className="h-9"
                  />
                  <span className="text-sm text-muted-foreground">to</span>
                  <Input
                    type="number"
                    min={0}
                    value={draft.maxPrice}
                    onChange={(e) => update("maxPrice", e.target.value)}
                    placeholder="Max"
                    className="h-9"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label className="text-sm font-medium text-foreground">Delivery within</label>
                <Input
                  type="number"
                  min={1}
                  value={draft.maxDeliveryDays}
                  onChange={(e) => update("maxDeliveryDays", e.target.value)}
                  placeholder="e.g. 7 days"
                  className="h-9"
                />
              </div>
            </>
          ) : null}
        </div>

        <div className="flex shrink-0 gap-2 border-t border-border pt-4">
          <Button type="button" variant="outline" className="flex-1" onClick={() => setDraft(EMPTY_FILTERS)}>
            Reset
          </Button>
          <Button type="button" className="flex-1" onClick={handleApply}>
            Apply Filters
          </Button>
        </div>
      </div>
    </div>
  );
}
