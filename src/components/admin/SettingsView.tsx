"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { StaffPricingSettings } from "@/api/types";
import { EmptyState } from "@/components/EmptyState";
import { useAction } from "@/lib/admin/use-action";
import { formatDate, formatDateTime, formatDecimal } from "@/lib/format";
import { useErrorMessage } from "@/lib/use-error-message";
import { AdminPageHeader, Loading, Pager, Section, TableWrap, useMoney } from "./ui";

const FIELDS = [
  "customerRate",
  "feePercent",
  "minServiceFee",
  "shippingCostPerKg",
  "ourMarkupPerKg",
  "minWeightKg",
  "defaultWeightKg",
  "priceChangeThresholdPercent",
  "deliveryDaysMin",
  "deliveryDaysMax",
] as const;
type Field = (typeof FIELDS)[number];
type Values = Record<Field, string>;

/** UZS amounts are rounded to 100 so'm, half up (backend rule). */
const round100 = (v: number) => Math.round(v / 100) * 100;

export function SettingsView() {
  const ta = useTranslations("admin");
  const errorMessage = useErrorMessage();
  const current = useQuery({
    queryKey: ["admin", "settings", "pricing"],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/settings/pricing")),
  });
  return (
    <>
      <AdminPageHeader title={ta("nav.settings")} />
      {current.isPending ? (
        <Loading />
      ) : current.isError ? (
        <EmptyState title={ta("loadError")} text={errorMessage(current.error)} />
      ) : (
        <div className="grid grid-cols-1 items-start gap-4 min-[1101px]:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
          <SettingsForm key={current.data.id} settings={current.data} />
          <div className="flex min-w-0 flex-col gap-4">
            <CbuCard />
          </div>
          <div className="min-[1101px]:col-span-2">
            <HistoryTable />
          </div>
        </div>
      )}
    </>
  );
}

function SettingsForm({ settings }: { settings: StaffPricingSettings }) {
  const t = useTranslations("admin.settings");
  const money = useMoney();
  const schema = useMemo(() => {
    const num = z.string().trim().refine((v) => v !== "" && Number.isFinite(Number(v.replace(",", "."))) && Number(v.replace(",", ".")) >= 0, {
      message: t("invalidNumber"),
    });
    const days = z.string().trim().refine((v) => /^\d+$/.test(v) && Number(v) >= 1 && Number(v) <= 365, { message: t("invalidDays") });
    return z
      .object({
        customerRate: num,
        feePercent: num.refine((v) => Number(v.replace(",", ".")) <= 100, { message: t("invalidPercent") }),
        minServiceFee: num,
        shippingCostPerKg: num,
        ourMarkupPerKg: num,
        minWeightKg: num,
        defaultWeightKg: num.refine((v) => Number(v.replace(",", ".")) > 0, { message: t("invalidNumber") }),
        priceChangeThresholdPercent: num,
        deliveryDaysMin: days,
        deliveryDaysMax: days,
      })
      .refine((v) => Number(v.deliveryDaysMax) >= Number(v.deliveryDaysMin), {
        path: ["deliveryDaysMax"],
        message: t("invalidDaysRange"),
      });
  }, [t]);

  const defaults = Object.fromEntries(FIELDS.map((f) => [f, String(settings[f] ?? "")])) as Values;
  const { register, handleSubmit, formState, control, reset } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: defaults,
  });
  const values = useWatch({ control }) as Partial<Values>;
  const n = (v?: string) => Number((v ?? "").replace(",", ".")) || 0;

  const save = useAction(
    async (v: Values) =>
      unwrap(
        await api.PUT("/api/v1/admin/settings/pricing", {
          body: {
            customerRate: n(v.customerRate),
            feePercent: n(v.feePercent),
            minServiceFee: n(v.minServiceFee),
            shippingCostPerKg: n(v.shippingCostPerKg),
            ourMarkupPerKg: n(v.ourMarkupPerKg),
            minWeightKg: n(v.minWeightKg),
            defaultWeightKg: n(v.defaultWeightKg),
            priceChangeThresholdPercent: n(v.priceChangeThresholdPercent),
            deliveryDaysMin: Math.round(n(v.deliveryDaysMin)),
            deliveryDaysMax: Math.round(n(v.deliveryDaysMax)),
          },
        }),
      ),
    {
      success: t("saved"),
      invalidate: [["admin", "settings"]],
      onSuccess: (saved) => reset(Object.fromEntries(FIELDS.map((f) => [f, String(saved[f] ?? "")])) as Values),
    },
  );

  // Live preview of one sample product with the values in the form (same formula as the backend).
  const [sampleCny, setSampleCny] = useState("129");
  const [sampleKg, setSampleKg] = useState("0.9");
  const goods = round100(n(sampleCny) * n(values.customerRate));
  const fee = round100(Math.max(goods * (n(values.feePercent) / 100), n(values.minServiceFee)));
  const shipping = round100(Math.max(n(values.minWeightKg), n(sampleKg) || n(values.defaultWeightKg)) * (n(values.shippingCostPerKg) + n(values.ourMarkupPerKg)));

  return (
    <>
      <Section title={t("formTitle")}>
        <form className="flex flex-col gap-4 p-4" onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
          <div className="grid grid-cols-1 gap-3 min-[561px]:grid-cols-2">
            {FIELDS.map((f) => (
              <label key={f} className="label">
                <span>
                  {t(`field.${f}`)} <span className="hint">{t(`unit.${f}`)}</span>
                </span>
                <input className="input num h-9" inputMode="decimal" aria-invalid={!!formState.errors[f]} {...register(f)} />
                {formState.errors[f] && <span className="text-[12px] font-normal text-danger">{formState.errors[f]?.message}</span>}
              </label>
            ))}
          </div>
          <p className="m-0 text-[12px] text-muted">{t("versionHint")}</p>
          <button type="submit" className="btn btn-orange self-start" disabled={save.isPending || !formState.isDirty}>
            {t("save")}
          </button>
        </form>
      </Section>

      <Section title={t("preview")}>
        <div className="flex flex-col gap-3 p-4 text-[13px]">
          <div className="grid grid-cols-2 gap-3">
            <label className="label">
              {t("sampleCny")}
              <input className="input num h-9" inputMode="decimal" value={sampleCny} onChange={(e) => setSampleCny(e.target.value)} />
            </label>
            <label className="label">
              {t("sampleKg")}
              <input className="input num h-9" inputMode="decimal" value={sampleKg} onChange={(e) => setSampleKg(e.target.value)} />
            </label>
          </div>
          <dl className="m-0">
            {[
              [t("previewGoods"), money(goods)],
              [t("previewFee"), money(fee)],
              [t("previewShipping"), money(shipping)],
            ].map(([label, value]) => (
              <div key={label} className="flex justify-between border-b border-dashed border-line py-1.5">
                <dt className="text-subtle">{label}</dt>
                <dd className="num m-0">{value}</dd>
              </div>
            ))}
            <div className="flex justify-between pt-2 text-[15px] font-bold">
              <dt>{t("previewTotal")}</dt>
              <dd className="num m-0">{money(goods + fee + shipping)}</dd>
            </div>
          </dl>
        </div>
      </Section>
    </>
  );
}

