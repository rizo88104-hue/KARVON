"use client";

import { useTranslations } from "next-intl";
import type { CartItem, CartShop } from "@/api/types";
import { PriceTag } from "@/components/PriceTag";
import { ProductImage } from "@/components/ProductImage";
import { QuantityInput } from "@/components/QuantityInput";
import { Link } from "@/i18n/navigation";
import { formatDecimal } from "@/lib/format";

type Props = {
  shop: CartShop;
  busy?: boolean;
  onSelect: (item: CartItem, selected: boolean) => void;
  onQuantity: (item: CartItem, quantity: number) => void;
  onRemove: (item: CartItem) => void;
};

/** The cart lines of one Taobao shop. */
export function CartShopGroup({ shop, busy, onSelect, onQuantity, onRemove }: Props) {
  const t = useTranslations("cart");
  const tc = useTranslations("productCard");

  return (
    <section className="card overflow-hidden" aria-label={shop.shopName}>
      <div className="flex items-center gap-2 border-b border-line bg-grey-bg/50 px-4 py-3 font-bold">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true" className="text-navy">
          <path d="M4 9h16l-1-5H5zM5 9v11h14V9" />
        </svg>
        <span className="min-w-0 truncate">{shop.shopName}</span>
        <span className="rounded-[4px] border border-orange px-1 text-[11px] font-semibold text-ink">{t("shopTag")}</span>
      </div>
      <ul className="m-0 list-none p-0">
        {(shop.items ?? []).map((item) => {
          const unavailable = !!item.outOfStock || !!item.blocked;
          const href = `/product/${encodeURIComponent(item.externalProductId ?? "")}`;
          const sku = (item.skuProperties ?? []).map((p) => p.value ?? p.valueOriginal).filter(Boolean).join(" · ");
          return (
            <li key={item.id} className="grid grid-cols-[24px_72px_minmax(0,1fr)] gap-3 border-b border-line px-4 py-4 last:border-b-0 min-[561px]:grid-cols-[24px_88px_minmax(0,1fr)]">
              <input
                type="checkbox"
                className="mt-1 h-[18px] w-[18px] accent-navy"
                checked={!!item.selected && !unavailable}
                disabled={busy || unavailable}
                onChange={(e) => onSelect(item, e.target.checked)}
                aria-label={t("selectItem", { title: item.title ?? "" })}
              />
              <Link href={href} tabIndex={-1} aria-hidden="true">
                <ProductImage src={item.imageUrl} alt="" placeholder={tc("noImage")} sizes="88px" className="aspect-square w-full rounded-sm" />
              </Link>
              <div className="flex min-w-0 flex-col gap-1.5">
                <Link href={href} className="line-clamp-2 text-[14px] leading-snug hover:text-navy">
                  {item.title}
                </Link>
                {sku && <span className="self-start rounded-[4px] bg-grey-bg px-1.5 py-0.5 text-[12px] text-subtle">{sku}</span>}
                {item.comment && <span className="text-[12px] text-muted">{t("commentLabel")}: {item.comment}</span>}
                {item.priceChanged && (
                  <span className="text-[13px] font-semibold text-danger">
                    {t("priceChanged", { old: formatDecimal(item.previousPriceCny), new: formatDecimal(item.priceCny) })}
                  </span>
                )}
                {item.outOfStock && <span className="text-[13px] font-semibold text-danger">{t("outOfStock")}</span>}
                {item.blocked && <span className="text-[13px] font-semibold text-danger">{t("blocked")}</span>}
                <div className="mt-1 flex flex-wrap items-center justify-between gap-3">
                  <QuantityInput
                    value={item.quantity ?? 1}
                    onChange={(q) => onQuantity(item, q)}
                    disabled={busy || unavailable}
                  />
                  <PriceTag amount={item.price?.totalUzs} size="md" />
                </div>
                <button type="button" className="link-btn self-start text-[13px]" onClick={() => onRemove(item)} disabled={busy}>
                  {t("remove")}
                </button>
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
