"use client";

import { useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { isApiError, unwrap } from "@/api/errors";
import type { StaffOrder } from "@/api/types";
import { StatusBadge } from "@/components/account/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { ProductImage } from "@/components/ProductImage";
import { Link } from "@/i18n/navigation";
import { hasRole } from "@/lib/admin/roles";
import { useAction } from "@/lib/admin/use-action";
import { formatDate, formatDateTime, formatDecimal } from "@/lib/format";
import { useErrorMessage } from "@/lib/use-error-message";
import { useSession } from "@/lib/use-session";
import { ConfirmDialog } from "./ConfirmDialog";
import { CopyButton, orderKeys, PhotoManager, PurchaseItemForm, taobaoUrl, WeightForm } from "./order-actions";
import { AdminPageHeader, Loading, Section, TableWrap, useMoney } from "./ui";

export function StaffOrderView({ id }: { id: string }) {
  const t = useTranslations("admin.order");
  const ta = useTranslations("admin");
  const errorMessage = useErrorMessage();
  const locale = useLocale();

  const query = useQuery({
    queryKey: ["admin", "order", id],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/orders/{id}", { params: { path: { id } } })),
  });

  if (query.isPending) return <Loading />;
  if (query.isError) {
    const missing = isApiError(query.error) && query.error.status === 404;
    return (
      <EmptyState
        title={missing ? t("notFound") : ta("loadError")}
        text={missing ? undefined : errorMessage(query.error)}
        action={
          <Link href="/admin/orders" className="btn btn-navy">
            {t("back")}
          </Link>
        }
      />
    );
  }

  const staff = query.data;
  const o = staff.order;
  return (
    <>
      <Link href="/admin/orders" className="mb-2 inline-block text-[13px] text-muted hover:text-navy hover:underline">
        ← {t("back")}
      </Link>
      <AdminPageHeader title={t("title", { number: o?.number ?? "" })}>
        <StatusBadge status={o?.status} />
      </AdminPageHeader>
      <p className="-mt-2 mb-4 text-[13px] text-muted">
        <span className="num">{formatDateTime(o?.createdAt, locale)}</span>
        {staff.customer && (
          <>
            {" · "}
            <Link href={`/admin/customers/${staff.customer.id}`} className="num text-navy hover:underline">
              {staff.customer.phone}
            </Link>
            {staff.customer.fullName && ` · ${staff.customer.fullName}`}
          </>
        )}
      </p>

      <div className="grid grid-cols-1 items-start gap-4 min-[1101px]:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-4">
          <Actions staff={staff} />
          <Items staff={staff} />
          <History staff={staff} />
        </div>
        <div className="flex min-w-0 flex-col gap-4">
          <Prices staff={staff} />
          <Delivery staff={staff} />
          <Section title={t("photos")}>
            <div className="p-4">
              <PhotoManager order={staff} />
            </div>
          </Section>
          <Comments staff={staff} />
        </div>
      </div>
    </>
  );
}

function Actions({ staff }: { staff: StaffOrder }) {
  const t = useTranslations("admin.order");
  const session = useSession();
  const o = staff.order;
  const id = o?.id ?? "";
  const next = staff.nextStatuses ?? [];
  const status = o?.status;
  const isAdmin = hasRole(session.user, "ADMIN");

  const [tracking, setTracking] = useState("");
  const [carrier, setCarrier] = useState("");
  const [arrival, setArrival] = useState("");
  const [pickup, setPickup] = useState("");
  const [note, setNote] = useState("");
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const [paidOpen, setPaidOpen] = useState(false);

  const keys = orderKeys(id);
  const start = useAction(
    async () => unwrap(await api.POST("/api/v1/admin/procurement/orders/{id}/start", { params: { path: { id } } })),
    { success: t("started"), invalidate: keys },
  );
  const ship = useAction(
    async () =>
      unwrap(
        await api.POST("/api/v1/admin/orders/{id}/ship", {
          params: { path: { id } },
          body: {
            internationalTrackingNumber: tracking.trim(),
            carrier: carrier.trim() || undefined,
            expectedArrivalDate: arrival || undefined,
            comment: note.trim() || undefined,
          },
        }),
      ),
    { success: t("shipped"), invalidate: keys },
  );
  const arrived = useAction(
    async () =>
      unwrap(
        await api.POST("/api/v1/admin/orders/{id}/arrived", {
          params: { path: { id } },
          body: { pickupInfo: pickup.trim() || undefined, comment: note.trim() || undefined },
        }),
      ),
    { success: t("arrivedDone"), invalidate: keys },
  );
  const delivered = useAction(
    async () =>
      unwrap(
        await api.POST("/api/v1/admin/orders/{id}/delivered", {
          params: { path: { id } },
          body: { comment: note.trim() || undefined },
        }),
      ),
    { success: t("deliveredDone"), invalidate: keys },
  );
  const cancel = useAction(
    async () =>
      unwrap(
        await api.POST("/api/v1/admin/orders/{id}/status", {
          params: { path: { id } },
          body: { toStatus: "CANCELLED", comment: cancelReason.trim() || undefined },
        }),
      ),
    { success: t("cancelled"), invalidate: keys, onSuccess: () => setCancelOpen(false) },
  );
  const shippingPaid = useAction(
    async () => unwrap(await api.POST("/api/v1/admin/orders/{id}/shipping-payment", { params: { path: { id } } })),
    { success: t("shippingPaidDone"), invalidate: keys, onSuccess: () => setPaidOpen(false) },
  );

  const blocks: React.ReactNode[] = [];
  if (next.includes("PURCHASING")) {
    blocks.push(
      <button key="start" type="button" className="btn btn-orange self-start" disabled={start.isPending} onClick={() => start.mutate(undefined)}>
        {t("startPurchase")}
      </button>,
    );
  }
  if (status === "PURCHASED" && next.includes("AT_WAREHOUSE")) {
    blocks.push(
      <div key="weight">
        <h3 className="mt-0 mb-2 text-[14px] font-bold">{t("weightTitle")}</h3>
        <WeightForm order={staff} />
      </div>,
    );
  }
  if (next.includes("IN_TRANSIT")) {
    blocks.push(
      <form
        key="ship"
        className="flex flex-col gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (tracking.trim()) ship.mutate(undefined);
        }}
      >
        <h3 className="m-0 text-[14px] font-bold">{t("shipTitle")}</h3>
        <div className="grid grid-cols-1 gap-3 min-[561px]:grid-cols-3">
          <label className="label">
            {t("trackingNumber")}
            <input className="input num h-9" value={tracking} onChange={(e) => setTracking(e.target.value)} required />
          </label>
          <label className="label">
            {t("carrier")}
            <input className="input h-9" value={carrier} onChange={(e) => setCarrier(e.target.value)} />
          </label>
          <label className="label">
            {t("expectedArrival")}
            <input type="date" className="input h-9" value={arrival} onChange={(e) => setArrival(e.target.value)} />
          </label>
        </div>
        <button type="submit" className="btn btn-orange self-start" disabled={!tracking.trim() || ship.isPending}>
          {t("ship")}
        </button>
      </form>,
    );
  }
  if (next.includes("ARRIVED")) {
    blocks.push(
      <form
        key="arrived"
        className="flex flex-col gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          arrived.mutate(undefined);
        }}
      >
        <h3 className="m-0 text-[14px] font-bold">{t("arrivedTitle")}</h3>
        <label className="label">
          {t("pickupInfo")}
          <textarea className="input" rows={2} value={pickup} onChange={(e) => setPickup(e.target.value)} />
        </label>
        <button type="submit" className="btn btn-orange self-start" disabled={arrived.isPending}>
          {t("arrived")}
        </button>
      </form>,
    );
  }
  if (next.includes("DELIVERED")) {
    blocks.push(
      <div key="delivered" className="flex flex-col gap-3">
        <h3 className="m-0 text-[14px] font-bold">{t("deliveredTitle")}</h3>
        <label className="label">
          {t("comment")}
          <input className="input h-9" value={note} onChange={(e) => setNote(e.target.value)} />
        </label>
        <button type="button" className="btn btn-orange self-start" disabled={delivered.isPending} onClick={() => delivered.mutate(undefined)}>
          {t("delivered")}
        </button>
      </div>,
    );
  }
  if (status === "AWAITING_SHIPPING_PAYMENT" && isAdmin) {
    blocks.push(
      <button key="paid" type="button" className="btn btn-outline self-start" onClick={() => setPaidOpen(true)}>
        {t("markShippingPaid")}
      </button>,
    );
  }
  const canCancel = next.includes("CANCELLED");

  if (!blocks.length && !canCancel) return null;
  return (
    <Section title={t("actions")}>
      <div className="flex flex-col gap-5 p-4">
        {blocks}
        {canCancel && (
          <button type="button" className="btn btn-danger btn-sm self-start" onClick={() => setCancelOpen(true)}>
            {t("cancel")}
          </button>
        )}
      </div>
      <ConfirmDialog
        open={cancelOpen}
        title={t("cancelTitle", { number: o?.number ?? "" })}
        text={t("cancelText")}
        confirmLabel={t("cancel")}
        busy={cancel.isPending}
        onConfirm={() => cancel.mutate(undefined)}
        onClose={() => setCancelOpen(false)}
      >
        <label className="label">
          {t("cancelReason")}
          <textarea className="input" rows={2} value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} />
        </label>
      </ConfirmDialog>
      <ConfirmDialog
        open={paidOpen}
        title={t("markShippingPaid")}
        text={t("markShippingPaidText")}
        confirmLabel={t("markShippingPaid")}
        busy={shippingPaid.isPending}
        onConfirm={() => shippingPaid.mutate(undefined)}
        onClose={() => setPaidOpen(false)}
      />
    </Section>
  );
}

