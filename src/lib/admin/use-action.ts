"use client";

import { useMutation, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { useToast } from "@/components/Toast";
import { useErrorMessage } from "@/lib/use-error-message";

/**
 * A staff action: runs `fn`, shows a toast with the result (the backend's localized message on errors),
 * and refreshes the given queries on success.
 */
export function useAction<V, R>(
  fn: (vars: V) => Promise<R>,
  options: { success: string | ((result: R) => string); invalidate?: QueryKey[]; onSuccess?: (result: R) => void },
) {
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (result) => {
      toast(typeof options.success === "function" ? options.success(result) : options.success);
      for (const key of options.invalidate ?? []) void qc.invalidateQueries({ queryKey: key });
      options.onSuccess?.(result);
    },
    onError: (e) => toast(errorMessage(e)),
  });
}
