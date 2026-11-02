"use client";

import * as Sentry from "@sentry/nextjs";
import { useTranslations } from "next-intl";
import { useEffect } from "react";
import { EmptyState } from "@/components/EmptyState";
import { Link } from "@/i18n/navigation";

export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const t = useTranslations("errors");
  const tc = useTranslations("common");
  useEffect(() => {
    Sentry.captureException(error);
    console.error(error);
  }, [error]);
  return (
    <main className="container-site pt-10 pb-16">
      <EmptyState
        title={t("errorTitle")}
        text={t("errorText")}
        action={
          <div className="flex flex-wrap justify-center gap-2">
            <button type="button" className="btn btn-orange" onClick={reset}>
              {tc("retry")}
            </button>
            <Link href="/" className="btn btn-outline">
              {t("goHome")}
            </Link>
          </div>
        }
      />
    </main>
  );
}
