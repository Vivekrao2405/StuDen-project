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
