"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Image from "next/image";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { isApiError, unwrap } from "@/api/errors";
import type { OrderDetail, PaymentProvider } from "@/api/types";
import { EmptyState } from "@/components/EmptyState";
import { Modal } from "@/components/Modal";
import { PriceTag } from "@/components/PriceTag";
import { ProductImage } from "@/components/ProductImage";
import { StatusTimeline } from "@/components/StatusTimeline";
import { useToast } from "@/components/Toast";
import { Link } from "@/i18n/navigation";
import { formatAmount, formatDate, formatDateTime, formatDecimal } from "@/lib/format";
import { keys, useWallet } from "@/lib/queries";
import { useErrorMessage } from "@/lib/use-error-message";
import { StatusBadge } from "./StatusBadge";

type PayPurpose = "pay" | "pay-shipping" | "pay-price-difference";

export function OrderDetailView({ number }: { number: string }) {
  const t = useTranslations("order");
  const tc = useTranslations("common");
  const errorMessage = useErrorMessage();
  const locale = useLocale();

  const order = useQuery({
    queryKey: keys.order(number),
    queryFn: async () => unwrap(await api.GET("/api/v1/orders/{number}", { params: { path: { number } } })),
  });

  if (order.isPending) {
    return (
      <p className="py-14 text-center text-muted" role="status">
        {tc("loading")}
      </p>
    );
  }
  if (order.isError) {
    const missing = isApiError(order.error) && (order.error.status === 404 || order.error.status === 403);
    return (
      <EmptyState
        title={missing ? t("notFoundTitle") : t("error")}
        text={missing ? t("notFoundText") : errorMessage(order.error)}
        action={
          <Link href="/account/orders" className="btn btn-navy">
            {t("back")}
          </Link>
        }
      />
    );
  }

  const o = order.data;
  return (
    <>
      <Link href="/account/orders" className="mb-3 inline-block text-[14px] text-muted hover:text-navy hover:underline">
        ← {t("back")}
      </Link>
      <div className="mb-4 flex flex-wrap items-center gap-x-3 gap-y-1">
        <h1 className="num m-0 text-[24px] font-bold">{t("title", { number: o.number ?? number })}</h1>
        <StatusBadge status={o.status} />
        <span className="num w-full text-[13px] text-muted">{t("createdAt", { date: formatDateTime(o.createdAt, locale) })}</span>
      </div>

      <OrderActions order={o} />

      <div className="grid grid-cols-1 items-start gap-4 min-[1101px]:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-4">
          <Items order={o} />
          <Summary order={o} />
        </div>
        <div className="flex min-w-0 flex-col gap-4">
          <section className="card p-4">
            <h2 className="mt-0 mb-3 text-[17px] font-bold">{t("timeline")}</h2>
            <StatusTimeline steps={o.timeline ?? []} />
          </section>
          <Tracking order={o} />
          <Photos order={o} />
          <DeliveryInfo order={o} />
        </div>
      </div>
    </>
  );
}