function CbuCard() {
  const t = useTranslations("admin.settings");
  const locale = useLocale();
  const errorMessage = useErrorMessage();
  const cbu = useQuery({
    queryKey: ["admin", "settings", "cbu"],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/settings/cbu-rate")),
    retry: false,
  });
  return (
    <Section title={t("cbuTitle")}>
      <div className="p-4 text-[13px]">
        {cbu.isPending ? (
          <Loading />
        ) : cbu.isError ? (
          <p className="m-0 text-danger">{errorMessage(cbu.error)}</p>
        ) : (
          <dl className="m-0 grid grid-cols-2 gap-3">
            <div>
              <dt className="text-muted">{t("cbuRate", { date: formatDate(cbu.data.date, locale) })}</dt>
              <dd className="num m-0 text-[18px] font-bold">{formatDecimal(cbu.data.cbuRate)}</dd>
            </div>
            <div>
              <dt className="text-muted">{t("ourRate")}</dt>
              <dd className="num m-0 text-[18px] font-bold">{formatDecimal(cbu.data.customerRate)}</dd>
            </div>
            <div className="col-span-2">
              <dt className="text-muted">{t("difference")}</dt>
              <dd className="num m-0 font-semibold">{formatDecimal(cbu.data.differencePercent)}%</dd>
            </div>
          </dl>
        )}
      </div>
    </Section>
  );
}

function HistoryTable() {
  const t = useTranslations("admin.settings");
  const locale = useLocale();
  const [page, setPage] = useState(0);
  const history = useQuery({
    queryKey: ["admin", "settings", "history", page],
    queryFn: async () =>
      unwrap(await api.GET("/api/v1/admin/settings/pricing/history", { params: { query: { page, size: 10 } } })),
  });
  return (
    <Section title={t("history")}>
      {history.isPending ? (
        <Loading />
      ) : (
        <TableWrap>
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t("effectiveFrom")}</th>
                {FIELDS.map((f) => (
                  <th key={f} className="num">
                    {t(`short.${f}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(history.data?.content ?? []).map((row) => (
                <tr key={row.id}>
                  <td className="num">{formatDateTime(row.effectiveFrom, locale)}</td>
                  {FIELDS.map((f) => (
                    <td key={f} className="num">
                      {formatDecimal(row[f])}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>
      )}
      <Pager page={page} totalPages={history.data?.totalPages ?? 0} onChange={setPage} />
    </Section>
  );
}
