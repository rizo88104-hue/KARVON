import { getTranslations, setRequestLocale } from "next-intl/server";
import { asLocale } from "@/i18n/routing";
import type { Banner } from "@/api/types";
import { CategoryCards } from "@/components/home/CategoryCards";
import { CategoryShelf } from "@/components/home/CategoryShelf";
import { HeroBanners } from "@/components/home/HeroBanners";
import { LinkOrderCard } from "@/components/home/LinkOrderCard";
import { categoryHref, categoryName } from "@/components/layout/categories";
import {
  getBanners,
  getCategories,
  getCategoryProducts,
  getPricingSettings,
} from "@/lib/server/data";
import { getDeliveryDays } from "@/lib/site";

type Props = { params: Promise<{ locale: string }> };

export default async function HomePage({ params }: Props) {
  const locale = asLocale((await params).locale);
  setRequestLocale(locale);
  const t = await getTranslations("home");

  const [banners, categories, settings] = await Promise.all([
    getBanners(locale),
    getCategories(locale),
    getPricingSettings(),
  ]);
  // One block of best sellers per top category.
  const shelves = (
    await Promise.all(
      categories.map(async (category) => ({
        category,
        products: category.searchKeywordZh ? await getCategoryProducts(locale, category.searchKeywordZh) : [],
      })),
    )
  ).filter((s) => s.products.length > 0);
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
  const days = getDeliveryDays(settings);

  const steps = [
    { n: 1, title: t("step1Title"), text: t("step1Text") },
    { n: 2, title: t("step2Title"), text: t("step2Text") },
    { n: 3, title: t("step3Title"), text: days ? t("step3Text", { days }) : t("step3TextNoDays") },
  ];

  return (
    <main className="container-site pt-6 pb-16">
      <section className="grid grid-cols-1 gap-4 min-[901px]:grid-cols-[minmax(0,1.82fr)_minmax(0,1fr)]">
        <HeroBanners banners={heroBanners} popularHref="#popular" />
        <LinkOrderCard />
      </section>

      <section className="mt-4 grid grid-cols-1 gap-4 min-[901px]:grid-cols-3" aria-label={t("stepsLabel")}>
        {steps.map((s) => (
          <div key={s.n} className="flex items-center gap-3.5 rounded-md border border-line px-5 py-[18px]">
            <span className="grid h-[42px] w-[42px] flex-none place-items-center rounded-full bg-grey-bg text-[17px] font-bold">
              {s.n}
            </span>
            <div>
              <b className="block text-[16px] font-bold">{s.title}</b>
              <span className="block text-[14px] text-muted">{s.text}</span>
            </div>
          </div>
        ))}
      </section>

      {categories.length > 0 && (
        <section className="mt-9" aria-labelledby="home-categories">
          <h2 id="home-categories" className="m-0 mb-3.5 text-[24px] font-bold tracking-[-0.01em]">
            {t("categories")}
          </h2>
          <CategoryCards categories={categories} locale={locale} label={t("categories")} />
        </section>
      )}

      <div id="popular" className="scroll-mt-4">
        {shelves.length === 0 && <p className="mt-9 text-muted">{t("feedError")}</p>}
        {shelves.map((s) => (
          <CategoryShelf
            key={s.category.slug}
            category={s.category}
            products={s.products}
            locale={locale}
            seeAll={t("seeAll")}
          />
        ))}
      </div>
    </main>
  );
}