function OrderActions({ order: o }: { order: OrderDetail }) {
  const t = useTranslations("order");
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const qc = useQueryClient();
  const locale = useLocale();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [refundOpen, setRefundOpen] = useState(false);
  const [reason, setReason] = useState("");
  const number = o.number ?? "";

  const done = (data: OrderDetail) => {
    qc.setQueryData(keys.order(number), data);
    void qc.invalidateQueries({ queryKey: ["orders"] });
    void qc.invalidateQueries({ queryKey: ["wallet"] });
  };
  const decide = useMutation({
    mutationFn: async (action: "ACCEPT" | "REFUND") =>
      unwrap(await api.POST("/api/v1/orders/{number}/decision", { params: { path: { number } }, body: { action } })),
    onSuccess: (data) => {
      done(data);
      setRefundOpen(false);
      toast(t("decisionSaved"));
    },
    onError: (e) => toast(errorMessage(e)),
  });
  const cancel = useMutation({
    mutationFn: async () =>
      unwrap(
        await api.POST("/api/v1/orders/{number}/cancel", {
          params: { path: { number } },
          body: { reason: reason.trim() || undefined },
        }),
      ),
    onSuccess: (data) => {
      done(data);
      setCancelOpen(false);
      toast(t("cancelled"));
    },
    onError: (e) => toast(errorMessage(e)),
  });

  const status = o.status;
  const due = o.amountDueUzs ?? 0;
  let pay: { purpose: PayPurpose; label: string } | null = null;
  if (status === "AWAITING_PAYMENT") pay = { purpose: "pay", label: t("pay") };
  else if (status === "AWAITING_SHIPPING_PAYMENT") pay = { purpose: "pay-shipping", label: t("payShipping") };
  else if (due > 0 && (o.priceDifferenceUzs ?? 0) > 0 && status !== "CANCELLED")
    pay = { purpose: "pay-price-difference", label: t("payPriceDifference") };

  const decision = status === "OUT_OF_STOCK" || status === "PRICE_CHANGED";
  const cancellable = status === "AWAITING_PAYMENT" || status === "PAID";
  if (!pay && !decision && !cancellable) return null;

  return (
    <section className="card mb-4 flex flex-col gap-3 border-2 border-orange p-4">
      {status === "AWAITING_PAYMENT" && o.paymentDeadline && (
        <p className="m-0 font-semibold">{t("payBefore", { time: formatDateTime(o.paymentDeadline, locale) })}</p>
      )}
      {decision && (
        <>
          <p className="m-0">{status === "OUT_OF_STOCK" ? t("decisionOutOfStock") : t("decisionPriceChanged")}</p>
          <div className="flex flex-col gap-2 min-[561px]:flex-row">
            <button type="button" className="btn btn-orange" disabled={decide.isPending} onClick={() => decide.mutate("ACCEPT")}>
              {t("accept")}
            </button>
            <button type="button" className="btn btn-outline" disabled={decide.isPending} onClick={() => setRefundOpen(true)}>
              {t("refund")}
            </button>
          </div>
        </>
      )}
      {pay && <PayPanel number={number} purpose={pay.purpose} label={pay.label} amount={due} />}
      {cancellable && (
        <button type="button" className="link-btn self-start" onClick={() => setCancelOpen(true)}>
          {t("cancel")}
        </button>
      )}

      <Modal open={cancelOpen} onClose={() => setCancelOpen(false)} title={t("cancelTitle")}>
        <p className="m-0 text-subtle">{t("cancelText")}</p>
        <label className="flex flex-col gap-1.5 text-[14px]">
          <span className="font-semibold">{t("cancelReason")}</span>
          <textarea className="input" rows={2} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
        </label>
        <button type="button" className="btn btn-navy" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
          {t("cancelConfirm")}
        </button>
      </Modal>
      <Modal open={refundOpen} onClose={() => setRefundOpen(false)} title={t("refund")}>
        <p className="m-0 text-subtle">{t("cancelText")}</p>
        <button type="button" className="btn btn-navy" disabled={decide.isPending} onClick={() => decide.mutate("REFUND")}>
          {t("cancelConfirm")}
        </button>
      </Modal>
    </section>
  );
}

const PROVIDERS: PaymentProvider[] = ["PAYME", "CLICK", "WALLET"];

function PayPanel({ number, purpose, label, amount }: { number: string; purpose: PayPurpose; label: string; amount: number }) {
  const t = useTranslations("order");
  const tch = useTranslations("checkout");
  const m = useTranslations("money");
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const qc = useQueryClient();
  const wallet = useWallet(0, 1);
  const balance = wallet.data?.balanceUzs ?? 0;
  const [provider, setProvider] = useState<PaymentProvider>("PAYME");

  const start = useMutation({
    mutationFn: async () => {
      const opts = { params: { path: { number } }, body: { provider } };
      const res =
        purpose === "pay"
          ? await api.POST("/api/v1/orders/{number}/pay", opts)
          : purpose === "pay-shipping"
            ? await api.POST("/api/v1/orders/{number}/pay-shipping", opts)
            : await api.POST("/api/v1/orders/{number}/pay-price-difference", opts);
      return unwrap(res);
    },
    onSuccess: (payment) => {
      if (payment.checkoutUrl) {
        toast(tch("redirecting"));
        window.location.assign(payment.checkoutUrl);
        return;
      }
      toast(t("paidToast"));
      void qc.invalidateQueries({ queryKey: keys.order(number) });
      void qc.invalidateQueries({ queryKey: ["orders"] });
      void qc.invalidateQueries({ queryKey: ["wallet"] });
    },
    onError: (e) => toast(errorMessage(e)),
  });

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <b>{t("amountDue")}</b>
        <PriceTag amount={amount} size="lg" />
      </div>
      <fieldset className="m-0 flex flex-wrap gap-2 border-0 p-0">
        <legend className="sr-only">{t("choosePayment")}</legend>
        {PROVIDERS.map((p) => {
          const short = p === "WALLET" && balance < amount;
          return (
            <label key={p} className={`chip gap-2 ${short ? "opacity-50" : ""}`} aria-checked={provider === p}>
              <input
                type="radio"
                name={`provider-${purpose}`}
                className="sr-only"
                checked={provider === p}
                disabled={short}
                onChange={() => setProvider(p)}
              />
              {tch(`provider${p}`)}
              {p === "WALLET" && <small className="num text-muted">{m("som", { amount: formatAmount(balance) })}</small>}
            </label>
          );
        })}
      </fieldset>
      <button type="button" className="btn btn-orange self-stretch min-[561px]:self-start" disabled={start.isPending} onClick={() => start.mutate()}>
        {label}
      </button>
    </div>
  );
}

