"use client";

import { useTranslations } from "next-intl";
import { useCallback } from "react";
import { ApiError } from "@/api/errors";

/** Text for an error: the backend's localized `detail` when there is one, otherwise a generic message. */
export function useErrorMessage() {
  const t = useTranslations("errors");
  return useCallback(
    (error: unknown) => {
      if (error instanceof ApiError) return error.detail ?? t("generic");
      if (error instanceof TypeError) return t("network");
      return t("generic");
    },
    [t],
  );
}
