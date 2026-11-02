"use client";

import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { useRouter } from "@/i18n/navigation";

export const SEARCH_INPUT_ID = "q";

/** Header search (design: .search). Taobao links are resolved on the /search page. */
export function SearchForm() {
  const t = useTranslations("header");
  const router = useRouter();
  const params = useSearchParams();
  const urlQuery = params.get("q") ?? "";
  const [value, setValue] = useState(urlQuery);
  const [lastUrlQuery, setLastUrlQuery] = useState(urlQuery);

  // Follow the URL when the query changes elsewhere (category links, back button).
  if (urlQuery !== lastUrlQuery) {
    setLastUrlQuery(urlQuery);
    setValue(urlQuery);
  }

  return (
    <form
      role="search"
      className="order-3 flex h-12 min-w-0 flex-[1_1_100%] items-center gap-2.5 rounded-[10px] border-2 border-navy bg-white px-3.5 min-[901px]:order-none min-[901px]:flex-1"
      onSubmit={(e) => {
        e.preventDefault();
        const q = value.trim();
        if (q) router.push(`/search?q=${encodeURIComponent(q)}`);
        else document.getElementById(SEARCH_INPUT_ID)?.focus();
      }}
    >
      <svg
        viewBox="0 0 24 24"
        width="16"
        height="16"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        className="flex-none text-muted"
        aria-hidden="true"
      >
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.5-3.5" />
      </svg>
      <input
        id={SEARCH_INPUT_ID}
        name="q"
        type="search"
        enterKeyHint="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder={t("searchPlaceholder")}
        aria-label={t("searchLabel")}
        className="min-w-0 flex-1 border-0 bg-transparent text-[15px] outline-none placeholder:text-muted"
      />
      <button type="submit" className="sr-only">
        {t("searchSubmit")}
      </button>
    </form>
  );
}
