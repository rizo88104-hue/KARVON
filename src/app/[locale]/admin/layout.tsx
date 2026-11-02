import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import type { ReactNode } from "react";
import { AdminShell } from "@/components/admin/AdminShell";
import { asLocale } from "@/i18n/routing";

type Props = { children: ReactNode; params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const t = await getTranslations({ locale: asLocale((await params).locale), namespace: "admin" });
  return {
    title: { default: t("title"), template: `%s — ${t("title")}` },
    robots: { index: false, follow: false },
  };
}

/** Staff panel: its own frame instead of the customer header and footer. */
export default async function AdminLayout({ children, params }: Props) {
  setRequestLocale(asLocale((await params).locale));
  return <AdminShell>{children}</AdminShell>;
}
