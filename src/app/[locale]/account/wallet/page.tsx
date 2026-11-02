import { setRequestLocale } from "next-intl/server";
import { WalletView } from "@/components/account/WalletView";
import { asLocale } from "@/i18n/routing";
import { privatePageMetadata } from "@/lib/server/metadata";

export const generateMetadata = privatePageMetadata("wallet");

export default async function WalletPage({ params }: { params: Promise<{ locale: string }> }) {
  setRequestLocale(asLocale((await params).locale));
  return <WalletView />;
}
