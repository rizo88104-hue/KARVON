import { setRequestLocale } from "next-intl/server";
import { AddressesView } from "@/components/account/AddressesView";
import { asLocale } from "@/i18n/routing";
import { privatePageMetadata } from "@/lib/server/metadata";

export const generateMetadata = privatePageMetadata("addresses");

export default async function AddressesPage({ params }: { params: Promise<{ locale: string }> }) {
  setRequestLocale(asLocale((await params).locale));
  return <AddressesView />;
}
