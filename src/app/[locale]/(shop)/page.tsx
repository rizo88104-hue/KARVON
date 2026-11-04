import { getTranslations, setRequestLocale } from "next-intl/server";
import { asLocale } from "@/i18n/routing";
import type { Banner } from "@/api/types";
import { CategoryCards } from "@/components/home/CategoryCards";
import { HeroBanners } from "@/components/home/HeroBanners";
import { LinkOrderCard } from "@/components/home/LinkOrderCard";
import { categoryHref, categoryName } from "@/components/layout/categories";
import { MixedFeed, type MixSource } from "@/components/ProductFeed";
import { getBanners, getCategories, getCategoryProducts } from "@/lib/server/data";

type Props = { params: Promise<{ locale: string }> };

export default async function HomePage({ params }: Props) {
  const locale = asLocale((await params).locale);
  setRequestLocale(locale);
  const t = await getTranslations("home");

  const [banners, categories] = await Promise.all([getBanners(locale), getCategories(locale)]);
  // The feed mixes the best sellers of every top category.
  const feedSources: MixSource[] = (
    await Promise.all(
      categories.map(async (category) => ({
        keywordZh: category.searchKeywordZh ?? "",
        firstPage: category.searchKeywordZh ? await getCategoryProducts(locale, category.searchKeywordZh) : null,
      })),
    )
  ).filter((s): s is MixSource => (s.firstPage?.content?.length ?? 0) > 0);
  // Without banners from the admin panel the hero shows the categories that have a cover photo.
  const heroBanners: Banner[] = banners.length
    ? banners
    : categories
        .filter((c) => c.imageUrl)
        .slice(0, 5)
        .map((c) => {
          const name = categoryName(c, locale);
          return {
            id: c.slug,
            title: name,
            subtitle: t("categoryBannerText", { name }),
            imageUrl: c.imageUrl,
            linkUrl: categoryHref(c, locale),
          };
        });
  return (
    <main className="container-site pt-6 pb-16">
      <section className="grid grid-cols-1 gap-4 min-[901px]:grid-cols-[minmax(0,1.82fr)_minmax(0,1fr)]">
        <HeroBanners banners={heroBanners} popularHref="#popular" />
        <LinkOrderCard />
      </section>

      {categories.length > 0 && (
        <section className="mt-9" aria-labelledby="home-categories">
          <h2 id="home-categories" className="m-0 mb-3.5 text-[24px] font-bold tracking-[-0.01em]">
            {t("categories")}
          </h2>
          <CategoryCards categories={categories} locale={locale} label={t("categories")} />
        </section>
      )}

      <section id="popular" className="mt-9 scroll-mt-4" aria-labelledby="home-popular">
        <h2 id="home-popular" className="m-0 mb-3.5 text-[24px] font-bold tracking-[-0.01em]">
          {t("popular")}
        </h2>
        {feedSources.length === 0 ? (
          <p className="text-muted">{t("feedError")}</p>
        ) : (
          <MixedFeed sources={feedSources} label={t("popular")} />
        )}
      </section>
    </main>
  );
}
