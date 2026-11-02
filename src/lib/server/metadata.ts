import "server-only";

import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { asLocale } from "@/i18n/routing";
import type messages from "@/messages/uz.json";

type MetaKey = keyof (typeof messages)["meta"];

/** generateMetadata for private pages: a translated title, never indexed. */
export function privatePageMetadata(key: MetaKey) {
  return async ({ params }: { params: Promise<{ locale: string }> }): Promise<Metadata> => {
    const t = await getTranslations({ locale: asLocale((await params).locale), namespace: "meta" });
    return { title: t(key), robots: { index: false, follow: false } };
  };
}
