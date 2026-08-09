import { useCallback, useEffect, useState } from "react";

import { ApiError } from "@/lib/api/ApiError";

interface UseAsyncState<T> {
  data: T | null;
  error: ApiError | Error | null;
  loading: boolean;
}

export function useAsync<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<UseAsyncState<T>>({ data: null, error: null, loading: true });

  const refetch = useCallback(() => {
    let cancelled = false;
    setState((prev) => ({ ...prev, loading: true, error: null }));
    fn()
      .then((data) => {
        if (!cancelled) setState({ data, error: null, loading: false });
      })
      .catch((error: ApiError | Error) => {
        if (!cancelled) setState({ data: null, error, loading: false });
      });
    return () => {
      cancelled = true;
    };
    // deps is an intentionally caller-provided dependency list, not the closed-over fn
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    return refetch();
  }, [refetch]);

  return { ...state, refetch };
}
