import { useTranslations } from "next-intl";
import type { ProductCard as ProductCardData } from "@/api/types";
import { Link } from "@/i18n/navigation";
import { formatAmount } from "@/lib/format";
import { discountPercent } from "@/lib/site";
import { DeliveryChip } from "./DeliveryChip";
import { DiscountBadge } from "./DiscountBadge";
import { FavoriteButton } from "./FavoriteButton";
import { PriceTag } from "./PriceTag";
import { ProductImage } from "./ProductImage";

/**
 * Product card in the marketplace layout: a square photo with rounded corners (Taobao photos are square, so nothing is
 * cut off; heart top-right, discount bottom-left), then the price with room for the old one (so the rows line up), the delivery days chip, the name (two lines), the Taobao sales count and a
 * full-width "Savatga" button. The button opens the product page, because a variant (SKU) must be chosen before
 * adding to the cart. Photo zoom on hover and the rise-in on scroll: .product-card in globals.css.
 */
export function ProductCard({ product }: { product: ProductCardData }) {
  const t = useTranslations("productCard");
  const href = `/product/${encodeURIComponent(product.externalId ?? "")}`;
  const sold = product.salesCount ?? 0;
  return (
    <article className="product-card relative flex flex-col">
      <div className="relative">
        <Link href={href} tabIndex={-1} aria-hidden="true" className="block overflow-hidden rounded-lg">
          <ProductImage
            src={product.mainImageUrl}
            alt=""
            placeholder={t("noImage")}
            sizes="(max-width: 560px) 50vw, (max-width: 900px) 33vw, (max-width: 1100px) 25vw, 240px"
            className="aspect-square w-full"
          />
        </Link>
        <FavoriteButton card={product} className="absolute top-2 right-2 z-[2]" />
        <DiscountBadge
          percent={discountPercent(product.priceUzs, product.originalPriceUzs)}
          position="bottom-2 left-2"
        />
      </div>
      <div className="flex flex-1 flex-col px-0.5 pt-2.5">
        {/* The old price line is reserved on every card, so chips and names line up across a row. */}
        <div className="min-h-[42px] min-[640px]:min-h-[46px]">
          <PriceTag amount={product.priceUzs} old={product.originalPriceUzs} />
        </div>
        <DeliveryChip />
        <Link href={href} className="mt-1.5 line-clamp-2 text-[13px] leading-snug text-body hover:text-navy">
          {product.title}
        </Link>
        {sold > 0 && (
          <span className="mt-1 flex items-center gap-1 text-[12px] text-muted">
            <svg
              viewBox="0 0 24 24"
              width="13"
              height="13"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M5 8h14l-1.2 12H6.2L5 8zM9 8V6.5a3 3 0 0 1 6 0V8" />
            </svg>
            {t("sold", { count: formatAmount(sold) })}
          </span>
        )}
        <div className="mt-auto pt-3">
          <Link href={href} className="btn btn-navy h-10 w-full gap-2 rounded-[10px] text-[14px]">
            <svg
              viewBox="0 0 24 24"
              width="17"
              height="17"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M3 4h2l2.4 11h11l2-8H6.3" />
              <circle cx="9.5" cy="19.5" r="1.5" />
              <circle cx="17.5" cy="19.5" r="1.5" />
            </svg>
            {t("addToCart")}
          </Link>
        </div>
      </div>
    </article>
  );
}

export function ProductGrid({ children, label }: { children: React.ReactNode; label?: string }) {
  return (
    <section
      aria-label={label}
      className="grid grid-cols-2 gap-x-2.5 gap-y-6 min-[561px]:grid-cols-3 min-[561px]:gap-x-4 min-[901px]:grid-cols-4 min-[1101px]:grid-cols-5"
    >
      {children}
    </section>
  );
}
