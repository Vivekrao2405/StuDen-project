import type { MarketplaceAvailability, MarketplaceCategory } from "@/lib/api/types";

export interface MarketplaceFilterState {
  category: MarketplaceCategory | "";
  location: string;
  availability: MarketplaceAvailability | "";
  skill: string;
  // Service-tab-only — kept as strings (empty = unset) to match the other free-text filter
  // fields; parsed to numbers only when building the search request.
  minPrice: string;
  maxPrice: string;
  maxDeliveryDays: string;
}

export const EMPTY_FILTERS: MarketplaceFilterState = {
  category: "",
  location: "",
  availability: "",
  skill: "",
  minPrice: "",
  maxPrice: "",
  maxDeliveryDays: "",
};

export function hasActiveFilters(filters: MarketplaceFilterState): boolean {
  return Boolean(
    filters.category ||
      filters.location ||
      filters.availability ||
      filters.skill ||
      filters.minPrice ||
      filters.maxPrice ||
      filters.maxDeliveryDays
  );
}
