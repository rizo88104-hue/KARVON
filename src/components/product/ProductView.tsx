"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { Cart, PricingSettings, ProductDetail } from "@/api/types";
import { PriceBreakdown } from "@/components/PriceBreakdown";
import { PriceTag } from "@/components/PriceTag";
import { QuantityInput } from "@/components/QuantityInput";
import { findSku, initialSelection, SkuSelector, type SkuSelection } from "@/components/SkuSelector";
import { useToast } from "@/components/Toast";
import { usePathname, useRouter } from "@/i18n/navigation";
import { formatAmount, formatDecimal } from "@/lib/format";
import { discountPercent } from "@/lib/site";
import { useCartMutation } from "@/lib/queries";
import { useErrorMessage } from "@/lib/use-error-message";
import { useSession } from "@/lib/use-session";
import { ProductGallery } from "./ProductGallery";

type Props = { product: ProductDetail; settings: PricingSettings | null; deliveryDays: string | null };

export function ProductView({ product, settings, deliveryDays }: Props) {
  const t = useTranslations("product");
  const m = useTranslations("money");
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const pathname = usePathname();
  const session = useSession();

  const skus = product.skus ?? [];
  const [selection, setSelection] = useState<SkuSelection>(() => initialSelection(skus));
  const [quantity, setQuantity] = useState(1);
  const [comment, setComment] = useState("");
  const [tab, setTab] = useState<"details" | "price">("details");

  const sku = findSku(skus, selection);
  const stock = sku?.stock ?? 0;
  const qty = Math.max(1, Math.min(quantity, stock || 1));
  const priceCny = sku?.priceCny ?? product.priceCny;

  // One unit's breakdown comes with the SKU; for more units ask the backend (min fee makes it non-linear).
  const estimate = useQuery({
    queryKey: ["estimate", priceCny, product.chinaShippingCny, product.weightKg, qty],
    queryFn: async () =>
      unwrap(
        await api.POST("/api/v1/pricing/estimate", {
          body: {
            priceCny: priceCny ?? 0,
            chinaShippingCny: product.chinaShippingCny,
            weightKg: product.weightKg,
            quantity: qty,
          },
        }),
      ),
    enabled: qty > 1 && priceCny != null,
    placeholderData: keepPreviousData,
  });
  const price = qty > 1 ? (estimate.data ?? null) : (sku?.price ?? product.price ?? null);
  // The old price from the backend is for one item at the base (cheapest SKU) price; compare only like with like.
  const oldPrice = qty === 1 && priceCny === product.priceCny ? (product.originalPriceUzs ?? null) : null;
  const discount = discountPercent(price?.totalUzs, oldPrice);

  const addItem = useCartMutation(
    async () =>
      unwrap(
        await api.POST("/api/v1/cart/items", {
          body: {
            externalProductId: product.externalId ?? "",
            skuId: sku?.skuId ?? "",
            quantity: qty,
            comment: comment.trim() || undefined,
          },
        }),
      ),
  );

  const canBuy = !!sku && stock > 0;

  async function add(): Promise<Cart | null> {
    if (session.status !== "authenticated") {
      toast(t("loginToBuy"));
      router.push(`/login?next=${encodeURIComponent(pathname)}`);
      return null;
    }
    if (!canBuy) {
      toast(sku ? t("outOfStock") : t("chooseVariant"));
      return null;
    }
    try {
      return await addItem.mutateAsync(undefined);
    } catch (e) {
      toast(errorMessage(e));
      return null;
    }
  }

  async function buyNow() {
    const cart = await add();
    if (!cart) return;
    // Select only this line, then open the checkout on the cart page.
    const line = cart.shops
      ?.flatMap((s) => s.items ?? [])
      .find((i) => i.externalProductId === product.externalId && i.skuId === sku?.skuId);
    try {
      await api.PATCH("/api/v1/cart/select-all", { body: { selected: false } });
      if (line?.id) await api.PATCH("/api/v1/cart/items/{id}", { params: { path: { id: line.id } }, body: { selected: true } });
    } finally {
      router.push("/cart?checkout=1");
    }
  }

  const images = product.images?.length ? product.images : product.mainImageUrl ? [product.mainImageUrl] : [];
  const busy = addItem.isPending;

  return (
    <>
      <div className="card grid grid-cols-1 gap-6 p-4 min-[901px]:grid-cols-[minmax(0,480px)_minmax(0,1fr)] min-[901px]:p-6">
        <ProductGallery images={images} title={product.title ?? ""} highlight={sku?.imageUrl} discount={discount} />

        <div className="flex min-w-0 flex-col gap-4">
          <div>
            <h1 className="m-0 text-[20px] leading-snug font-bold min-[561px]:text-[24px]">{product.title}</h1>
            {product.titleOriginal && (
              <p lang="zh" className="m-0 mt-1 text-[13px] text-muted">
                {product.titleOriginal}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1.5 rounded-md bg-grey-bg px-4 py-3.5">
            <span className="text-[13px] text-subtle">{t("finalPrice")}</span>
            <div className="flex flex-wrap items-baseline gap-3">
              <PriceTag amount={price?.totalUzs} old={discount ? oldPrice : null} size="xl" />
              {priceCny != null && (
                <span className="num text-[13px] text-subtle">{t("taobaoPrice", { amount: formatDecimal(priceCny) })}</span>
              )}
            </div>
          </div>

          <Row label={t("delivery")}>
            <span className="pt-1">{deliveryDays ? t("deliveryDays", { days: deliveryDays }) : t("deliveryNoDays")}</span>
          </Row>

          <SkuSelector skus={skus} value={selection} onChange={setSelection} />

          <Row label={t("quantity")}>
            <div className="flex flex-wrap items-center gap-3">
              <QuantityInput value={qty} onChange={setQuantity} max={Math.max(1, stock)} disabled={!canBuy} />
              <span className="text-[13px] text-muted">
                {!sku ? t("chooseVariant") : stock > 0 ? t("inStock", { count: stock }) : t("outOfStock")}
              </span>
            </div>
          </Row>

          <Row label={t("comment")}>
            <textarea
              className="input min-h-[64px] resize-y"
              maxLength={500}
              rows={2}
              placeholder={t("commentPlaceholder")}
              aria-label={t("comment")}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
            />
          </Row>

          <div className="grid grid-cols-1 gap-3 min-[561px]:grid-cols-2">
            <button type="button" className="btn btn-orange w-full" onClick={buyNow} disabled={busy || !canBuy}>
              {t("buyNow")}
            </button>
            <button
              type="button"
              className="btn btn-navy w-full"
              disabled={busy || !canBuy}
              onClick={async () => {
                if (await add()) toast(t("added"));
              }}
            >
              {t("addToCart")}
            </button>
          </div>

          <div className="rounded-md border border-line p-4">
            <PriceBreakdown price={price} />
          </div>
        </div>
      </div>

      <section className="card mt-4">
        <div role="tablist" className="no-scrollbar flex overflow-x-auto border-b border-line px-2">
          {(["details", "price"] as const).map((key) => (
            <button
              key={key}
              type="button"
              role="tab"
              id={`tab-${key}`}
              aria-selected={tab === key}
              aria-controls={`panel-${key}`}
              onClick={() => setTab(key)}
              className={`-mb-px border-0 border-b-[3px] bg-transparent px-4 py-3.5 text-[15px] font-bold whitespace-nowrap ${
                tab === key ? "border-orange text-ink" : "border-transparent text-muted"
              }`}
            >
              {key === "details" ? t("tabDetails") : t("tabPrice")}
            </button>
          ))}
        </div>
        <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`} className="p-4 min-[561px]:p-5">
          {tab === "details" ? (
            <dl className="m-0 grid grid-cols-1 gap-x-6 gap-y-2.5 text-[14px] min-[561px]:grid-cols-2 min-[901px]:grid-cols-3">
              {product.titleOriginal && <Spec label={t("originalTitle")} value={product.titleOriginal} lang="zh" />}
              {product.shop?.name && <Spec label={t("shop")} value={product.shop.name} />}
              {product.weightKg != null && (
                <Spec
                  label={t("weight")}
                  value={
                    product.weightEstimated
                      ? t("weightEstimated", { kg: formatDecimal(product.weightKg) })
                      : m("kg", { amount: formatDecimal(product.weightKg) })
                  }
                />
              )}
              {product.salesCount != null && <Spec label={t("sold")} value={formatAmount(product.salesCount)} />}
              {(product.attributes ?? []).map((a, i) => (
                <Spec key={`${a.nameOriginal}-${i}`} label={a.name ?? a.nameOriginal ?? ""} value={a.value ?? a.valueOriginal ?? ""} />
              ))}
            </dl>
          ) : (
            <PriceTable priceCny={priceCny} chinaCny={product.chinaShippingCny} qty={qty} price={price} settings={settings} />
          )}
        </div>
      </section>
    </>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-1 gap-2 text-[14px] sm:grid-cols-[110px_1fr] sm:gap-3">
      <span className="pt-1 text-muted">{label}</span>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

function Spec({ label, value, lang }: { label: string; value: string; lang?: string }) {
  return (
    <div className="flex min-w-0 gap-2">
      <dt className="flex-none text-muted">{label}:</dt>
      <dd className="m-0 min-w-0 break-words" lang={lang}>
        {value}
      </dd>
    </div>
  );
}

function PriceTable({
  priceCny,
  chinaCny,
  qty,
  price,
  settings,
}: {
  priceCny?: number;
  chinaCny?: number;
  qty: number;
  price: ProductDetail["price"] | null;
  settings: PricingSettings | null;
}) {
  const t = useTranslations("product");
  const b = useTranslations("breakdown");
  const m = useTranslations("money");
  const som = (v?: number) => m("som", { amount: formatAmount(v) });
  const rows: [string, string][] = [
    [t("priceRowTaobao"), m("cny", { amount: formatDecimal(priceCny) })],
    [t("priceRowChina"), m("cny", { amount: formatDecimal(chinaCny) })],
  ];
  if (settings) rows.push([t("priceRowRate"), t("priceRowRateValue", { rate: formatDecimal(settings.customerRate) })]);
  rows.push([t("priceRowGoods"), som(price?.goodsUzs)]);
  rows.push([
    settings
      ? t("priceRowFee", { percent: formatDecimal(settings.feePercent), min: som(settings.minServiceFee) })
      : b("serviceFee"),
    som(price?.serviceFeeUzs),
  ]);
  rows.push([
    t("priceRowShipping", {
      kg: formatDecimal(price?.chargeableWeightKg),
      perKg: settings ? som(settings.shippingPricePerKg) : "—",
    }),
    som(price?.shippingUzs),
  ]);

  return (
    <div className="max-w-[560px]">
      <table className="w-full border-collapse text-[14px]">
        <tbody>
          {rows.map(([label, value]) => (
            <tr key={label} className="border-b border-dashed border-line">
              <td className="py-2 pr-3">{label}</td>
              <td className="num py-2 text-right">{value}</td>
            </tr>
          ))}
          <tr>
            <td className="pt-3 pr-3 text-[16px] font-bold">{t("priceRowTotal", { count: qty })}</td>
            <td className="num pt-3 text-right text-[16px] font-bold">{som(price?.totalUzs)}</td>
          </tr>
        </tbody>
      </table>
      <p className="mt-3 mb-0 text-[13px] text-muted">{t("priceNote")}</p>
    </div>
  );
}
