import { ApiError } from "@/lib/api/ApiError";
import type { ApiErrorBody, AuthResponse } from "@/lib/api/types";

export const TOKEN_STORAGE_KEY = "studen.accessToken";
export const UNAUTHORIZED_EVENT = "studen:unauthorized";

const BASE_URL = import.meta.env.VITE_API_BASE_URL as string;

interface ApiFetchOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  auth?: boolean;
}

// A single in-flight refresh is shared by every caller that hits a 401 at the same time, so five
// concurrent expired-token requests trigger exactly one POST /auth/refresh instead of five. The
// check-and-assign below is safe without a lock: it runs synchronously (no `await` in between), so
// two "concurrent" callers can never both see `refreshPromise` as null.
let refreshPromise: Promise<string | null> | null = null;

/**
 * Exchanges the refresh-token cookie for a new access token. Used both by apiFetch's own 401
 * retry logic and directly by AuthProvider on app startup to restore a session whose access token
 * is missing or has expired (e.g. the browser was closed and reopened after 15+ minutes).
 */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function performRefresh(): Promise<string | null> {
  try {
    const res = await fetch(`${BASE_URL}/auth/refresh`, {
      method: "POST",
      credentials: "include",
    });
    if (!res.ok) {
      return null;
    }
    const data = (await res.json()) as AuthResponse;
    localStorage.setItem(TOKEN_STORAGE_KEY, data.accessToken);
    return data.accessToken;
  } catch {
    // Network failure while refreshing must not look like an invalid session — the caller
    // treats a null return as "could not refresh right now", not "log the user out".
    return null;
  }
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  return performFetch<T>(path, options, false);
}

async function performFetch<T>(path: string, options: ApiFetchOptions, isRetry: boolean): Promise<T> {
  const { method = "GET", body, auth = true } = options;
  const isFormData = body instanceof FormData;

  const headers: Record<string, string> = {};
  if (body !== undefined && !isFormData) {
    // FormData bodies must NOT get an explicit Content-Type: the browser sets
    // multipart/form-data with the correct boundary itself when it's left unset.
    headers["Content-Type"] = "application/json";
  }
  if (auth) {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
  }

  let res: Response;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      // Required cross-origin (Vercel frontend, Render backend) both to send the HttpOnly refresh
      // cookie on /auth/refresh and /auth/logout, and to let the browser store it from the
      // Set-Cookie on /auth/login, /auth/register, and /auth/refresh responses.
      credentials: "include",
      body: isFormData ? body : body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // fetch() itself threw: the backend is unreachable (offline, DNS failure, Render cold-start
    // timeout, etc). This is never an authentication failure, so the session must be left alone.
    throw new ApiError({
      timestamp: new Date().toISOString(),
      status: 0,
      error: "NETWORK_ERROR",
      message: "StuDen is temporarily unavailable. Please try again.",
      path,
    });
  }

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

  // A 401 on an authenticated request almost always just means the short-lived access token
  // expired mid-session — the normal, expected case, not a security event. Try exactly one
  // silent refresh-and-retry before giving up. `isRetry` guarantees this only ever happens once
  // per original call, so a request that still 401s right after a successful refresh can't loop.
  if (res.status === 401 && auth && !isRetry) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      return performFetch<T>(path, options, true);
    }
  }

  if (res.status === 401 && auth) {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
  }

  throw new ApiError(errorBody);
}
