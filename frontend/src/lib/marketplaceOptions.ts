import type { MarketplaceAvailability, MarketplaceCategory, MarketplaceResultType, MarketplaceSort } from "@/lib/api/types";

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
  { value: "ARTS_PERFORMANCE", label: "Arts & Performance" },
  { value: "SPORTS_FITNESS", label: "Sports & Fitness" },
  { value: "SCIENCE_RESEARCH", label: "Science & Research" },
  { value: "LANGUAGES", label: "Languages" },
  { value: "PRACTICAL_TECHNICAL", label: "Practical & Technical" },
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

export const MARKETPLACE_RESULT_TYPE_OPTIONS: { value: "ALL" | MarketplaceResultType; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "STUDENT", label: "Students" },
  { value: "SERVICE", label: "Services" },
];

// A delivery <select> value: a preset number of days, or "custom" to reveal a free-entry number
// input. Kept as strings throughout the form since <select> values are always strings.
export const MARKETPLACE_DELIVERY_OPTIONS: { value: string; label: string }[] = [
  { value: "1", label: "1 day" },
  { value: "2", label: "2 days" },
  { value: "3", label: "3 days" },
  { value: "5", label: "5 days" },
  { value: "7", label: "7 days" },
  { value: "14", label: "14 days" },
  { value: "custom", label: "Custom" },
];

export const SERVICE_LINK_PRESETS = ["GitHub", "Live Demo", "Behance", "Figma", "Website", "YouTube", "Other"];
