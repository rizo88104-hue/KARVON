import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { OrderDetailView } from "@/components/account/OrderDetailView";
import { asLocale } from "@/i18n/routing";

type Props = { params: Promise<{ locale: string; number: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, number } = await params;
  const t = await getTranslations({ locale: asLocale(locale), namespace: "meta" });
  return { title: t("order", { number: decodeURIComponent(number) }), robots: { index: false, follow: false } };
}

export default async function OrderPage({ params }: Props) {
  const { locale, number } = await params;
  setRequestLocale(asLocale(locale));
  return <OrderDetailView number={decodeURIComponent(number)} />;
}
