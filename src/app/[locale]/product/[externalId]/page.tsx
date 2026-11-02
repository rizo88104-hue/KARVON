import type { Metadata } from "next";
import { getTranslations, setRequestLocale } from "next-intl/server";
import { notFound } from "next/navigation";
import { ProductView } from "@/components/product/ProductView";
import { Link } from "@/i18n/navigation";
import { asLocale, routing } from "@/i18n/routing";
import { formatAmount } from "@/lib/format";
import { getPricingSettings, getProduct } from "@/lib/server/data";
import { getDeliveryDays, SITE_URL } from "@/lib/site";

type Props = { params: Promise<{ locale: string; externalId: string }> };

function productPath(locale: string, externalId: string) {
  const prefix = locale === routing.defaultLocale ? "" : `/${locale}`;
  return `${prefix}/product/${encodeURIComponent(externalId)}`;
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { externalId } = await params;
  const locale = asLocale((await params).locale);
  const product = await getProduct(externalId, locale);
  const t = await getTranslations({ locale, namespace: "meta" });
  const m = await getTranslations({ locale, namespace: "money" });
  if (!product) return { title: t("notFound"), robots: { index: false } };

  const price = m("som", { amount: formatAmount(product.price?.totalUzs) });
  const description = `${product.title ?? ""} — ${price}. ${t("description")}`.slice(0, 300);
  return {
    title: product.title,
    description,
    alternates: {
      canonical: productPath(locale, externalId),
      languages: { uz: productPath("uz", externalId), ru: productPath("ru", externalId) },
    },
    openGraph: {
      type: "website",
      title: product.title,
      description,
      images: product.mainImageUrl ? [{ url: product.mainImageUrl, alt: product.title }] : undefined,
    },
  };
}

export default async function ProductPage({ params }: Props) {
  const { externalId } = await params;
  const locale = asLocale((await params).locale);
  setRequestLocale(locale);
  const [product, settings] = await Promise.all([getProduct(externalId, locale), getPricingSettings()]);
  if (!product) notFound();
  const t = await getTranslations("product");

  // Structured data for search engines.
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "Product",
    name: product.title,
    image: product.images?.length ? product.images : product.mainImageUrl ? [product.mainImageUrl] : undefined,
    sku: product.externalId,
    offers: {
      "@type": "Offer",
      priceCurrency: "UZS",
      price: product.price?.totalUzs,
      availability: (product.skus ?? []).some((s) => (s.stock ?? 0) > 0)
        ? "https://schema.org/InStock"
        : "https://schema.org/OutOfStock",
      url: `${SITE_URL}${productPath(locale, externalId)}`,
    },
  };

  return (
    <main className="container-site pt-5 pb-16">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, "\\u003c") }}
      />
      <nav aria-label={t("breadcrumbLabel")} className="mb-3 text-[13px] text-muted">
        <Link href="/" className="hover:text-navy hover:underline">
          {t("breadcrumbHome")}
        </Link>
        <span className="mx-1.5" aria-hidden="true">
          ›
        </span>
        <span aria-current="page" className="line-clamp-1 inline">
          {product.title}
        </span>
      </nav>
      <ProductView product={product} settings={settings} deliveryDays={getDeliveryDays()} />
    </main>
  );
}
