import { setRequestLocale } from "next-intl/server";
import { Suspense } from "react";
import { OrdersList } from "@/components/account/OrdersList";
import { asLocale } from "@/i18n/routing";
import { privatePageMetadata } from "@/lib/server/metadata";

export const generateMetadata = privatePageMetadata("orders");

export default async function OrdersPage({ params }: { params: Promise<{ locale: string }> }) {
  setRequestLocale(asLocale((await params).locale));
  return (
    <Suspense>
      <OrdersList />
    </Suspense>
  );
}
