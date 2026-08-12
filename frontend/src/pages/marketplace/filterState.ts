import type { MarketplaceAvailability, MarketplaceCategory } from "@/lib/api/types";

export interface MarketplaceFilterState {
  category: MarketplaceCategory | "";
  location: string;
  availability: MarketplaceAvailability | "";
  skill: string;
}

export const EMPTY_FILTERS: MarketplaceFilterState = {
  category: "",
  location: "",
  availability: "",
  skill: "",
};

export function hasActiveFilters(filters: MarketplaceFilterState): boolean {
  return Boolean(filters.category || filters.location || filters.availability || filters.skill);
}
