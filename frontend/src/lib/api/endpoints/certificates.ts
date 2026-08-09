import { apiFetch } from "@/lib/api/client";
import type { CertificateRequest, CertificateResponse } from "@/lib/api/types";

export function listCertificates() {
  return apiFetch<CertificateResponse[]>("/users/me/certificates");
}

export function createCertificate(payload: CertificateRequest) {
  return apiFetch<CertificateResponse>("/users/me/certificates", { method: "POST", body: payload });
}

export function updateCertificate(id: string, payload: CertificateRequest) {
  return apiFetch<CertificateResponse>(`/users/me/certificates/${id}`, { method: "PUT", body: payload });
}

export function deleteCertificate(id: string) {
  return apiFetch<void>(`/users/me/certificates/${id}`, { method: "DELETE" });
}
