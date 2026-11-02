import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { notFound } from "next/navigation";
import Markdown from "react-markdown";
import { asLocale } from "@/i18n/routing";
import { formatDate } from "@/lib/format";
import { getContentPage } from "@/lib/server/data";

type Props = { params: Promise<{ locale: string; slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const locale = asLocale((await params).locale);
  const page = await getContentPage(slug, locale);
  if (!page) {
    const t = await getTranslations({ locale, namespace: "meta" });
    return { title: t("notFound"), robots: { index: false } };
  }
  const prefix = locale === "uz" ? "" : `/${locale}`;
  return {
    title: page.title,
    description: page.body?.replace(/[#*_>`[\]()-]/g, "").slice(0, 160),
    alternates: { canonical: `${prefix}/pages/${slug}`, languages: { uz: `/pages/${slug}`, ru: `/ru/pages/${slug}` } },
  };
}

export default async function ContentPage({ params }: Props) {
  const { slug } = await params;
  const locale = asLocale((await params).locale);
  setRequestLocale(locale);
  const page = await getContentPage(slug, locale);
  if (!page) notFound();
  const t = await getTranslations("page");

  return (
    <main className="container-site pt-6 pb-16">
      <article className="mx-auto max-w-[760px]">
        <h1 className="mt-0 mb-1 text-[28px] font-extrabold tracking-[-0.02em]">{page.title}</h1>
        {page.updatedAt && (
          <p className="num mt-0 mb-6 text-[13px] text-muted">{t("updatedAt", { date: formatDate(page.updatedAt, locale) })}</p>
        )}
        <div className="prose-page text-[15px] leading-relaxed">
          {/* react-markdown does not render raw HTML, so admin-written Markdown cannot inject scripts. */}
          <Markdown>{page.body ?? ""}</Markdown>
        </div>
      </article>
    </main>
  );
}
