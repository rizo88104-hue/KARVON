"use client";

import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { formatAmount } from "@/lib/format";

export function AdminPageHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
      <h1 className="m-0 text-[22px] font-bold">{title}</h1>
      {children && <div className="flex flex-wrap items-center gap-2">{children}</div>}
    </div>
  );
}

export function Section({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="card overflow-hidden">
      {(title || actions) && (
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line px-4 py-3">
          {title && <h2 className="m-0 text-[16px] font-bold">{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

/** Horizontal scroll wrapper so wide tables never widen the page on a phone. */
export function TableWrap({ children }: { children: ReactNode }) {
  return <div className="overflow-x-auto">{children}</div>;
}

export function Loading() {
  const t = useTranslations("common");
  return (
    <p className="py-10 text-center text-muted" role="status">
      {t("loading")}
    </p>
  );
}

export function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-center gap-3 p-3">
      <button type="button" className="btn btn-outline btn-sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        ←
      </button>
      <span className="num text-[13px]">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        className="btn btn-outline btn-sm"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        →
      </button>
    </div>
  );
}

export function useMoney() {
  const m = useTranslations("money");
  return (v?: number | null) => m("som", { amount: formatAmount(v) });
}

/** "2026-11-02" for date inputs, in local time. */
export function isoDate(d: Date) {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
