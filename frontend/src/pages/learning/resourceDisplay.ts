import { FileText, Link2, NotebookText, Video, type LucideIcon } from "lucide-react";

import type { ResourceProgressStatus, ResourceStatus, ResourceType } from "@/lib/api/resourceTypes";

export const RESOURCE_TYPE_LABEL: Record<ResourceType, string> = {
  PDF: "PDF",
  EXTERNAL_LINK: "External Link",
  VIDEO: "Video",
  DOCUMENT: "Document",
  NOTES: "Notes",
};

export const RESOURCE_TYPE_OPTIONS: { value: ResourceType; label: string }[] = (
  Object.keys(RESOURCE_TYPE_LABEL) as ResourceType[]
).map((value) => ({ value, label: RESOURCE_TYPE_LABEL[value] }));

export function resourceTypeIcon(type: ResourceType): LucideIcon {
  switch (type) {
    case "PDF":
    case "DOCUMENT":
      return FileText;
    case "VIDEO":
      return Video;
    case "NOTES":
      return NotebookText;
    case "EXTERNAL_LINK":
      return Link2;
  }
}

// The label on the primary action button for a resource card/detail page.
export function resourceActionLabel(type: ResourceType): string {
  switch (type) {
    case "PDF":
      return "View PDF";
    case "DOCUMENT":
      return "View Document";
    case "VIDEO":
      return "Watch";
    case "EXTERNAL_LINK":
      return "Open Resource";
    case "NOTES":
      return "Read";
  }
}

export function resourceStatusLabel(status: ResourceStatus): string {
  switch (status) {
    case "DRAFT":
      return "Draft";
    case "PUBLISHED":
      return "Published";
    case "ARCHIVED":
      return "Archived";
  }
}

export function resourceStatusBadgeVariant(status: ResourceStatus): "default" | "secondary" | "outline" | "destructive" {
  switch (status) {
    case "PUBLISHED":
      return "default";
    case "ARCHIVED":
      return "destructive";
    default:
      return "outline";
  }
}

export function progressStatusLabel(status: ResourceProgressStatus): string {
  switch (status) {
    case "NOT_STARTED":
      return "Not Started";
    case "IN_PROGRESS":
      return "In Progress";
    case "COMPLETED":
      return "Completed";
  }
}

export function progressStatusBadgeVariant(status: ResourceProgressStatus): "default" | "secondary" | "outline" {
  switch (status) {
    case "COMPLETED":
      return "default";
    case "IN_PROGRESS":
      return "secondary";
    case "NOT_STARTED":
      return "outline";
  }
}
