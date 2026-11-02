"use client";

import { useLocale, useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { Link, usePathname } from "@/i18n/navigation";
import { routing } from "@/i18n/routing";

export function LanguageSwitch() {
  const t = useTranslations("topbar");
  const locale = useLocale();
  const pathname = usePathname();
  const search = useSearchParams().toString();
  const href = search ? `${pathname}?${search}` : pathname;
  return (
    <span className="whitespace-nowrap" aria-label={t("language")}>
      {routing.locales.map((l, i) => (
        <span key={l}>
          {i > 0 && <span className="mx-1 opacity-60">·</span>}
          <Link
            href={href}
            locale={l}
            hrefLang={l}
            aria-current={l === locale ? "true" : undefined}
            className={l === locale ? "font-semibold" : "opacity-95 hover:underline"}
          >
            {t(l)}
          </Link>
        </span>
      ))}
    </span>
  );
}
