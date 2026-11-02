import { redirect } from "@/i18n/navigation";
import { asLocale } from "@/i18n/routing";

export default async function AccountPage({ params }: { params: Promise<{ locale: string }> }) {
  redirect({ href: "/account/orders", locale: asLocale((await params).locale) });
}
