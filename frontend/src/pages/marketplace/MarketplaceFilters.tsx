import { Input } from "@/components/ui/input";
import type { MarketplaceSort } from "@/lib/api/types";
import {
  MARKETPLACE_AVAILABILITY_OPTIONS,
  MARKETPLACE_CATEGORY_OPTIONS,
  MARKETPLACE_LOCATION_OPTIONS,
  MARKETPLACE_SORT_OPTIONS,
} from "@/lib/marketplaceOptions";
import { cn } from "@/lib/utils";
import type { MarketplaceFilterState } from "@/pages/marketplace/filterState";
import { MARKETPLACE_SELECT_CLASS } from "@/pages/marketplace/marketplaceSelectClass";

interface MarketplaceFiltersProps {
  filters: MarketplaceFilterState;
  onFiltersChange: (filters: MarketplaceFilterState) => void;
  sort: MarketplaceSort;
  onSortChange: (sort: MarketplaceSort) => void;
  // Price/delivery filters only make sense for services — Student results have neither field, so
  // they're hidden rather than forced onto a tab they don't apply to.
  showServiceFilters?: boolean;
}

/** Desktop-only inline filter row. Mobile uses MarketplaceFilterSheet + a separate sort trigger
 * instead of squeezing this same row onto a small screen. */
export function MarketplaceFilters({
  filters,
  onFiltersChange,
  sort,
  onSortChange,
  showServiceFilters,
}: MarketplaceFiltersProps) {
  function update<K extends keyof MarketplaceFilterState>(key: K, value: MarketplaceFilterState[K]) {
    onFiltersChange({ ...filters, [key]: value });
  }

  return (
    <div className="hidden flex-wrap items-center gap-2 lg:flex">
      <select
        value={filters.category}
        onChange={(e) => update("category", e.target.value as MarketplaceFilterState["category"])}
        className={cn(MARKETPLACE_SELECT_CLASS, "w-auto min-w-36")}
      >
        <option value="">All categories</option>
        {MARKETPLACE_CATEGORY_OPTIONS.map((c) => (
          <option key={c.value} value={c.value}>
            {c.label}
          </option>
        ))}
      </select>

      <select
        value={filters.location}
        onChange={(e) => update("location", e.target.value)}
        className={cn(MARKETPLACE_SELECT_CLASS, "w-auto min-w-36")}
      >
        <option value="">Any location</option>
        {MARKETPLACE_LOCATION_OPTIONS.map((loc) => (
          <option key={loc} value={loc}>
            {loc}
          </option>
        ))}
      </select>

      <select
        value={filters.availability}
        onChange={(e) => update("availability", e.target.value as MarketplaceFilterState["availability"])}
        className={cn(MARKETPLACE_SELECT_CLASS, "w-auto min-w-40")}
      >
        <option value="">Any availability</option>
        {MARKETPLACE_AVAILABILITY_OPTIONS.map((a) => (
          <option key={a.value} value={a.value}>
            {a.label}
          </option>
        ))}
      </select>

      <Input
        value={filters.skill}
        onChange={(e) => update("skill", e.target.value)}
        placeholder="Skill (e.g. React)"
        className="h-9 w-auto min-w-36"
      />

      {showServiceFilters ? (
        <>
          <Input
            type="number"
            min={0}
            value={filters.minPrice}
            onChange={(e) => update("minPrice", e.target.value)}
            placeholder="Min ₹"
            className="h-9 w-auto min-w-20"
          />
          <Input
            type="number"
            min={0}
            value={filters.maxPrice}
            onChange={(e) => update("maxPrice", e.target.value)}
            placeholder="Max ₹"
            className="h-9 w-auto min-w-20"
          />
          <Input
            type="number"
            min={1}
            value={filters.maxDeliveryDays}
            onChange={(e) => update("maxDeliveryDays", e.target.value)}
            placeholder="Delivery ≤ days"
            className="h-9 w-auto min-w-28"
          />
        </>
      ) : null}

      <select
        value={sort}
        onChange={(e) => onSortChange(e.target.value as MarketplaceSort)}
        className={cn(MARKETPLACE_SELECT_CLASS, "ml-auto w-auto min-w-36")}
      >
        {MARKETPLACE_SORT_OPTIONS.map((s) => (
          <option key={s.value} value={s.value}>
            {s.label}
          </option>
        ))}
      </select>
    </div>
  );
}