function Items({ order: o }: { order: OrderDetail }) {
  const t = useTranslations("order");
  const ti = useTranslations("itemStatus");
  const tp = useTranslations("productCard");
  return (
    <section className="card">
      <h2 className="m-0 border-b border-line px-4 py-3 text-[17px] font-bold">{t("items")}</h2>
      <ul className="m-0 list-none p-0">
        {(o.items ?? []).map((item) => {
          const sku = (item.skuProperties ?? []).map((p) => p.value ?? p.valueOriginal).filter(Boolean).join(" · ");
          return (
            <li key={item.id} className="grid grid-cols-[64px_minmax(0,1fr)] gap-3 border-b border-line p-4 last:border-b-0">
              <Link href={`/product/${encodeURIComponent(item.externalProductId ?? "")}`} tabIndex={-1} aria-hidden="true">
                <ProductImage src={item.imageUrl} alt="" placeholder={tp("noImage")} sizes="64px" className="aspect-square w-full rounded-sm" />
              </Link>
              <div className="flex min-w-0 flex-col gap-1 text-[14px]">
                <Link href={`/product/${encodeURIComponent(item.externalProductId ?? "")}`} className="line-clamp-2 hover:text-navy">
                  {item.title}
                </Link>
                {sku && <span className="self-start rounded-[4px] bg-grey-bg px-1.5 py-0.5 text-[12px] text-subtle">{sku}</span>}
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span className="num text-muted">{t("qty", { count: item.quantity ?? 1 })}</span>
                  <PriceTag amount={item.totalUzs} size="sm" />
                  {item.status && item.status !== "PENDING" && (
                    <span className={`text-[12px] font-bold ${item.status === "PURCHASED" ? "text-success" : "text-danger"}`}>
                      {ti(item.status)}
                    </span>
                  )}
                </div>
                {item.actualPriceCny != null && item.actualPriceCny !== item.priceCny && (
                  <span className="num text-[13px] text-danger">{t("actualPrice", { amount: formatDecimal(item.actualPriceCny) })}</span>
                )}
                {item.chinaTrackingNumber && (
                  <span className="num text-[13px] text-muted">{t("chinaTracking", { number: item.chinaTrackingNumber })}</span>
                )}
                {item.comment && <span className="text-[13px] text-muted">{t("yourComment", { text: item.comment })}</span>}
                {item.staffComment && <span className="text-[13px] text-subtle">{t("staffComment", { text: item.staffComment })}</span>}
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function Summary({ order: o }: { order: OrderDetail }) {
  const t = useTranslations("order");
  const m = useTranslations("money");
  const som = (v?: number) => m("som", { amount: formatAmount(v) });
  const rows: [string, string, boolean?][] = [
    [t("goods"), som(o.goodsUzs)],
    [t("serviceFee"), som(o.serviceFeeUzs)],
    [t("prepaidShipping"), som(o.prepaidShippingUzs)],
  ];
  if (o.realWeightKg != null) rows.push([t("realWeight"), m("kg", { amount: formatDecimal(o.realWeightKg) })]);
  if (o.finalShippingUzs != null) rows.push([t("finalShipping"), som(o.finalShippingUzs)]);
  if (o.shippingDifferenceUzs) rows.push([t("shippingDifference"), som(o.shippingDifferenceUzs)]);
  if (o.priceDifferenceUzs) rows.push([t("priceDifference"), som(o.priceDifferenceUzs)]);
  rows.push([t("total"), som(o.totalUzs), true]);
  rows.push([t("paid"), som(o.paidUzs)]);
  if (o.refundedUzs) rows.push([t("refunded"), som(o.refundedUzs)]);
  if ((o.amountDueUzs ?? 0) > 0) rows.push([t("amountDue"), som(o.amountDueUzs), true]);

  return (
    <section className="card p-4">
      <h2 className="mt-0 mb-2 text-[17px] font-bold">{t("summary")}</h2>
      <dl className="m-0 text-[14px]">
        {rows.map(([label, value, strong]) => (
          <div key={label} className={`flex justify-between gap-3 border-b border-dashed border-line py-2 last:border-b-0 ${strong ? "font-bold" : ""}`}>
            <dt className={strong ? "" : "text-subtle"}>{label}</dt>
            <dd className="num m-0">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function Tracking({ order: o }: { order: OrderDetail }) {
  const t = useTranslations("order");
  const locale = useLocale();
  const rows: [string, string][] = [];
  if (o.internationalTrackingNumber) rows.push([t("trackingNumber"), o.internationalTrackingNumber]);
  if (o.carrier) rows.push([t("carrier"), o.carrier]);
  if (o.expectedArrivalDate) rows.push([t("expectedArrival"), formatDate(o.expectedArrivalDate, locale)]);
  if (o.pickupInfo) rows.push([t("pickupInfo"), o.pickupInfo]);
  return (
    <section className="card p-4">
      <h2 className="mt-0 mb-2 text-[17px] font-bold">{t("tracking")}</h2>
      {rows.length === 0 ? (
        <p className="m-0 text-[14px] text-muted">{t("noTracking")}</p>
      ) : (
        <dl className="m-0 text-[14px]">
          {rows.map(([label, value]) => (
            <div key={label} className="flex flex-col py-1.5 min-[561px]:flex-row min-[561px]:justify-between min-[561px]:gap-3">
              <dt className="text-muted">{label}</dt>
              <dd className="num m-0 break-all">{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}

function Photos({ order: o }: { order: OrderDetail }) {
  const t = useTranslations("order");
  const photos = o.photos ?? [];
  return (
    <section className="card p-4">
      <h2 className="mt-0 mb-3 text-[17px] font-bold">{t("photos")}</h2>
      {photos.length === 0 ? (
        <p className="m-0 text-[14px] text-muted">{t("noPhotos")}</p>
      ) : (
        <div className="grid grid-cols-3 gap-2">
          {photos.map((p, i) =>
            p.url ? (
              <a key={p.id ?? i} href={p.url} target="_blank" rel="noopener" className="relative block aspect-square overflow-hidden rounded-sm bg-grey-bg">
                <Image src={p.url} alt={t("photo", { index: i + 1 })} fill sizes="160px" className="object-cover" />
              </a>
            ) : null,
          )}
        </div>
      )}
    </section>
  );
}

function DeliveryInfo({ order: o }: { order: OrderDetail }) {
  const t = useTranslations("order");
  const tch = useTranslations("checkout");
  const a = o.address;
  const address = a ? [a.region, a.city, a.district, a.street, a.house, a.apartment].filter(Boolean).join(", ") : null;
  return (
    <section className="card p-4 text-[14px]">
      <h2 className="mt-0 mb-2 text-[17px] font-bold">{t("delivery")}</h2>
      <dl className="m-0 flex flex-col gap-1.5">
        {o.deliveryMethod && (
          <div className="flex justify-between gap-3">
            <dt className="text-muted">{t("deliveryMethod")}</dt>
            <dd className="m-0 text-right">{tch(`delivery${o.deliveryMethod}`)}</dd>
          </div>
        )}
        {address && (
          <div className="flex justify-between gap-3">
            <dt className="text-muted">{t("address")}</dt>
            <dd className="m-0 text-right">{address}</dd>
          </div>
        )}
        {o.paymentMethod && (
          <div className="flex justify-between gap-3">
            <dt className="text-muted">{t("paymentMethod")}</dt>
            <dd className="m-0 text-right">{tch(`provider${o.paymentMethod}`)}</dd>
          </div>
        )}
      </dl>
    </section>
  );
}
