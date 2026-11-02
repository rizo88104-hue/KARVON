"use client";

import { useQuery } from "@tanstack/react-query";
import { useLocale, useMessages, useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { OrderStatus } from "@/api/types";
import { EmptyState } from "@/components/EmptyState";
import { useToast } from "@/components/Toast";
import { FORM_QUERY } from "@/lib/admin/roles";
import { useAction } from "@/lib/admin/use-action";
import { formatDateTime } from "@/lib/format";
import { useErrorMessage } from "@/lib/use-error-message";
import { ConfirmDialog } from "./ConfirmDialog";
import { AdminPageHeader, isoDate, Loading, Section, TableWrap, useMoney } from "./ui";

export function FinanceView() {
  const ta = useTranslations("admin");
  return (
    <>
      <AdminPageHeader title={ta("nav.finance")} />
      <div className="flex flex-col gap-4">
        <OrdersReport />
        <MonthlyReport />
        <WalletAdjustSection />
      </div>
    </>
  );
}

/**
 * GET /admin/reports/orders answers ResponseEntity<?>, so the OpenAPI spec has no row type. The table is generic:
 * columns are the row keys, headers come from messages (admin.finance.columns), values are formatted by name.
 */
function OrdersReport() {
  const t = useTranslations("admin.finance");
  const ta = useTranslations("admin");
  const ts = useTranslations("status");
  const locale = useLocale();
  const money = useMoney();
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const columns: Record<string, string> = useMessages().admin.finance.columns;
  const today = new Date();
  const [from, setFrom] = useState(isoDate(new Date(today.getFullYear(), today.getMonth(), 1)));
  const [to, setTo] = useState(isoDate(today));
  const [range, setRange] = useState({ from, to });
  const [downloading, setDownloading] = useState(false);

  const report = useQuery({
    queryKey: ["admin", "report", "orders", range],
    queryFn: async () =>
      unwrap(await api.GET("/api/v1/admin/reports/orders", { params: { query: { from: range.from, to: range.to, format: "json" } } })) as {
        rows?: Record<string, unknown>[];
        totals?: Record<string, unknown>;
      },
  });

  const cell = (key: string, value: unknown) => {
    if (value == null) return "";
    if (key.endsWith("Uzs")) return money(Number(value));
    if (key === "status") return ts(value as OrderStatus);
    if (key === "date") return formatDateTime(String(value), locale);
    return String(value);
  };

  async function download() {
    setDownloading(true);
    try {
      const { data, response } = await api.GET("/api/v1/admin/reports/orders", {
        params: { query: { from: range.from, to: range.to, format: "xlsx" } },
        parseAs: "blob",
      });
      if (!response.ok || !(data instanceof Blob)) throw new Error(`HTTP ${response.status}`);
      const url = URL.createObjectURL(data);
      const a = document.createElement("a");
      a.href = url;
      a.download = `e-market-orders-${range.from}_${range.to}.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
      toast(t("downloaded"));
    } catch (e) {
      toast(errorMessage(e));
    } finally {
      setDownloading(false);
    }
  }

  const rows = report.data?.rows ?? [];
  const keys = rows[0] ? Object.keys(rows[0]) : [];
  const totals = report.data?.totals ?? {};

  return (
    <Section
      title={t("ordersReport")}
      actions={
        <button type="button" className="btn btn-outline btn-sm" disabled={downloading || !rows.length} onClick={download}>
          {downloading ? t("downloading") : t("excel")}
        </button>
      }
    >
      <form
        className="flex flex-wrap items-end gap-2 border-b border-line p-3"
        onSubmit={(e) => {
          e.preventDefault();
          setRange({ from, to });
        }}
      >
        <label className="label">
          <span className="hint">{t("from")}</span>
          <input type="date" className="input h-9" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="label">
          <span className="hint">{t("to")}</span>
          <input type="date" className="input h-9" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
        </label>
        <button type="submit" className="btn btn-navy btn-sm">
          {t("show")}
        </button>
      </form>
      {report.isPending ? (
        <Loading />
      ) : report.isError ? (
        <EmptyState title={ta("loadError")} text={errorMessage(report.error)} />
      ) : (
        <>
          <dl className="m-0 grid grid-cols-2 gap-3 border-b border-line p-4 text-[13px] min-[901px]:grid-cols-6">
            {Object.entries(totals).map(([k, v]) => (
              <div key={k}>
                <dt className="text-muted">{columns[k] ?? k}</dt>
                <dd className="num m-0 font-bold">{k === "orders" ? String(v) : cell(k, v)}</dd>
              </div>
            ))}
          </dl>
          {rows.length === 0 ? (
            <p className="m-0 p-4 text-[13px] text-muted">{t("noRows")}</p>
          ) : (
            <TableWrap>
              <table className="admin-table">
                <thead>
                  <tr>
                    {keys.map((k) => (
                      <th key={k} className={k.endsWith("Uzs") ? "num" : ""}>
                        {columns[k] ?? k}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row, i) => (
                    <tr key={String(row.number ?? i)}>
                      {keys.map((k) => (
                        <td key={k} className={k.endsWith("Uzs") || k === "date" ? "num" : ""}>
                          {cell(k, row[k])}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableWrap>
          )}
        </>
      )}
    </Section>
  );
}

function MonthlyReport() {
  const t = useTranslations("admin.finance");
  const ta = useTranslations("admin");
  const money = useMoney();
  const errorMessage = useErrorMessage();
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const report = useQuery({
    queryKey: ["admin", "report", "monthly", year],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/reports/monthly", { params: { query: { year } } })),
  });
  // Browsers have no Uzbek month names in Intl, so they come from the messages.
  const months: Record<string, string> = useMessages().admin.finance.monthNames;
  const monthName = (m: number) => months[`m${m}`] ?? String(m);

  return (
    <Section
      title={t("monthlyReport")}
      actions={
        <select className="input h-9 w-auto" value={year} onChange={(e) => setYear(Number(e.target.value))} aria-label={t("year")}>
          {[0, 1, 2, 3].map((d) => (
            <option key={d} value={thisYear - d}>
              {thisYear - d}
            </option>
          ))}
        </select>
      }
    >
      {report.isPending ? (
        <Loading />
      ) : report.isError ? (
        <EmptyState title={ta("loadError")} text={errorMessage(report.error)} />
      ) : (
        <TableWrap>
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t("month")}</th>
                <th className="num">{t("columns.orders")}</th>
                <th className="num">{t("revenue")}</th>
                <th className="num">{t("columns.profitUzs")}</th>
              </tr>
            </thead>
            <tbody>
              {(report.data.months ?? []).map((m) => (
                <tr key={m.month}>
                  <td>{monthName(m.month ?? 1)}</td>
                  <td className="num">{m.orders}</td>
                  <td className="num">{money(m.revenueUzs)}</td>
                  <td className="num">{money(m.profitUzs)}</td>
                </tr>
              ))}
              <tr className="font-bold">
                <td>{t("yearTotal")}</td>
                <td className="num">{report.data.orders}</td>
                <td className="num">{money(report.data.revenueUzs)}</td>
                <td className="num">{money(report.data.profitUzs)}</td>
              </tr>
            </tbody>
          </table>
        </TableWrap>
      )}
    </Section>
  );
}

function WalletAdjustSection() {
  const t = useTranslations("admin.finance");
  const [draft, setDraft] = useState("");
  const [phone, setPhone] = useState("");
  const [userId, setUserId] = useState<string | null>(null);
  const users = useQuery({
    queryKey: ["admin", "users", "lookup", phone],
    enabled: phone.length >= 3,
    queryFn: async () =>
      unwrap(
        await api.GET("/api/v1/admin/users", {
          params: { query: { phone, pageable: { page: 0, size: 10 } } },
          querySerializer: FORM_QUERY,
        }),
      ),
  });
  const found = users.data?.content ?? [];
  return (
    <Section title={t("walletAdjust")}>
      <div className="flex flex-col gap-3 p-4">
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setUserId(null);
            setPhone(draft.replace(/[^\d+]/g, ""));
          }}
        >
          <label className="label min-w-[220px] flex-1">
            <span className="hint">{t("customerPhone")}</span>
            <input className="input num h-9" inputMode="tel" placeholder="+998" value={draft} onChange={(e) => setDraft(e.target.value)} />
          </label>
          <button type="submit" className="btn btn-navy btn-sm">
            {t("find")}
          </button>
        </form>
        {phone.length >= 3 && !users.isPending && found.length === 0 && <p className="m-0 text-[13px] text-muted">{t("noUser")}</p>}
        {found.length > 0 && (
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-label={t("customerPhone")}>
            {found.map((u) => (
              <button
                key={u.id}
                type="button"
                role="radio"
                aria-checked={userId === u.id}
                className="chip num"
                onClick={() => setUserId(u.id ?? null)}
              >
                {u.phone}
                {u.fullName ? ` · ${u.fullName}` : ""}
              </button>
            ))}
          </div>
        )}
        {userId && <WalletAdjustForm key={userId} userId={userId} />}
      </div>
    </Section>
  );
}

/** ADMIN: signed amount (+ credit / − debit) with a reason; confirmed in a dialog. */
export function WalletAdjustForm({ userId, onDone }: { userId: string; onDone?: () => void }) {
  const t = useTranslations("admin.finance");
  const money = useMoney();
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [confirm, setConfirm] = useState(false);
  const value = Math.round(Number(amount.replace(/\s/g, "").replace(",", ".")));
  const valid = Number.isFinite(value) && value !== 0 && reason.trim().length > 0;

  const adjust = useAction(
    async () =>
      unwrap(
        await api.POST("/api/v1/admin/wallets/{userId}/adjust", {
          params: { path: { userId } },
          body: { amount: value, reason: reason.trim() },
        }),
      ),
    {
      success: (tx) => t("adjusted", { balance: money(tx.balanceAfterUzs) }),
      invalidate: [["admin", "customer", userId]],
      onSuccess: () => {
        setConfirm(false);
        setAmount("");
        setReason("");
        onDone?.();
      },
    },
  );

  return (
    <form
      className="grid grid-cols-1 gap-2 min-[561px]:grid-cols-[160px_1fr_auto] min-[561px]:items-end"
      onSubmit={(e) => {
        e.preventDefault();
        if (valid) setConfirm(true);
      }}
    >
      <label className="label">
        <span>
          {t("amount")} <span className="hint">{t("amountHint")}</span>
        </span>
        <input className="input num h-9" inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value)} />
      </label>
      <label className="label">
        {t("reason")}
        <input className="input h-9" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <button type="submit" className="btn btn-navy btn-sm" disabled={!valid}>
        {t("adjust")}
      </button>
      <ConfirmDialog
        open={confirm}
        title={t("adjustConfirmTitle")}
        text={t(value > 0 ? "adjustConfirmCredit" : "adjustConfirmDebit", { amount: money(Math.abs(value)) })}
        confirmLabel={t("adjust")}
        busy={adjust.isPending}
        onConfirm={() => adjust.mutate(undefined)}
        onClose={() => setConfirm(false)}
      />
    </form>
  );
}
