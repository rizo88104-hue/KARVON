"use client";

import { useTranslations } from "next-intl";
import { useEffect } from "react";
import { EmptyState } from "@/components/EmptyState";

export default function AdminError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const t = useTranslations("errors");
  const tc = useTranslations("common");
  useEffect(() => {
    console.error(error);
  }, [error]);
  return (
    <EmptyState
      title={t("errorTitle")}
      text={t("errorText")}
      action={
        <button type="button" className="btn btn-orange" onClick={reset}>
          {tc("retry")}
        </button>
      }
    />
  );
}
