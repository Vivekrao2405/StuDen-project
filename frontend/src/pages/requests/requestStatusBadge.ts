import type { ServiceRequestStatus } from "@/lib/api/types";

const STATUS_LABELS: Record<ServiceRequestStatus, string> = {
  PENDING: "Pending",
  ACCEPTED: "Accepted",
  REJECTED: "Rejected",
  IN_PROGRESS: "In Progress",
  SUBMITTED: "Submitted",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

const STATUS_VARIANTS: Record<ServiceRequestStatus, "default" | "secondary" | "destructive" | "outline"> = {
  PENDING: "default",
  ACCEPTED: "default",
  REJECTED: "destructive",
  IN_PROGRESS: "default",
  SUBMITTED: "default",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
};

export function requestStatusLabel(status: ServiceRequestStatus) {
  return STATUS_LABELS[status];
}

export function requestStatusVariant(status: ServiceRequestStatus) {
  return STATUS_VARIANTS[status];
}
