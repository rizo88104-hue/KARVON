import type { Metadata } from "next";
import { setRequestLocale } from "next-intl/server";
import type { ReactNode } from "react";
import { AccountShell } from "@/components/account/AccountShell";
import { asLocale } from "@/i18n/routing";

export const metadata: Metadata = { robots: { index: false, follow: false } };

export default async function AccountLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ locale: string }>;
}) {
  setRequestLocale(asLocale((await params).locale));
  return (
    <main className="container-site pt-6 pb-16">
      <AccountShell>{children}</AccountShell>
    </main>
  );
}
