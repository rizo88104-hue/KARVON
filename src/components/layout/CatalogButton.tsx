"use client";

import { useLocale, useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";
import type { Category } from "@/api/types";
import { CategoryIcon } from "@/components/CategoryIcon";
import { Link, usePathname } from "@/i18n/navigation";
import { categoryHref, categoryName } from "./categories";

/** Navy "Katalog" button (design: .btn-katalog) that opens the category tree. */
export function CatalogButton({ categories }: { categories: Category[] }) {
  const t = useTranslations("header");
  const locale = useLocale();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const pathname = usePathname();
  const [lastPath, setLastPath] = useState(pathname);

  // Close after navigation.
  if (pathname !== lastPath) {
    setLastPath(pathname);
    setOpen(false);
  }

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    const onClick = (e: MouseEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("click", onClick);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("click", onClick);
    };
  }, [open]);

  return (
    <div className="relative" ref={root}>
      <button
        type="button"
        className="btn btn-navy gap-2.5 px-3 min-[561px]:px-[18px]"
        aria-expanded={open}
        aria-controls="catalog-panel"
        aria-label={t("catalog")}
        onClick={() => setOpen((v) => !v)}
      >
        <svg
          viewBox="0 0 24 24"
          width="18"
          height="18"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          aria-hidden="true"
        >
          {open ? <path d="M6 6l12 12M18 6 6 18" /> : <path d="M4 6h16M4 12h16M4 18h16" />}
        </svg>
        <span className="hidden min-[561px]:inline">{t("catalog")}</span>
      </button>
      <div
        id="catalog-panel"
        hidden={!open}
        className="card absolute top-[calc(100%+8px)] left-0 z-40 max-h-[70vh] w-[min(720px,calc(100vw-32px))] overflow-auto p-4 shadow-[0_12px_32px_rgba(17,17,17,0.12)]"
      >
        <nav
          aria-label={t("categories")}
          className="grid grid-cols-1 gap-x-6 gap-y-4 min-[561px]:grid-cols-2 min-[901px]:grid-cols-3"
        >
          {categories.map((c) => (
            <div key={c.slug} className="flex flex-col gap-1">
              <Link href={categoryHref(c, locale)} className="flex items-center gap-2 font-bold hover:text-navy">
                <CategoryIcon category={c} size={18} className="flex-none text-navy" />
                {categoryName(c, locale)}
              </Link>
              {(c.children ?? []).map((child) => (
                <Link key={child.slug} href={categoryHref(child, locale)} className="text-[14px] text-body hover:text-navy">
                  {categoryName(child, locale)}
                </Link>
              ))}
            </div>
          ))}
        </nav>
      </div>
    </div>
  );
}
