"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useEffect, useMemo } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { api } from "@/api/client";
import { isApiError, unwrap } from "@/api/errors";
import type { CartTotals, DeliveryMethod, PaymentProvider } from "@/api/types";
import { Modal } from "@/components/Modal";
import { PriceTag } from "@/components/PriceTag";
import { useToast } from "@/components/Toast";
import { Link, useRouter } from "@/i18n/navigation";
import { formatAmount } from "@/lib/format";
import { keys, useAddresses, useWallet } from "@/lib/queries";
import { CONTENT_PAGES } from "@/lib/site";
import { useErrorMessage } from "@/lib/use-error-message";

const DELIVERY: DeliveryMethod[] = ["CARGO_PICKUP", "COURIER", "POST"];
const PROVIDERS: PaymentProvider[] = ["PAYME", "CLICK", "WALLET"];

type Props = { open: boolean; onClose: () => void; totals?: CartTotals };

/**
 * Delivery method + address, payment method (Payme, Click, balance) and the public offer checkbox.
 * Creates the order from the selected cart lines, starts the payment and redirects to its checkoutUrl.
 */
export function CheckoutModal({ open, onClose, totals }: Props) {
  const t = useTranslations("checkout");
  const tc = useTranslations("common");
  const m = useTranslations("money");
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const qc = useQueryClient();
  const addresses = useAddresses();
  const wallet = useWallet(0, 1);
  const balance = wallet.data?.balanceUzs ?? 0;

  const schema = useMemo(
    () =>
      z
        .object({
          deliveryMethod: z.enum(["POST", "CARGO_PICKUP", "COURIER"]),
          addressId: z.string().optional(),
          provider: z.enum(["PAYME", "CLICK", "WALLET"]),
          comment: z.string().max(500).optional(),
          offer: z.boolean().refine((v) => v, { message: t("offerRequired") }),
        })
        .superRefine((v, ctx) => {
          if (v.deliveryMethod !== "CARGO_PICKUP" && !v.addressId) {
            ctx.addIssue({ code: "custom", path: ["addressId"], message: t("addressRequired") });
          }
        }),
    [t],
  );
  type Values = z.infer<typeof schema>;

  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { deliveryMethod: "CARGO_PICKUP", provider: "PAYME", comment: "", offer: false },
  });
  const { register, handleSubmit, formState, setValue, control } = form;
  const deliveryMethod = useWatch({ control, name: "deliveryMethod" });
  const addressId = useWatch({ control, name: "addressId" });

  // Preselect the default address once addresses load.
  const list = addresses.data;
  useEffect(() => {
    if (!list?.length || addressId) return;
    setValue("addressId", (list.find((a) => a.isDefault) ?? list[0]).id);
  }, [list, addressId, setValue]);

  const onSubmit = handleSubmit(async (values) => {
    let number: string | undefined;
    try {
      const order = unwrap(
        await api.POST("/api/v1/orders", {
          body: {
            deliveryMethod: values.deliveryMethod,
            addressId: values.deliveryMethod === "CARGO_PICKUP" ? undefined : values.addressId,
            // The order records a card method; paying from the balance is chosen at the payment step.
            paymentMethod: values.provider === "WALLET" ? "PAYME" : values.provider,
            comment: values.comment?.trim() || undefined,
          },
        }),
      );
      number = order.number;
    } catch (e) {
      if (isApiError(e, "CART_CHANGED")) {
        toast(t("cartChanged"));
        await qc.invalidateQueries({ queryKey: keys.cart });
        onClose();
      } else {
        toast(errorMessage(e));
      }
      return;
    }

    void qc.invalidateQueries({ queryKey: keys.cart });
    void qc.invalidateQueries({ queryKey: ["orders"] });
    if (!number) return;
    try {
      const payment = unwrap(
        await api.POST("/api/v1/orders/{number}/pay", {
          params: { path: { number } },
          body: { provider: values.provider },
        }),
      );
      if (payment.checkoutUrl) {
        toast(t("redirecting"));
        window.location.assign(payment.checkoutUrl);
        return;
      }
      toast(payment.paid ? t("paid") : t("orderCreated", { number }));
      void qc.invalidateQueries({ queryKey: ["wallet"] });
    } catch (e) {
      // The order exists and waits for payment; it can be paid from its page.
      toast(errorMessage(e));
    }
    onClose();
    router.push(`/account/orders/${encodeURIComponent(number)}`);
  });

  const errors = formState.errors;
  const needsAddress = deliveryMethod !== "CARGO_PICKUP";
  const option =
    "flex cursor-pointer items-center gap-3 rounded-[10px] border border-line px-3 py-2.5 has-[:checked]:border-2 has-[:checked]:border-navy has-[:checked]:px-[11px] has-[:checked]:py-[9px]";

  return (
    <Modal open={open} onClose={onClose} title={t("title")}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4" noValidate>
        <fieldset className="m-0 flex flex-col gap-2 border-0 p-0">
          <legend className="mb-2 font-bold">{t("delivery")}</legend>
          {DELIVERY.map((d) => (
            <label key={d} className={option}>
              <input type="radio" value={d} {...register("deliveryMethod")} className="accent-navy" />
              <b className="font-semibold">{t(`delivery${d}`)}</b>
              <small className="ml-auto text-right text-muted">{t(`delivery${d}Hint`)}</small>
            </label>
          ))}
        </fieldset>

        {needsAddress && (
          <fieldset className="m-0 flex flex-col gap-2 border-0 p-0">
            <legend className="mb-2 font-bold">{t("address")}</legend>
            {addresses.isPending ? (
              <span className="text-muted">{tc("loading")}</span>
            ) : !list?.length ? (
              <span className="text-muted">{t("noAddresses")}</span>
            ) : (
              list.map((a) => (
                <label key={a.id} className={option}>
                  <input type="radio" value={a.id} {...register("addressId")} className="accent-navy" />
                  <span className="min-w-0 text-[14px]">
                    {a.label && <b className="mr-1">{a.label}:</b>}
                    {[a.region, a.city, a.district, a.street, a.house, a.apartment].filter(Boolean).join(", ")}
                  </span>
                </label>
              ))
            )}
            <Link href="/account/addresses" className="link-btn self-start text-navy" onClick={onClose}>
              + {t("addAddress")}
            </Link>
            {errors.addressId && <span className="text-[13px] text-danger">{errors.addressId.message}</span>}
          </fieldset>
        )}

        <fieldset className="m-0 flex flex-col gap-2 border-0 p-0">
          <legend className="mb-2 font-bold">{t("payment")}</legend>
          {PROVIDERS.map((p) => {
            const short = p === "WALLET" && totals?.totalUzs != null && balance < totals.totalUzs;
            return (
              <label key={p} className={`${option} ${short ? "opacity-50" : ""}`}>
                <input type="radio" value={p} {...register("provider")} disabled={short} className="accent-navy" />
                <b className="font-semibold">{t(`provider${p}`)}</b>
                <small className="ml-auto text-right text-muted">
                  {p === "WALLET"
                    ? t("providerWALLETHint", { amount: m("som", { amount: formatAmount(balance) }) })
                    : t(`provider${p}Hint`)}
                </small>
              </label>
            );
          })}
        </fieldset>

        <label className="flex flex-col gap-1.5 text-[14px]">
          <span className="font-bold">
            {t("comment")} <span className="font-normal text-muted">({tc("optional")})</span>
          </span>
          <textarea className="input" rows={2} maxLength={500} {...register("comment")} />
        </label>

        {totals && (
          <div className="flex flex-col gap-1.5 border-t border-dashed border-line pt-3 text-[14px]">
            <Line label={t("lineGoods", { count: totals.quantity ?? 0 })} value={totals.goodsUzs} />
            <Line label={t("lineFee")} value={totals.serviceFeeUzs} />
            <Line label={t("lineShipping")} value={totals.shippingUzs} />
            <div className="flex items-baseline justify-between gap-3 pt-1 font-bold">
              <span className="text-[16px]">{t("toPay")}</span>
              <PriceTag amount={totals.totalUzs} size="lg" />
            </div>
          </div>
        )}

        <label className="flex cursor-pointer items-start gap-2.5 text-[14px]">
          <input type="checkbox" {...register("offer")} className="mt-0.5 h-[18px] w-[18px] flex-none accent-navy" />
          <span>
            {t.rich("offer", {
              link: (chunks) => (
                <Link href={`/pages/${CONTENT_PAGES.offer}`} target="_blank" className="text-navy underline">
                  {chunks}
                </Link>
              ),
            })}
          </span>
        </label>
        {errors.offer && <span className="-mt-2 text-[13px] text-danger">{errors.offer.message}</span>}

        <div className="flex flex-col-reverse gap-2 min-[561px]:flex-row">
          <button type="button" className="btn btn-outline" onClick={onClose}>
            {tc("cancel")}
          </button>
          <button type="submit" className="btn btn-orange flex-1" disabled={formState.isSubmitting}>
            {formState.isSubmitting ? t("submitting") : t("submit")}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function Line({ label, value }: { label: string; value?: number }) {
  const m = useTranslations("money");
  return (
    <div className="flex justify-between gap-3">
      <span className="text-subtle">{label}</span>
      <span className="num">{m("som", { amount: formatAmount(value) })}</span>
    </div>
  );
}
