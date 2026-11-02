"use client";

import { useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { QueueOrder, StaffOrderSummary } from "@/api/types";
import { EmptyState } from "@/components/EmptyState";
import { ProductImage } from "@/components/ProductImage";
import { useAction } from "@/lib/admin/use-action";
import { formatDateTime, formatDecimal } from "@/lib/format";
import { useErrorMessage } from "@/lib/use-error-message";
import { CopyButton, orderKeys, PurchaseItemForm, taobaoUrl, WeightForm } from "./order-actions";
import { AdminPageHeader, Loading } from "./ui";

type Tab = "queue" | "purchasing" | "weighing";

/** Buyer work in China, phone first: take paid orders, buy each item on Taobao, then weigh the parcel. */
export function QueueView() {
  const t = useTranslations("admin.queue");
  const ta = useTranslations("admin");
  const [tab, setTab] = useState<Tab>("queue");
  return (
    <>
      <AdminPageHeader title={ta("nav.queue")} />
      <div role="tablist" className="card no-scrollbar mb-4 flex gap-1 overflow-x-auto p-1.5">
        {(["queue", "purchasing", "weighing"] as const).map((k) => (
          <button
            key={k}
            type="button"
            role="tab"
            aria-selected={tab === k}
            onClick={() => setTab(k)}
            className={`rounded-sm border-0 px-4 py-2 text-[14px] whitespace-nowrap ${
              tab === k ? "bg-orange font-bold text-ink" : "bg-transparent hover:bg-grey-bg"
            }`}
          >
            {t(`tab.${k}`)}
          </button>
        ))}
      </div>
      {tab === "queue" ? <PaidQueue /> : <WorkList status={tab === "purchasing" ? "PURCHASING" : "PURCHASED"} />}
    </>
  );
}

function PaidQueue() {
  const t = useTranslations("admin.queue");
  const ta = useTranslations("admin");
  const errorMessage = useErrorMessage();
  const queue = useQuery({
    queryKey: ["admin", "queue", "paid"],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/procurement/queue", { params: { query: { page: 0, size: 50 } } })),
    refetchInterval: 60_000,
  });
  if (queue.isPending) return <Loading />;
  if (queue.isError) return <EmptyState title={ta("loadError")} text={errorMessage(queue.error)} />;
  const orders = queue.data.content ?? [];
  if (!orders.length) return <EmptyState title={t("emptyQueue")} />;
  return (
    <div className="flex flex-col gap-3">
      {orders.map((o) => (
        <QueueCard key={o.orderId} order={o} />
      ))}
    </div>
  );
}

function QueueCard({ order }: { order: QueueOrder }) {
  const t = useTranslations("admin.queue");
  const tp = useTranslations("productCard");
  const id = order.orderId ?? "";
  const start = useAction(
    async () => unwrap(await api.POST("/api/v1/admin/procurement/orders/{id}/start", { params: { path: { id } } })),
    { success: t("started", { number: order.number ?? "" }), invalidate: orderKeys(id) },
  );
  const hours = Math.floor((order.paidMinutesAgo ?? 0) / 60);
  const minutes = (order.paidMinutesAgo ?? 0) % 60;

  return (
    <article className="card overflow-hidden">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-line bg-grey-bg/50 px-4 py-2.5 text-[13px]">
        <b className="num text-[15px]">{order.number}</b>
        <span className={`num ${hours >= 24 ? "font-semibold text-danger" : "text-muted"}`}>{t("paidAgo", { hours, minutes })}</span>
        <button type="button" className="btn btn-orange btn-sm ml-auto" disabled={start.isPending} onClick={() => start.mutate(undefined)}>
          {t("start")}
        </button>
      </div>
      {order.customerComment && (
        <p className="m-0 border-b border-line bg-[#fff6e5] px-4 py-2 text-[13px]">{t("orderComment", { text: order.customerComment })}</p>
      )}
      <ul className="m-0 list-none p-0">
        {(order.items ?? []).map((item) => {
          const sku = (item.skuProperties ?? []).map((p) => `${p.name ?? p.nameOriginal}: ${p.value ?? p.valueOriginal}`).join(" · ");
          const skuOriginal = (item.skuProperties ?? []).map((p) => `${p.nameOriginal}: ${p.valueOriginal}`).join("; ");
          return (
            <li key={item.itemId} className="flex flex-col gap-2 border-b border-line p-4 last:border-b-0">
              <div className="grid grid-cols-[64px_minmax(0,1fr)] gap-3">
                <ProductImage src={item.imageUrl} alt="" placeholder={tp("noImage")} sizes="64px" className="aspect-square w-full rounded-sm" />
                <div className="min-w-0 text-[13px]">
                  <b className="line-clamp-2 text-[14px] font-semibold">{item.title}</b>
                  {sku && <span className="block text-subtle">{sku}</span>}
                  {skuOriginal && (
                    <span lang="zh" className="block text-muted">
                      {skuOriginal}
                    </span>
                  )}
                  <span className="num block font-semibold">
                    {t("qtyPrice", { qty: item.quantity ?? 1, cny: formatDecimal(item.priceCny) })}
                  </span>
                  {item.shopName && <span className="block text-muted">{item.shopName}</span>}
                  {item.comment && <span className="mt-1 block rounded-[4px] bg-[#fff6e5] px-2 py-1">{t("itemComment", { text: item.comment })}</span>}
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <a
                  href={item.taobaoUrl || taobaoUrl(item)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn btn-navy btn-sm"
                >
                  {t("openTaobao")}
                </a>
                {item.skuId && <CopyButton text={`${item.skuId}${skuOriginal ? ` (${skuOriginal})` : ""}`} label={t("copySku")} />}
                {item.comment && <CopyButton text={item.comment} label={t("copyComment")} />}
              </div>
            </li>
          );
        })}
      </ul>
    </article>
  );
}

/** PURCHASING: mark items; PURCHASED: weigh and upload photos. Each card loads the full order when opened. */
function WorkList({ status }: { status: "PURCHASING" | "PURCHASED" }) {
  const t = useTranslations("admin.queue");
  const ta = useTranslations("admin");
  const errorMessage = useErrorMessage();
  const list = useQuery({
    queryKey: ["admin", "orders", "queue", status],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/orders", { params: { query: { status, page: 0, size: 50 } } })),
  });
  if (list.isPending) return <Loading />;
  if (list.isError) return <EmptyState title={ta("loadError")} text={errorMessage(list.error)} />;
  const orders = list.data.content ?? [];
  if (!orders.length) return <EmptyState title={status === "PURCHASING" ? t("emptyPurchasing") : t("emptyWeighing")} />;
  return (
    <div className="flex flex-col gap-3">
      {orders.map((o) => (
        <WorkCard key={o.id} summary={o} status={status} />
      ))}
    </div>
  );
}

function WorkCard({ summary, status }: { summary: StaffOrderSummary; status: "PURCHASING" | "PURCHASED" }) {
  const t = useTranslations("admin.queue");
  const to = useTranslations("admin.order");
  const tp = useTranslations("productCard");
  const ti = useTranslations("itemStatus");
  const locale = useLocale();
  const [open, setOpen] = useState(false);
  const id = summary.id ?? "";
  const detail = useQuery({
    queryKey: ["admin", "order", id],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/orders/{id}", { params: { path: { id } } })),
    enabled: open,
  });

  return (
    <article className="card overflow-hidden">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="flex w-full flex-wrap items-center gap-x-3 gap-y-1 border-0 bg-grey-bg/50 px-4 py-3 text-left text-[13px]"
      >
        <b className="num text-[15px]">{summary.number}</b>
        <span className="num text-muted">{formatDateTime(summary.createdAt, locale)}</span>
        <span className="text-muted">{t("itemsCount", { count: summary.itemCount ?? 0 })}</span>
        <span className="ml-auto font-semibold text-navy">{open ? t("collapse") : t("open")}</span>
      </button>
      {open &&
        (detail.isPending ? (
          <Loading />
        ) : detail.data ? (
          <div className="flex flex-col gap-3 p-4">
            {status === "PURCHASED" ? (
              <WeightForm order={detail.data} />
            ) : (
              (detail.data.order?.items ?? []).map((item) => (
                <div key={item.id} className="flex flex-col gap-2 border-b border-line pb-3 last:border-b-0">
                  <div className="grid grid-cols-[56px_minmax(0,1fr)] gap-3 text-[13px]">
                    <ProductImage src={item.imageUrl} alt="" placeholder={tp("noImage")} sizes="56px" className="aspect-square w-full rounded-sm" />
                    <div className="min-w-0">
                      <b className="line-clamp-2 font-semibold">{item.title}</b>
                      <span className="num block">{t("qtyPrice", { qty: item.quantity ?? 1, cny: formatDecimal(item.priceCny) })}</span>
                      {item.status && <span className="pill mt-1">{ti(item.status)}</span>}
                      {item.comment && <span className="mt-1 block rounded-[4px] bg-[#fff6e5] px-2 py-1">{t("itemComment", { text: item.comment })}</span>}
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <a href={taobaoUrl(item)} target="_blank" rel="noopener noreferrer" className="btn btn-navy btn-sm">
                      {to("openTaobao")}
                    </a>
                    {item.skuId && <CopyButton text={item.skuId} label={t("copySku")} />}
                  </div>
                  {item.status === "PENDING" && item.id && (
                    <PurchaseItemForm orderId={id} item={{ id: item.id, priceCny: item.priceCny }} />
                  )}
                </div>
              ))
            )}
          </div>
        ) : null)}
    </article>
  );
}
