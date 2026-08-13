import type { OrderStatus } from "@/lib/api/types";

const STATUS_LABELS: Record<OrderStatus, string> = {
  IN_PROGRESS: "In Progress",
  WORK_SUBMITTED: "Work Submitted",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

const STATUS_VARIANTS: Record<OrderStatus, "default" | "secondary" | "destructive" | "outline"> = {
  IN_PROGRESS: "default",
  WORK_SUBMITTED: "default",
  COMPLETED: "secondary",
  CANCELLED: "destructive",
};

export function orderStatusLabel(status: OrderStatus) {
  return STATUS_LABELS[status];
}

export function orderStatusVariant(status: OrderStatus) {
  return STATUS_VARIANTS[status];
}
