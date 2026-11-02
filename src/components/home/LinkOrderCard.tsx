"use client";

import { useTranslations } from "next-intl";
import { SEARCH_INPUT_ID } from "@/components/layout/SearchForm";

/** Grey side card (design: .side): "Havola joylash" focuses the header search and shows a link example. */
export function LinkOrderCard() {
  const t = useTranslations("home");
  return (
    <div className="flex flex-col rounded-lg bg-grey-bg px-7 py-[30px]">
      <h2 className="m-0 mb-2 text-[22px] font-bold tracking-[-0.01em]">{t("sideTitle")}</h2>
      <p className="m-0 mb-5 max-w-[40ch] text-[15px] text-subtle">{t("sideText")}</p>
      <button
        type="button"
        className="btn btn-outline mt-auto self-start"
        onClick={() => {
          const input = document.getElementById(SEARCH_INPUT_ID) as HTMLInputElement | null;
          if (!input) return;
          input.placeholder = t("linkPlaceholder");
          input.focus();
          input.scrollIntoView({ block: "center", behavior: "smooth" });
        }}
      >
        {t("sideCta")}
      </button>
    </div>
  );
}
