import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { FavoritesView } from "@/components/favorites/FavoritesView";
import { asLocale } from "@/i18n/routing";

type Props = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const locale = asLocale((await params).locale);
  const t = await getTranslations({ locale, namespace: "meta" });
  return { title: t("favorites"), robots: { index: false } };
}

export default async function FavoritesPage({ params }: Props) {
  setRequestLocale(asLocale((await params).locale));
  return (
    <main className="container-site pt-6 pb-16">
      <FavoritesView />
    </main>
  );
}
