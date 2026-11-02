import { getTranslations, setRequestLocale } from "next-intl/server";
import { asLocale } from "@/i18n/routing";
import { HeroBanners } from "@/components/home/HeroBanners";
import { LinkOrderCard } from "@/components/home/LinkOrderCard";
import { categoryName } from "@/components/layout/categories";
import { ProductCard, ProductGrid } from "@/components/ProductCard";
import { Link } from "@/i18n/navigation";
import { getBanners, getCategories, getPopularProducts, getPricingSettings } from "@/lib/server/data";
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
  const { query: feedQuery, products } = await getPopularProducts(
    locale,
    categories.map((c) => categoryName(c, locale)),
  );
  const days = getDeliveryDays(settings);
  const allHref = feedQuery ? `/search?q=${encodeURIComponent(feedQuery)}&sort=SALES` : "/search";

  const steps = [
    { n: 1, title: t("step1Title"), text: t("step1Text") },
    { n: 2, title: t("step2Title"), text: t("step2Text") },
    { n: 3, title: t("step3Title"), text: days ? t("step3Text", { days }) : t("step3TextNoDays") },
  ];

  return (
    <main className="container-site pt-6 pb-16">
      <section className="grid grid-cols-1 gap-4 min-[901px]:grid-cols-[minmax(0,1.82fr)_minmax(0,1fr)]">
        <HeroBanners banners={banners} popularHref="#popular" />
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

      <div id="popular" className="mt-9 mb-3.5 flex scroll-mt-4 items-baseline justify-between gap-3">
        <h2 className="m-0 text-[24px] font-bold tracking-[-0.01em]">{t("popular")}</h2>
        <Link href={allHref} className="text-[14px] font-medium hover:text-navy hover:underline">
          {t("seeAll")}
        </Link>
      </div>
      {products === null ? (
        <p className="text-muted">{t("feedError")}</p>
      ) : (
        <ProductGrid label={t("popular")}>
          {products.slice(0, 10).map((p) => (
            <ProductCard key={p.externalId} product={p} />
          ))}
        </ProductGrid>
      )}
    </main>
  );
}
