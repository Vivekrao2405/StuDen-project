import { apiFetch, apiFetchBlob } from "@/lib/api/client";
import type { MyLearningResponse, ResourceProgress, StudentResource } from "@/lib/api/resourceTypes";

export function getMyLearning() {
  return apiFetch<MyLearningResponse>("/resources/my-learning");
}

export function getResource(id: string) {
  return apiFetch<StudentResource>(`/resources/${id}`);
}

// Fetches the PDF/DOCUMENT bytes for inline viewing (Content-Disposition: inline on the backend) —
// never navigate to this URL directly, only ever consume the Blob via URL.createObjectURL().
export function viewResource(id: string) {
  return apiFetchBlob(`/resources/${id}/file`);
}

// Explicit download — the only call that hits the attachment-disposition endpoint.
export function downloadResource(id: string) {
  return apiFetchBlob(`/resources/${id}/file/download`);
}

// updateLearningStatus concern: start/complete below only ever touch progress state, never a file.
export function startResource(id: string) {
  return apiFetch<ResourceProgress>(`/resources/${id}/start`, { method: "POST" });
}

export function completeResource(id: string) {
  return apiFetch<ResourceProgress>(`/resources/${id}/complete`, { method: "POST" });
}
