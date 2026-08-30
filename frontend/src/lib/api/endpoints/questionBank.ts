import { apiFetch } from "@/lib/api/client";
import type {
  DuplicateWarning,
  ImportConfirmRequest,
  ImportConfirmResponse,
  ImportParseResponse,
  PageResponse,
  QuestionBankStats,
  QuestionListParams,
  QuestionRequest,
  QuestionResponse,
  QuestionSummaryResponse,
  TopicResponse,
} from "@/lib/api/types";

export function listQuestions(params: QuestionListParams) {
  const query = new URLSearchParams();
  if (params.skillId) query.set("skillId", params.skillId);
  if (params.topicId) query.set("topicId", params.topicId);
  if (params.difficulty) query.set("difficulty", params.difficulty);
  if (params.type) query.set("type", params.type);
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  return apiFetch<PageResponse<QuestionSummaryResponse>>(`/admin/questions${qs ? `?${qs}` : ""}`);
}

export function getQuestionBankStats() {
  return apiFetch<QuestionBankStats>("/admin/questions/stats");
}

export async function checkDuplicateQuestion(skillId: string, text: string) {
  const query = new URLSearchParams({ skillId, text });
  // A 204 (no duplicate) resolves as `undefined`, not `null` — apiFetch only special-cases 204
  // by returning `undefined`, so this normalizes both "no duplicate" cases to `null` for callers.
  const result = await apiFetch<DuplicateWarning | undefined>(
    `/admin/questions/check-duplicate?${query.toString()}`
  );
  return result ?? null;
}

export function getQuestion(id: string) {
  return apiFetch<QuestionResponse>(`/admin/questions/${id}`);
}

export function createQuestion(payload: QuestionRequest) {
  return apiFetch<QuestionResponse>("/admin/questions", { method: "POST", body: payload });
}

export function updateQuestion(id: string, payload: QuestionRequest) {
  return apiFetch<QuestionResponse>(`/admin/questions/${id}`, { method: "PATCH", body: payload });
}

export function deleteQuestion(id: string) {
  return apiFetch<void>(`/admin/questions/${id}`, { method: "DELETE" });
}

export function submitQuestionForReview(id: string) {
  return apiFetch<QuestionResponse>(`/admin/questions/${id}/submit-review`, { method: "POST" });
}

export function publishQuestion(id: string) {
  return apiFetch<QuestionResponse>(`/admin/questions/${id}/publish`, { method: "POST" });
}

export function archiveQuestion(id: string) {
  return apiFetch<QuestionResponse>(`/admin/questions/${id}/archive`, { method: "POST" });
}

export function createNewQuestionVersion(id: string) {
  return apiFetch<QuestionResponse>(`/admin/questions/${id}/new-version`, { method: "POST" });
}

export function listTopics(skillId: string) {
  const query = new URLSearchParams({ skillId });
  return apiFetch<TopicResponse[]>(`/admin/topics?${query.toString()}`);
}

export function createTopic(skillId: string, name: string) {
  return apiFetch<TopicResponse>("/admin/topics", { method: "POST", body: { skillId, name } });
}

export function parseQuestionImport(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch<ImportParseResponse>("/admin/questions/import/parse", { method: "POST", body: formData });
}

export function confirmQuestionImport(payload: ImportConfirmRequest) {
  return apiFetch<ImportConfirmResponse>("/admin/questions/import/confirm", { method: "POST", body: payload });
}