function Items({ staff }: { staff: StaffOrder }) {
  const t = useTranslations("admin.order");
  const ti = useTranslations("itemStatus");
  const tp = useTranslations("productCard");
  const session = useSession();
  const money = useMoney();
  const o = staff.order;
  const canBuy = o?.status === "PURCHASING" && hasRole(session.user, "BUYER", "ADMIN");

  return (
    <Section title={t("items")}>
      <ul className="m-0 list-none p-0">
        {(o?.items ?? []).map((item) => {
          const sku = (item.skuProperties ?? []).map((p) => `${p.name ?? p.nameOriginal}: ${p.value ?? p.valueOriginal}`).join(" · ");
          return (
            <li key={item.id} className="flex flex-col gap-2 border-b border-line p-4 last:border-b-0">
              <div className="grid grid-cols-[56px_minmax(0,1fr)] gap-3">
                <ProductImage src={item.imageUrl} alt="" placeholder={tp("noImage")} sizes="56px" className="aspect-square w-full rounded-sm" />
                <div className="min-w-0 text-[13px]">
                  <b className="line-clamp-2 text-[14px] font-semibold">{item.title}</b>
                  {sku && <span className="block text-subtle">{sku}</span>}
                  <span className="num block text-muted">
                    {t("qtyPrice", { qty: item.quantity ?? 1, cny: formatDecimal(item.priceCny) })} · {money(item.totalUzs)}
                  </span>
                  <span className="mt-1 flex flex-wrap items-center gap-2">
                    {item.status && (
                      <span className={`pill ${item.status === "PURCHASED" ? "pill-navy" : item.status === "PENDING" ? "" : "pill-danger"}`}>
                        {ti(item.status)}
                      </span>
                    )}
                    {item.actualPriceCny != null && <span className="num">{t("actualPrice", { cny: formatDecimal(item.actualPriceCny) })}</span>}
                    {item.taobaoOrderNumber && <span className="num">{t("taobaoOrder", { number: item.taobaoOrderNumber })}</span>}
                    {item.chinaTrackingNumber && <span className="num">{t("chinaTracking", { number: item.chinaTrackingNumber })}</span>}
                  </span>
                  {item.comment && <span className="mt-1 block rounded-[4px] bg-[#fff6e5] px-2 py-1">{t("customerComment", { text: item.comment })}</span>}
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <a href={taobaoUrl(item)} target="_blank" rel="noopener noreferrer" className="btn btn-outline btn-sm">
                  {t("openTaobao")}
                </a>
                {item.skuId && <CopyButton text={item.skuId} label={t("copySku")} />}
              </div>
              {canBuy && item.status === "PENDING" && item.id && (
                <PurchaseItemForm orderId={o?.id ?? ""} item={{ id: item.id, priceCny: item.priceCny }} />
              )}
            </li>
          );
        })}
      </ul>
    </Section>
  );
}

