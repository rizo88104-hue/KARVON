import "server-only";

import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { asLocale } from "@/i18n/routing";
import type { AdminSectionKey } from "@/lib/admin/roles";

/** generateMetadata for a staff panel section: its menu name as the title. */
export function adminPageMetadata(key: AdminSectionKey) {
  return async ({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> => {
    const t = await getTranslations({ locale: asLocale((await params).locale), namespace: "admin" });
    return { title: t(`nav.${key}`) };
  };
}
