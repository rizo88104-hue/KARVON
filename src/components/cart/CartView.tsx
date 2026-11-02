"use client";

import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { CartItem } from "@/api/types";
import { EmptyState } from "@/components/EmptyState";
import { PriceTag } from "@/components/PriceTag";
import { useToast } from "@/components/Toast";
import { Link } from "@/i18n/navigation";
import { formatAmount } from "@/lib/format";
import { useCart, useCartMutation } from "@/lib/queries";
import { useErrorMessage } from "@/lib/use-error-message";
import { useSession } from "@/lib/use-session";
import { CartShopGroup } from "./CartShopGroup";
import { CheckoutModal } from "./CheckoutModal";

export function CartView() {
  const t = useTranslations("cart");
  const tc = useTranslations("common");
  const m = useTranslations("money");
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const session = useSession();
  const cart = useCart();
  const params = useSearchParams();
  const [checkoutOpen, setCheckoutOpen] = useState(params.get("checkout") === "1");

  const onError = (e: unknown) => toast(errorMessage(e));
  const update = useCartMutation(async ({ id, ...body }: { id: string; quantity?: number; selected?: boolean }) =>
    unwrap(await api.PATCH("/api/v1/cart/items/{id}", { params: { path: { id } }, body })),
  );
  const remove = useCartMutation(async (id: string) =>
    unwrap(await api.DELETE("/api/v1/cart/items/{id}", { params: { path: { id } } })),
  );
  const removeSelected = useCartMutation(async () =>
    unwrap(await api.DELETE("/api/v1/cart/items", { params: { query: { selected: true } } })),
  );
  const selectAll = useCartMutation(async (selected: boolean) =>
    unwrap(await api.PATCH("/api/v1/cart/select-all", { body: { selected } })),
  );
  const refresh = useCartMutation(async () => unwrap(await api.POST("/api/v1/cart/refresh-prices")));

  if (session.status === "unknown" || (session.status === "authenticated" && cart.isPending)) {
    return (
      <p className="py-14 text-center text-muted" role="status">
        {tc("loading")}
      </p>
    );
  }
  if (session.status === "guest") {
    return (
      <EmptyState
        title={t("loginTitle")}
        text={t("loginText")}
        action={
          <Link href="/login?next=/cart" className="btn btn-orange">
            {t("loginCta")}
          </Link>
        }
      />
    );
  }
  if (cart.isError) {
    return (
      <EmptyState
        title={t("error")}
        text={errorMessage(cart.error)}
        action={
          <button type="button" className="btn btn-navy" onClick={() => cart.refetch()}>
            {tc("retry")}
          </button>
        }
      />
    );
  }

  const data = cart.data;
  const shops = data?.shops ?? [];
  if (!data || shops.length === 0) {
    return (
      <>
        <h1 className="mt-0 mb-4 text-[24px] font-bold">{t("title")}</h1>
        <EmptyState
          title={t("emptyTitle")}
          text={t("emptyText")}
          action={
            <Link href="/" className="btn btn-orange">
              {t("emptyCta")}
            </Link>
          }
        />
      </>
    );
  }

  const items = shops.flatMap((s) => s.items ?? []);
  const selectable = items.filter((i) => !i.outOfStock && !i.blocked);
  const allSelected = selectable.length > 0 && selectable.every((i) => i.selected);
  const totals = data.totals;
  const selectedCount = data.selectedCount ?? 0;
  const busy = update.isPending || remove.isPending || removeSelected.isPending || selectAll.isPending || refresh.isPending;
  const som = (v?: number) => m("som", { amount: formatAmount(v) });

  const onQuantity = (item: CartItem, quantity: number) => item.id && update.mutate({ id: item.id, quantity }, { onError });
  const onSelect = (item: CartItem, selected: boolean) => item.id && update.mutate({ id: item.id, selected }, { onError });
  const onRemove = (item: CartItem) =>
    item.id && remove.mutate(item.id, { onError, onSuccess: () => toast(t("removed")) });

  return (
    <>
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="m-0 text-[24px] font-bold">
          {t("title")} <span className="text-muted">{t("count", { count: data.itemCount ?? 0 })}</span>
        </h1>
        <button
          type="button"
          className="btn btn-outline h-9 text-[13px]"
          disabled={busy}
          onClick={() => refresh.mutate(undefined, { onError, onSuccess: () => toast(t("refreshed")) })}
        >
          {t("refreshPrices")}
        </button>
      </div>
      <p className="mt-0 mb-3 text-[14px] text-muted">{t("note")}</p>

      <label className="card mb-3 flex cursor-pointer items-center gap-3 px-4 py-3 text-[14px] font-semibold">
        <input
          type="checkbox"
          className="h-[18px] w-[18px] accent-navy"
          checked={allSelected}
          disabled={busy || selectable.length === 0}
          onChange={(e) => selectAll.mutate(e.target.checked, { onError })}
        />
        {t("selectAll")}
      </label>

      <div className="flex flex-col gap-3">
        {shops.map((shop) => (
          <CartShopGroup
            key={shop.shopName}
            shop={shop}
            busy={busy}
            onSelect={onSelect}
            onQuantity={onQuantity}
            onRemove={onRemove}
          />
        ))}
      </div>

      <div
        className="sticky z-10 mt-4 flex flex-wrap items-center gap-x-4 gap-y-3 rounded-md border border-line bg-white p-3 pl-4 shadow-[0_-2px_16px_rgba(17,17,17,0.08)]"
        style={{ bottom: "calc(12px + env(safe-area-inset-bottom, 0px))" }}
      >
        <button
          type="button"
          className="link-btn text-[13px]"
          disabled={busy || selectedCount === 0}
          onClick={() => removeSelected.mutate(undefined, { onError, onSuccess: () => toast(t("removed")) })}
        >
          {t("removeSelected")}
        </button>
        <div className="flex min-w-0 flex-1 flex-col items-end gap-0.5 text-right">
          <div className="flex flex-wrap items-baseline justify-end gap-x-2">
            <span className="text-[14px]">
              {t("selected", { count: totals?.quantity ?? 0 })} · {t("total")}
            </span>
            <PriceTag amount={totals?.totalUzs} size="lg" />
          </div>
          <span className="num text-[12px] text-muted">
            {t("totalDetails", {
              goods: som(totals?.goodsUzs),
              fee: som(totals?.serviceFeeUzs),
              shipping: som(totals?.shippingUzs),
            })}
          </span>
        </div>
        <button
          type="button"
          className="btn btn-orange w-full min-[561px]:w-auto"
          disabled={busy || selectedCount === 0}
          onClick={() => setCheckoutOpen(true)}
        >
          {t("checkout")}
        </button>
      </div>

      <CheckoutModal open={checkoutOpen && selectedCount > 0} onClose={() => setCheckoutOpen(false)} totals={totals} />
    </>
  );
}
