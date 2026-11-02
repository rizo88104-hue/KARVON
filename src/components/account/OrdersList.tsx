"use client";

import { useInfiniteQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import { EmptyState } from "@/components/EmptyState";
import { PriceTag } from "@/components/PriceTag";
import { ProductImage } from "@/components/ProductImage";
import { Link } from "@/i18n/navigation";
import { formatAmount, formatDate } from "@/lib/format";
import { keys } from "@/lib/queries";
import { useErrorMessage } from "@/lib/use-error-message";
import { StatusBadge } from "./StatusBadge";

const TABS = ["ALL", "AWAITING_PAYMENT", "IN_PROGRESS", "IN_TRANSIT", "DELIVERED"] as const;
type Tab = (typeof TABS)[number];

export function OrdersList() {
  const t = useTranslations("orders");
  const tc = useTranslations("common");
  const m = useTranslations("money");
  const tp = useTranslations("productCard");
  const locale = useLocale();
  const errorMessage = useErrorMessage();
  const raw = (useSearchParams().get("tab") ?? "ALL").toUpperCase() as Tab;
  const tab: Tab = TABS.includes(raw) ? raw : "ALL";

  const orders = useInfiniteQuery({
    queryKey: keys.orders(tab),
    initialPageParam: 0,
    queryFn: async ({ pageParam }) =>
      unwrap(await api.GET("/api/v1/orders", { params: { query: { tab: tab.toLowerCase(), page: pageParam, size: 10 } } })),
    getNextPageParam: (last) => ((last.page ?? 0) + 1 < (last.totalPages ?? 0) ? (last.page ?? 0) + 1 : undefined),
  });
  const list = orders.data?.pages.flatMap((p) => p.content ?? []) ?? [];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h1 className="m-0 text-[24px] font-bold">{t("title")}</h1>
        <span className="text-[14px] text-muted">{t("note")}</span>
      </div>
      <nav className="card no-scrollbar mb-4 flex gap-1 overflow-x-auto p-1.5" aria-label={t("title")}>
        {TABS.map((key) => (
          <Link
            key={key}
            href={key === "ALL" ? "/account/orders" : `/account/orders?tab=${key.toLowerCase()}`}
            aria-current={tab === key ? "page" : undefined}
            className={`rounded-sm px-4 py-2 text-[14px] whitespace-nowrap ${
              tab === key ? "bg-grey-bg font-bold text-navy" : "hover:bg-grey-bg"
            }`}
          >
            {t(`tab${key}`)}
          </Link>
        ))}
      </nav>

      {orders.isPending ? (
        <p className="py-10 text-center text-muted" role="status">
          {tc("loading")}
        </p>
      ) : orders.isError ? (
        <EmptyState title={t("error")} text={errorMessage(orders.error)} />
      ) : list.length === 0 ? (
        <EmptyState title={t("emptyTitle")} text={t("emptyText")} />
      ) : (
        <ul className="m-0 flex list-none flex-col gap-3 p-0">
          {list.map((o) => {
            const more = (o.itemCount ?? 1) - 1;
            const href = `/account/orders/${encodeURIComponent(o.number ?? "")}`;
            return (
              <li key={o.number} className="card overflow-hidden">
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-line bg-grey-bg/50 px-4 py-2.5 text-[13px] text-subtle">
                  <b className="num text-ink">{t("number", { number: o.number ?? "" })}</b>
                  <span className="num">{formatDate(o.createdAt, locale)}</span>
                  <span className="ml-auto">
                    <StatusBadge status={o.status} />
                  </span>
                </div>
                <div className="grid grid-cols-[64px_minmax(0,1fr)] gap-3 p-4 min-[561px]:grid-cols-[72px_minmax(0,1fr)_auto] min-[561px]:items-center">
                  <ProductImage src={o.firstItemImageUrl} alt="" placeholder={tp("noImage")} sizes="72px" className="aspect-square w-full rounded-sm" />
                  <div className="min-w-0">
                    <Link href={href} className="line-clamp-2 text-[14px] hover:text-navy">
                      {more > 0 ? t("andMore", { title: o.firstItemTitle ?? "", count: more }) : o.firstItemTitle}
                    </Link>
                    <span className="text-[13px] text-muted">{t("items", { count: o.itemCount ?? 1 })}</span>
                  </div>
                  <div className="col-span-2 flex flex-wrap items-center justify-between gap-3 min-[561px]:col-span-1 min-[561px]:flex-col min-[561px]:items-end">
                    <PriceTag amount={o.totalUzs} />
                    {(o.amountDueUzs ?? 0) > 0 && (
                      <span className="num text-[13px] font-semibold text-danger">
                        {t("amountDue", { amount: m("som", { amount: formatAmount(o.amountDueUzs) }) })}
                      </span>
                    )}
                    <Link href={href} className="btn btn-outline h-9 text-[13px]">
                      {t("open")}
                    </Link>
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {orders.hasNextPage && (
        <div className="mt-5 flex justify-center">
          <button type="button" className="btn btn-outline" disabled={orders.isFetchingNextPage} onClick={() => orders.fetchNextPage()}>
            {orders.isFetchingNextPage ? tc("loading") : tc("loadMore")}
          </button>
        </div>
      )}
    </>
  );
}
