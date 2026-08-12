import type { MarketplaceAvailability, MarketplaceCategory, MarketplaceSort } from "@/lib/api/types";

export const MARKETPLACE_CATEGORY_OPTIONS: { value: MarketplaceCategory; label: string }[] = [
  { value: "TECHNOLOGY", label: "Technology" },
  { value: "DESIGN_CREATIVE", label: "Design & Creative" },
  { value: "BUSINESS_FINANCE", label: "Business & Finance" },
  { value: "MARKETING", label: "Marketing" },
  { value: "WRITING_CONTENT", label: "Writing & Content" },
  { value: "EDUCATION_TUTORING", label: "Education & Tutoring" },
  { value: "VIDEO_MEDIA", label: "Video & Media" },
  { value: "DATA_ANALYTICS", label: "Data & Analytics" },
  { value: "ENGINEERING", label: "Engineering" },
  { value: "OTHER", label: "Other" },
];

export const MARKETPLACE_LOCATION_OPTIONS: string[] = [
  "Hyderabad",
  "Bengaluru",
  "Mumbai",
  "Delhi",
  "Chennai",
  "Pune",
  "Other",
];

export const MARKETPLACE_AVAILABILITY_OPTIONS: { value: MarketplaceAvailability; label: string }[] = [
  { value: "AVAILABLE", label: "Available" },
  { value: "NOT_AVAILABLE", label: "Not currently available" },
];

export const MARKETPLACE_SORT_OPTIONS: { value: MarketplaceSort; label: string }[] = [
  { value: "recommended", label: "Recommended" },
  { value: "newest", label: "Newest" },
  { value: "relevant", label: "Most Relevant" },
];
