import { setRequestLocale } from "next-intl/server";
import { SettingsView } from "@/components/account/SettingsView";
import { asLocale } from "@/i18n/routing";
import { privatePageMetadata } from "@/lib/server/metadata";

export const generateMetadata = privatePageMetadata("settings");

export default async function SettingsPage({ params }: { params: Promise<{ locale: string }> }) {
  setRequestLocale(asLocale((await params).locale));
  return <SettingsView />;
}
