import { useTranslations } from "next-intl";
import type { ProductCard as ProductCardData } from "@/api/types";
import { Link } from "@/i18n/navigation";
import { discountPercent } from "@/lib/site";
import { DiscountBadge } from "./DiscountBadge";
import { PriceTag } from "./PriceTag";
import { ProductImage } from "./ProductImage";

/**
 * Product card from e-market-design.html (.card): image, price, name, navy "Savatga" button.
 * The button opens the product page, because a variant (SKU) must be chosen before adding to the cart.
 */
export function ProductCard({ product }: { product: ProductCardData }) {
  const t = useTranslations("productCard");
  const href = `/product/${encodeURIComponent(product.externalId ?? "")}`;
  return (
    <article className="card flex flex-col overflow-hidden">
      <Link href={href} tabIndex={-1} aria-hidden="true" className="relative block">
        <DiscountBadge percent={discountPercent(product.priceUzs, product.originalPriceUzs)} />
        <ProductImage
          src={product.mainImageUrl}
          alt=""
          placeholder={t("noImage")}
          sizes="(max-width: 560px) 50vw, (max-width: 900px) 33vw, (max-width: 1100px) 25vw, 240px"
          className="aspect-[227/170] max-w-full"
        />
      </Link>
      <div className="flex flex-1 flex-col gap-0.5 px-3 pt-3.5 pb-3">
        <PriceTag amount={product.priceUzs} old={product.originalPriceUzs} />
        <Link href={href} className="mt-1 mb-2.5 line-clamp-2 text-[14px] text-body hover:text-navy">
          {product.title}
        </Link>
        <Link href={href} className="btn btn-navy mt-auto w-full">
          {t("addToCart")}
        </Link>
      </div>
    </article>
  );
}

export function ProductGrid({ children, label }: { children: React.ReactNode; label?: string }) {
  return (
    <section
      aria-label={label}
      className="grid grid-cols-2 gap-2.5 min-[561px]:grid-cols-3 min-[561px]:gap-4 min-[901px]:grid-cols-4 min-[1101px]:grid-cols-5"
    >
      {children}
    </section>
  );
}
