import { ApiError } from "@/lib/api/ApiError";
import type { ApiErrorBody } from "@/lib/api/types";

export const TOKEN_STORAGE_KEY = "studen.accessToken";
export const UNAUTHORIZED_EVENT = "studen:unauthorized";

const BASE_URL = import.meta.env.VITE_API_BASE_URL as string;

interface ApiFetchOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  auth?: boolean;
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { method = "GET", body, auth = true } = options;

  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (auth) {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
  }

  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.ok) {
    if (res.status === 204) {
      return undefined as T;
    }
    return (await res.json()) as T;
  }

  let errorBody: ApiErrorBody;
  try {
    errorBody = (await res.json()) as ApiErrorBody;
  } catch {
    errorBody = {
      timestamp: new Date().toISOString(),
      status: res.status,
      error: "UNKNOWN_ERROR",
      message: res.statusText || "Something went wrong",
      path,
    };
  }

  if (res.status === 401) {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }

  throw new ApiError(errorBody);
}
