// Number and date formatting shared by server and client components.
const grouping = new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 0 });
const decimal = new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 2 });

/** 149000 -> "149 000" (plain spaces, as in the design). */
export function formatAmount(value: number | null | undefined): string {
  return grouping.format(value ?? 0).replace(/[  ]/g, " ");
}

/** 0.9 -> "0,9"; 129.5 -> "129,5". */
export function formatDecimal(value: number | null | undefined): string {
  return decimal.format(value ?? 0).replace(/[  ]/g, " ");
}

function intlLocale(locale: string) {
  return locale === "ru" ? "ru-RU" : "uz-Latn-UZ";
}

export function formatDate(value: string | null | undefined, locale: string): string {
  if (!value) return "";
  return new Intl.DateTimeFormat(intlLocale(locale), { day: "2-digit", month: "2-digit", year: "numeric" }).format(
    new Date(value),
  );
}

export function formatDateTime(value: string | null | undefined, locale: string): string {
  if (!value) return "";
  return new Intl.DateTimeFormat(intlLocale(locale), {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}
