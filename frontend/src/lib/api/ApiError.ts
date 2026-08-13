import type { ApiErrorBody } from "@/lib/api/types";

export class ApiError extends Error {
  status: number;
  code: string;
  path: string;

  constructor(body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = body.status;
    this.code = body.error;
    this.path = body.path;
  }
}

// status 0 = fetch() itself threw (offline, DNS failure, connection refused). 5xx = the request
// reached a server but got a genuine infrastructure failure back — most notably Render's edge
// proxy answering with 502/503/504 while a sleeping instance is still cold-starting, which is a
// normal HTTP response (not a thrown network error) but must be treated the same way: it says
// nothing about whether the user's session is valid, so it must never be read as "logged out".
export function isTransientError(err: unknown): boolean {
  return err instanceof ApiError && (err.status === 0 || err.status >= 500);
}