function Prices({ staff }: { staff: StaffOrder }) {
  const t = useTranslations("admin.order");
  const money = useMoney();
  const m = useTranslations("money");
  const o = staff.order;
  const rows: [string, string, boolean?][] = [
    [t("goods"), money(o?.goodsUzs)],
    [t("serviceFee"), money(o?.serviceFeeUzs)],
  ];
  if (o?.promoCode) rows.push([t("promo", { code: o.promoCode }), `−${money(o.promoDiscountUzs)}`]);
  rows.push([t("prepaidShipping"), money(o?.prepaidShippingUzs)]);
  if (o?.realWeightKg != null) rows.push([t("realWeight"), m("kg", { amount: formatDecimal(o.realWeightKg) })]);
  if (o?.finalShippingUzs != null) rows.push([t("finalShipping"), money(o.finalShippingUzs)]);
  if (o?.shippingDifferenceUzs) rows.push([t("shippingDifference"), money(o.shippingDifferenceUzs)]);
  if (o?.priceDifferenceUzs) rows.push([t("priceDifference"), money(o.priceDifferenceUzs)]);
  rows.push([t("total"), money(o?.totalUzs), true]);
  rows.push([t("paid"), money(o?.paidUzs)]);
  if (o?.refundedUzs) rows.push([t("refunded"), money(o.refundedUzs)]);
  rows.push([t("due"), money(o?.amountDueUzs), true]);
  return (
    <Section title={t("prices")}>
      <dl className="m-0 px-4 py-2 text-[13px]">
        {rows.map(([label, value, strong]) => (
          <div key={label} className={`flex justify-between gap-3 border-b border-dashed border-line py-1.5 last:border-b-0 ${strong ? "font-bold" : ""}`}>
            <dt className={strong ? "" : "text-subtle"}>{label}</dt>
            <dd className="num m-0">{value}</dd>
          </div>
        ))}
      </dl>
    </Section>
  );
}

