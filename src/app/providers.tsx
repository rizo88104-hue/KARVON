"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useLocale } from "next-intl";
import { useEffect, useState, type ReactNode } from "react";
import { setApiLocale } from "@/api/client";
import { isApiError } from "@/api/errors";
import { ToastProvider } from "@/components/Toast";
import { initSession } from "@/lib/session";

export function Providers({ children }: { children: ReactNode }) {
  const locale = useLocale();
  setApiLocale(locale);

  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            refetchOnWindowFocus: false,
            // 4xx answers will not change on retry.
            retry: (count, error) => count < 2 && !(isApiError(error) && error.status < 500),
          },
        },
      }),
  );

  useEffect(() => {
    initSession();
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>{children}</ToastProvider>
    </QueryClientProvider>
  );
}
