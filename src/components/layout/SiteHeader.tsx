import { getLocale, getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { Link } from "@/i18n/navigation";
import { getCategories } from "@/lib/server/data";
import { CatalogButton } from "./CatalogButton";
import { categoryHref, categoryName } from "./categories";
import { HeaderActions } from "./HeaderActions";
import { LanguageSwitch } from "./LanguageSwitch";
import { SearchForm } from "./SearchForm";

/** Top bar + header + category strip, as in e-market-design.html. */
export async function SiteHeader() {
  const locale = await getLocale();
  const t = await getTranslations("header");
  const tb = await getTranslations("topbar");
  const categories = await getCategories(locale);

  return (
    <>
      <div className="bg-ink text-[12px] font-medium text-white min-[561px]:text-[13px]">
        <div className="container-site flex min-h-[34px] flex-wrap items-center justify-between gap-3">
          <span>{tb("tagline")}</span>
          <Suspense>
            <LanguageSwitch />
          </Suspense>
        </div>
      </div>

      <header className="border-b border-line bg-white">
        <div className="container-site flex flex-wrap items-center gap-4 pt-4 pb-2.5 min-[901px]:flex-nowrap">
          <Link
            href="/"
            className="mr-0.5 text-[22px] font-bold tracking-[-0.02em] whitespace-nowrap text-navy min-[561px]:text-[28px]"
            aria-label={t("home")}
          >
            E-Market
          </Link>
          <CatalogButton categories={categories} />
          <Suspense>
            <SearchForm />
          </Suspense>
          <HeaderActions />
        </div>
        {categories.length > 0 && (
          <nav
            className="container-site no-scrollbar flex gap-5 overflow-x-auto pt-1 pb-3"
            aria-label={t("categories")}
          >
            {categories.map((c) => (
              <Link
                key={c.slug}
                href={categoryHref(c, locale)}
                className="text-[14px] font-medium whitespace-nowrap text-body hover:text-navy"
              >
                {categoryName(c, locale)}
              </Link>
            ))}
          </nav>
        )}
      </header>
    </>
  );
}