function Delivery({ staff }: { staff: StaffOrder }) {
  const t = useTranslations("admin.order");
  const tch = useTranslations("checkout");
  const locale = useLocale();
  const o = staff.order;
  const a = o?.address;
  const rows: [string, string][] = [];
  if (o?.deliveryMethod) rows.push([t("deliveryMethod"), tch(`delivery${o.deliveryMethod}`)]);
  if (a) rows.push([t("address"), [a.region, a.city, a.district, a.street, a.house, a.apartment, a.landmark, a.phone].filter(Boolean).join(", ")]);
  if (o?.paymentMethod) rows.push([t("paymentMethod"), tch(`provider${o.paymentMethod}`)]);
  if (o?.internationalTrackingNumber) rows.push([t("trackingNumber"), o.internationalTrackingNumber]);
  if (o?.carrier) rows.push([t("carrier"), o.carrier]);
  if (o?.expectedArrivalDate) rows.push([t("expectedArrival"), formatDate(o.expectedArrivalDate, locale)]);
  if (o?.pickupInfo) rows.push([t("pickupInfo"), o.pickupInfo]);
  if (o?.customerComment) rows.push([t("orderComment"), o.customerComment]);
  return (
    <Section title={t("delivery")}>
      <dl className="m-0 flex flex-col gap-2 p-4 text-[13px]">
        {rows.map(([label, value]) => (
          <div key={label}>
            <dt className="text-muted">{label}</dt>
            <dd className="m-0 break-words">{value}</dd>
          </div>
        ))}
      </dl>
    </Section>
  );
}

function History({ staff }: { staff: StaffOrder }) {
  const t = useTranslations("admin.order");
  const ts = useTranslations("status");
  const tr = useTranslations("admin.actorRole");
  const locale = useLocale();
  return (
    <Section title={t("history")}>
      <TableWrap>
        <table className="admin-table">
          <thead>
            <tr>
              <th>{t("when")}</th>
              <th>{t("change")}</th>
              <th>{t("by")}</th>
              <th>{t("comment")}</th>
            </tr>
          </thead>
          <tbody>
            {(staff.history ?? []).map((h, i) => (
              <tr key={`${h.createdAt}-${i}`}>
                <td className="num">{formatDateTime(h.createdAt, locale)}</td>
                <td>
                  {h.fromStatus ? `${ts(h.fromStatus)} → ` : ""}
                  {h.toStatus ? ts(h.toStatus) : ""}
                </td>
                <td>{h.changedByRole ? tr(h.changedByRole) : ""}</td>
                <td className="whitespace-normal">{h.comment}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableWrap>
    </Section>
  );
}

function Comments({ staff }: { staff: StaffOrder }) {
  const t = useTranslations("admin.order");
  const locale = useLocale();
  const id = staff.order?.id ?? "";
  const [text, setText] = useState("");
  const add = useAction(
    async () =>
      unwrap(await api.POST("/api/v1/admin/orders/{id}/comments", { params: { path: { id } }, body: { text: text.trim() } })),
    { success: t("commentAdded"), invalidate: [["admin", "order", id]], onSuccess: () => setText("") },
  );
  return (
    <Section title={t("internalComments")}>
      <div className="flex flex-col gap-3 p-4">
        {(staff.comments ?? []).length === 0 && <p className="m-0 text-[13px] text-muted">{t("noComments")}</p>}
        {(staff.comments ?? []).map((c) => (
          <div key={c.id} className="rounded-sm bg-grey-bg/60 px-3 py-2 text-[13px]">
            <span className="num block text-[12px] text-muted">{formatDateTime(c.createdAt, locale)}</span>
            <span className="whitespace-pre-wrap">{c.text}</span>
          </div>
        ))}
        <form
          className="flex flex-col gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            if (text.trim()) add.mutate(undefined);
          }}
        >
          <textarea className="input" rows={2} maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} aria-label={t("internalComments")} />
          <button type="submit" className="btn btn-navy btn-sm self-start" disabled={!text.trim() || add.isPending}>
            {t("addComment")}
          </button>
        </form>
      </div>
    </Section>
  );
}
