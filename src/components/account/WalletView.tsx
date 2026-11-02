"use client";

import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { EmptyState } from "@/components/EmptyState";
import { PriceTag } from "@/components/PriceTag";
import { formatAmount, formatDateTime } from "@/lib/format";
import { useWallet } from "@/lib/queries";
import { useErrorMessage } from "@/lib/use-error-message";

const PAGE_SIZE = 20;

export function WalletView() {
  const t = useTranslations("wallet");
  const tc = useTranslations("common");
  const m = useTranslations("money");
  const locale = useLocale();
  const errorMessage = useErrorMessage();
  const [page, setPage] = useState(0);
  const wallet = useWallet(page, PAGE_SIZE);

  if (wallet.isPending) {
    return (
      <p className="py-14 text-center text-muted" role="status">
        {tc("loading")}
      </p>
    );
  }
  if (wallet.isError) return <EmptyState title={t("error")} text={errorMessage(wallet.error)} />;

  const tx = wallet.data.transactions;
  const list = tx?.content ?? [];
  const totalPages = tx?.totalPages ?? 1;

  return (
    <>
      <h1 className="mt-0 mb-4 text-[24px] font-bold">{t("title")}</h1>
      <section className="mb-4 flex flex-col gap-1 rounded-lg bg-navy px-6 py-7 text-white">
        <span className="text-[13px] font-bold tracking-[0.14em] uppercase">{t("balance")}</span>
        <PriceTag amount={wallet.data.balanceUzs} size="xl" />
        <span className="text-[14px] opacity-80">{t("balanceHint")}</span>
      </section>

      <section className="card">
        <h2 className="m-0 border-b border-line px-4 py-3 text-[17px] font-bold">{t("history")}</h2>
        {list.length === 0 ? (
          <p className="m-0 p-4 text-muted">{t("empty")}</p>
        ) : (
          <ul className="m-0 list-none p-0">
            {list.map((item) => {
              const amount = item.amountUzs ?? 0;
              return (
                <li key={item.id} className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1 border-b border-line px-4 py-3 last:border-b-0">
                  <div className="min-w-0">
                    <b className="block text-[14px]">{item.type ? t(`type${item.type}`) : ""}</b>
                    <span className="num text-[13px] text-muted">{formatDateTime(item.createdAt, locale)}</span>
                    {item.comment && <span className="block text-[13px] text-subtle">{item.comment}</span>}
                  </div>
                  <div className="text-right">
                    <span className={`num block font-bold ${amount >= 0 ? "text-success" : "text-ink"}`}>
                      {amount > 0 ? "+" : amount < 0 ? "−" : ""}
                      {m("som", { amount: formatAmount(Math.abs(amount)) })}
                    </span>
                    <span className="num text-[12px] text-muted">{m("som", { amount: formatAmount(item.balanceAfterUzs) })}</span>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>
      {totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3">
          <button type="button" className="btn btn-outline h-9" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            ←
          </button>
          <span className="num text-[14px]">
            {page + 1} / {totalPages}
          </span>
          <button type="button" className="btn btn-outline h-9" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
            →
          </button>
        </div>
      )}
    </>
  );
}
