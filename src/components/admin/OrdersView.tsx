"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { OrderStatus } from "@/api/types";
import { StatusBadge } from "@/components/account/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { Modal } from "@/components/Modal";
import { useToast } from "@/components/Toast";
import { Link } from "@/i18n/navigation";
import { useAction } from "@/lib/admin/use-action";
import { formatDateTime } from "@/lib/format";
import { useErrorMessage } from "@/lib/use-error-message";
import { AdminPageHeader, Loading, Pager, Section, TableWrap, useMoney } from "./ui";

export const ALL_STATUSES: OrderStatus[] = [
  "AWAITING_PAYMENT",
  "PAID",
  "PURCHASING",
  "OUT_OF_STOCK",
  "PRICE_CHANGED",
  "PURCHASED",
  "AT_WAREHOUSE",
  "AWAITING_SHIPPING_PAYMENT",
  "IN_TRANSIT",
  "ARRIVED",
  "DELIVERED",
  "CANCELLED",
];

const PAGE_SIZE = 20;
/** Order dates are Tashkent days. */
const TZ = "+05:00";

type Filters = { status: string; from: string; to: string; phone: string; number: string };
const EMPTY: Filters = { status: "", from: "", to: "", phone: "", number: "" };

export function OrdersView() {
  const t = useTranslations("admin.orders");
  const ta = useTranslations("admin");
  const ts = useTranslations("status");
  const locale = useLocale();
  const money = useMoney();
  const errorMessage = useErrorMessage();
  const [draft, setDraft] = useState<Filters>(EMPTY);
  const [filters, setFilters] = useState<Filters>(EMPTY);
  const [page, setPage] = useState(0);
  // order id -> order number (kept across pages so bulk results can name the orders)
  const [selected, setSelected] = useState<Map<string, string>>(new Map());
  const [bulk, setBulk] = useState<"IN_TRANSIT" | "ARRIVED" | null>(null);

  const orders = useQuery({
    queryKey: ["admin", "orders", filters, page],
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const nextDay = (d: string) => {
        const date = new Date(`${d}T00:00:00${TZ}`);
        date.setUTCDate(date.getUTCDate() + 1);
        return date.toISOString();
      };
      return unwrap(
        await api.GET("/api/v1/admin/orders", {
          params: {
            query: {
              status: (filters.status || undefined) as OrderStatus | undefined,
              from: filters.from ? new Date(`${filters.from}T00:00:00${TZ}`).toISOString() : undefined,
              to: filters.to ? nextDay(filters.to) : undefined,
              phone: filters.phone.trim() || undefined,
              number: filters.number.trim() || undefined,
              page,
              size: PAGE_SIZE,
            },
          },
        }),
      );
    },
  });

  const rows = orders.data?.content ?? [];
  const allOnPage = rows.length > 0 && rows.every((r) => r.id && selected.has(r.id));
  const toggle = (id: string, number: string, on: boolean) =>
    setSelected((prev) => {
      const next = new Map(prev);
      if (on) next.set(id, number);
      else next.delete(id);
      return next;
    });

  return (
    <>
      <AdminPageHeader title={ta("nav.orders")} />

      <form
        className="card mb-4 grid grid-cols-2 gap-3 p-3 min-[901px]:grid-cols-[repeat(5,minmax(0,1fr))_auto]"
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setSelected(new Map());
          setFilters(draft);
        }}
      >
        <label className="label">
          <span className="hint">{t("status")}</span>
          <select className="input h-9" value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value })}>
            <option value="">{t("allStatuses")}</option>
            {ALL_STATUSES.map((s) => (
              <option key={s} value={s}>
                {ts(s)}
              </option>
            ))}
          </select>
        </label>
        <label className="label">
          <span className="hint">{t("from")}</span>
          <input type="date" className="input h-9" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
        </label>
        <label className="label">
          <span className="hint">{t("to")}</span>
          <input type="date" className="input h-9" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
        </label>
        <label className="label">
          <span className="hint">{t("phone")}</span>
          <input className="input num h-9" inputMode="tel" value={draft.phone} onChange={(e) => setDraft({ ...draft, phone: e.target.value })} />
        </label>
        <label className="label">
          <span className="hint">{t("number")}</span>
          <input className="input num h-9" value={draft.number} onChange={(e) => setDraft({ ...draft, number: e.target.value })} />
        </label>
        <div className="col-span-2 flex items-end gap-2 min-[901px]:col-span-1">
          <button type="submit" className="btn btn-navy btn-sm">
            {t("apply")}
          </button>
          <button
            type="button"
            className="btn btn-outline btn-sm"
            onClick={() => {
              setDraft(EMPTY);
              setFilters(EMPTY);
              setPage(0);
            }}
          >
            {t("reset")}
          </button>
        </div>
      </form>

      {selected.size > 0 && (
        <div className="card mb-3 flex flex-wrap items-center gap-2 border-2 border-orange px-3 py-2 text-[14px]">
          <b>{t("selected", { count: selected.size })}</b>
          <button type="button" className="btn btn-navy btn-sm" onClick={() => setBulk("IN_TRANSIT")}>
            {t("bulkShip")}
          </button>
          <button type="button" className="btn btn-navy btn-sm" onClick={() => setBulk("ARRIVED")}>
            {t("bulkArrived")}
          </button>
          <button type="button" className="link-btn ml-auto" onClick={() => setSelected(new Map())}>
            {t("clearSelection")}
          </button>
        </div>
      )}

      <Section>
        {orders.isPending ? (
          <Loading />
        ) : orders.isError ? (
          <EmptyState title={ta("loadError")} text={errorMessage(orders.error)} />
        ) : rows.length === 0 ? (
          <p className="m-0 p-4 text-muted">{t("empty")}</p>
        ) : (
          <TableWrap>
            <table className="admin-table">
              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      className="accent-navy"
                      checked={allOnPage}
                      aria-label={t("selectPage")}
                      onChange={(e) => rows.forEach((r) => r.id && toggle(r.id, r.number ?? "", e.target.checked))}
                    />
                  </th>
                  <th>{t("number")}</th>
                  <th>{t("created")}</th>
                  <th>{t("phone")}</th>
                  <th>{t("status")}</th>
                  <th className="num">{t("items")}</th>
                  <th className="num">{t("total")}</th>
                  <th className="num">{t("due")}</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((o) => (
                  <tr key={o.id}>
                    <td>
                      <input
                        type="checkbox"
                        className="accent-navy"
                        checked={!!o.id && selected.has(o.id)}
                        aria-label={t("selectOrder", { number: o.number ?? "" })}
                        onChange={(e) => o.id && toggle(o.id, o.number ?? "", e.target.checked)}
                      />
                    </td>
                    <td>
                      <Link href={`/admin/orders/${o.id}`} className="num font-semibold text-navy hover:underline">
                        {o.number}
                      </Link>
                    </td>
                    <td className="num">{formatDateTime(o.createdAt, locale)}</td>
                    <td className="num">{o.customerPhone}</td>
                    <td>
                      <StatusBadge status={o.status} />
                    </td>
                    <td className="num">{o.itemCount}</td>
                    <td className="num">{money(o.totalUzs)}</td>
                    <td className={`num ${(o.amountDueUzs ?? 0) > 0 ? "font-semibold text-danger" : "text-muted"}`}>
                      {money(o.amountDueUzs)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableWrap>
        )}
        <Pager page={page} totalPages={orders.data?.totalPages ?? 0} onChange={setPage} />
      </Section>

      <BulkDialog
        target={bulk}
        orders={selected}
        onClose={() => setBulk(null)}
        onDone={() => {
          setBulk(null);
          setSelected(new Map());
        }}
      />
    </>
  );
}

function BulkDialog({
  target,
  orders,
  onClose,
  onDone,
}: {
  target: "IN_TRANSIT" | "ARRIVED" | null;
  orders: Map<string, string>;
  onClose: () => void;
  onDone: () => void;
}) {
  const t = useTranslations("admin.orders");
  const toast = useToast();
  const [tracking, setTracking] = useState("");
  const ids = [...orders.keys()];
  const [failed, setFailed] = useState<{ id: string; code: string }[]>([]);

  const run = useAction(
    async () =>
      unwrap(
        await api.POST("/api/v1/admin/orders/bulk-status", {
          body: { orderIds: ids, toStatus: target ?? "IN_TRANSIT", trackingNumber: tracking.trim() || undefined },
        }),
      ),
    {
      success: (results) =>
        t("bulkResult", {
          changed: results.filter((r) => r.changed).length,
          failed: results.filter((r) => !r.changed).length,
        }),
      invalidate: [["admin", "orders"]],
      onSuccess: (results) => {
        const bad = results.filter((r) => !r.changed).map((r) => ({ id: r.orderId ?? "", code: r.errorCode ?? "" }));
        setFailed(bad);
        if (bad.length === 0) {
          setTracking("");
          onDone();
        }
      },
    },
  );

  const needsTracking = target === "IN_TRANSIT";
  return (
    <Modal
      open={target !== null}
      onClose={() => {
        setFailed([]);
        onClose();
      }}
      title={target === "IN_TRANSIT" ? t("bulkShipTitle", { count: ids.length }) : t("bulkArrivedTitle", { count: ids.length })}
    >
      <form
        className="flex flex-col gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (needsTracking && !tracking.trim()) {
            toast(t("trackingRequired"));
            return;
          }
          run.mutate(undefined);
        }}
      >
        {needsTracking && (
          <label className="label">
            {t("trackingNumber")}
            <input className="input num" value={tracking} onChange={(e) => setTracking(e.target.value)} autoFocus />
          </label>
        )}
        {failed.length > 0 && (
          <div className="rounded-sm bg-[#fde8e7] p-3 text-[13px] text-danger" role="alert">
            <b className="block">{t("bulkFailed")}</b>
            <ul className="m-0 mt-1 pl-4">
              {failed.map((f) => (
                <li key={f.id} className="num">
                  {orders.get(f.id) ?? f.id} — {f.code}
                </li>
              ))}
            </ul>
          </div>
        )}
        <button type="submit" className="btn btn-orange" disabled={run.isPending}>
          {t("bulkApply")}
        </button>
      </form>
    </Modal>
  );
}
