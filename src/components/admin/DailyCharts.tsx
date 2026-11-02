"use client";

import { useLocale, useTranslations } from "next-intl";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { Dashboard } from "@/api/types";
import { formatAmount } from "@/lib/format";

const NAVY = "#232F3E";
const GRID = "#E3E5E8";
const AXIS = "#6B7280";

type Point = NonNullable<Dashboard["dailySeries"]>[number];

/**
 * Orders per day and revenue per day as two small charts on their own axes (never one dual-axis chart).
 * One series each, so the title names it and there is no legend. A visually hidden table carries the same data.
 */
export function DailyCharts({ points }: { points: Point[] }) {
  const t = useTranslations("admin.dashboard");
  const m = useTranslations("money");
  const locale = useLocale();
  const day = (iso?: string) =>
    iso
      ? new Intl.DateTimeFormat(locale === "ru" ? "ru-RU" : "uz-Latn-UZ", { day: "2-digit", month: "2-digit" }).format(
          new Date(`${iso}T00:00:00`),
        )
      : "";
  const data = points.map((p) => ({ date: day(p.date), orders: p.orders ?? 0, revenue: p.revenue ?? 0 }));
  const compact = (v: number) =>
    new Intl.NumberFormat(locale === "ru" ? "ru-RU" : "uz-Latn-UZ", { notation: "compact" }).format(v);

  return (
    <div className="grid grid-cols-1 gap-4 min-[1101px]:grid-cols-2">
      <MiniChart title={t("chartOrders")} data={data} dataKey="orders" tickFormat={(v) => String(v)} valueLabel={(v) => String(v)} />
      <MiniChart
        title={t("chartRevenue")}
        data={data}
        dataKey="revenue"
        tickFormat={compact}
        valueLabel={(v) => m("som", { amount: formatAmount(v) })}
      />
      <table className="sr-only">
        <caption>{t("chartTable")}</caption>
        <thead>
          <tr>
            <th>{t("date")}</th>
            <th>{t("chartOrders")}</th>
            <th>{t("chartRevenue")}</th>
          </tr>
        </thead>
        <tbody>
          {data.map((d) => (
            <tr key={d.date}>
              <td>{d.date}</td>
              <td>{d.orders}</td>
              <td>{m("som", { amount: formatAmount(d.revenue) })}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function MiniChart({
  title,
  data,
  dataKey,
  tickFormat,
  valueLabel,
}: {
  title: string;
  data: { date: string; orders: number; revenue: number }[];
  dataKey: "orders" | "revenue";
  tickFormat: (v: number) => string;
  valueLabel: (v: number) => string;
}) {
  return (
    <div className="card p-4" aria-hidden="true">
      <h3 className="mt-0 mb-3 text-[14px] font-bold">{title}</h3>
      <div className="h-[220px]">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 0 }} barCategoryGap={2}>
            <CartesianGrid vertical={false} stroke={GRID} strokeDasharray="0" />
            <XAxis dataKey="date" tick={{ fontSize: 11, fill: AXIS }} tickLine={false} axisLine={{ stroke: GRID }} minTickGap={16} />
            <YAxis
              tick={{ fontSize: 11, fill: AXIS }}
              tickLine={false}
              axisLine={false}
              width={44}
              allowDecimals={false}
              tickFormatter={tickFormat}
            />
            <Tooltip
              cursor={{ fill: "rgba(35,47,62,0.06)" }}
              formatter={(v) => [valueLabel(Number(v)), title]}
              contentStyle={{ borderRadius: 8, border: `1px solid ${GRID}`, fontSize: 12 }}
            />
            <Bar dataKey={dataKey} fill={NAVY} radius={[4, 4, 0, 0]} maxBarSize={18} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
