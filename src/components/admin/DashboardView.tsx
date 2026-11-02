"use client";

import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { OrderStatus } from "@/api/types";
import { StatusBadge } from "@/components/account/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { Link } from "@/i18n/navigation";
import { useErrorMessage } from "@/lib/use-error-message";
import { DailyCharts } from "./DailyCharts";
import { AdminPageHeader, isoDate, Loading, Section, TableWrap, useMoney } from "./ui";

const STATUS_ORDER: OrderStatus[] = [
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

export function DashboardView() {
  const t = useTranslations("admin.dashboard");
  const ts = useTranslations("status");
  const ta = useTranslations("admin");
  const money = useMoney();
  const errorMessage = useErrorMessage();
  const [from, setFrom] = useState(() => isoDate(new Date(Date.now() - 29 * 86_400_000)));
  const [to, setTo] = useState(() => isoDate(new Date()));

  const dashboard = useQuery({
    queryKey: ["admin", "dashboard", from, to],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/dashboard", { params: { query: { from, to } } })),
  });
  const delayed = useQuery({
    queryKey: ["admin", "delayed"],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/orders/delayed")),
  });

  const d = dashboard.data;
  const byStatus = d?.ordersByStatus ?? {};
  const maxCount = Math.max(1, ...Object.values(byStatus).map(Number));

  return (
    <>
      <AdminPageHeader title={ta("nav.dashboard")}>
        <label className="label">
          <span className="hint">{t("from")}</span>
          <input type="date" className="input h-9" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="label">
          <span className="hint">{t("to")}</span>
          <input type="date" className="input h-9" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
        </label>
      </AdminPageHeader>

      {dashboard.isPending ? (
        <Loading />
      ) : dashboard.isError ? (
        <EmptyState title={ta("loadError")} text={errorMessage(dashboard.error)} />
      ) : (
        <div className="flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-3 min-[901px]:grid-cols-4">
            <Kpi label={t("ordersToday")} value={String(d?.ordersToday ?? 0)} />
            <Kpi label={t("ordersInPeriod")} value={String(d?.ordersInPeriod ?? 0)} />
            <Kpi label={t("revenue")} value={money(d?.revenueUzs)} />
            <Kpi label={t("averageOrder")} value={money(d?.averageOrderUzs)} />
            <Kpi label={t("serviceIncome")} value={money(d?.serviceIncomeUzs)} />
            <Kpi label={t("shippingIncome")} value={money(d?.shippingIncomeUzs)} />
            <Kpi label={t("delayed")} value={String(d?.delayedCount ?? 0)} alert={(d?.delayedCount ?? 0) > 0} />
          </div>

          <DailyCharts points={d?.dailySeries ?? []} />

          <div className="grid grid-cols-1 gap-4 min-[1101px]:grid-cols-[minmax(0,1fr)_minmax(0,1.3fr)]">
            <Section title={t("byStatus")}>
              <ul className="m-0 flex list-none flex-col gap-2 p-4">
                {STATUS_ORDER.map((s) => {
                  const count = Number(byStatus[s] ?? 0);
                  return (
                    <li key={s} className="grid grid-cols-[minmax(0,150px)_1fr_36px] items-center gap-3 text-[13px]">
                      <span className="truncate">{ts(s)}</span>
                      <span className="h-2.5 overflow-hidden rounded-full bg-grey-bg">
                        <span
                          className="block h-full rounded-full bg-navy"
                          style={{ width: `${(count / maxCount) * 100}%` }}
                        />
                      </span>
                      <span className="num text-right font-semibold">{count}</span>
                    </li>
                  );
                })}
              </ul>
            </Section>

            <Section title={t("delayedOrders")}>
              {delayed.isPending ? (
                <Loading />
              ) : !delayed.data?.length ? (
                <p className="m-0 p-4 text-[14px] text-muted">{t("noDelayed")}</p>
              ) : (
                <TableWrap>
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>{t("number")}</th>
                        <th>{t("status")}</th>
                        <th className="num">{t("hoursInStatus")}</th>
                        <th className="num">{t("overdue")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {delayed.data.map((o) => (
                        <tr key={o.orderId}>
                          <td>
                            <Link href={`/admin/orders/${o.orderId}`} className="num font-semibold text-navy hover:underline">
                              {o.number}
                            </Link>
                          </td>
                          <td>
                            <StatusBadge status={o.status} />
                          </td>
                          <td className="num">
                            {o.hoursInStatus} / {o.limitHours}
                          </td>
                          <td className="num font-semibold text-danger">+{o.overdueHours}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </TableWrap>
              )}
            </Section>
          </div>
        </div>
      )}
    </>
  );
}

function Kpi({ label, value, alert }: { label: string; value: string; alert?: boolean }) {
  return (
    <div className="card p-4">
      <span className="text-[12px] text-muted">{label}</span>
      <b className={`num mt-1 block text-[20px] leading-tight ${alert ? "text-danger" : ""}`}>{value}</b>
    </div>
  );
}
