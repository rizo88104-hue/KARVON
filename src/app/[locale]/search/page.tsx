import type { Metadata } from "next";
import { asLocale } from "@/i18n/routing";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { Suspense } from "react";
import { SearchResults } from "@/components/search/SearchResults";

type Props = {
  params: Promise<{ locale: string }>;
  searchParams: Promise<{ q?: string | string[] }>;
};

export async function generateMetadata({ params, searchParams }: Props): Promise<Metadata> {
  const locale = asLocale((await params).locale);
  const { q } = await searchParams;
  const t = await getTranslations({ locale, namespace: "meta" });
  const query = (Array.isArray(q) ? q[0] : q)?.trim();
  return {
    title: query ? t("search", { q: query.slice(0, 60) }) : t("searchEmpty"),
    robots: { index: false, follow: true },
  };
}

export default async function SearchPage({ params }: Props) {
  const locale = asLocale((await params).locale);
  setRequestLocale(locale);
  return (
    <main className="container-site pt-6 pb-16">
      <Suspense>
        <SearchResults />
      </Suspense>
    </main>
  );
}
