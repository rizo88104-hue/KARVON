// Site-wide constants.

export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000").replace(/\/$/, "");

/**
 * Delivery time shown instead of "[MUDDAT]" (e.g. "12–18"), from deliveryDaysMin/deliveryDaysMax of
 * GET /api/v1/settings/pricing. NEXT_PUBLIC_DELIVERY_DAYS is only a fallback for when the settings cannot be loaded.
 * Every caller goes through this function.
 */
export function getDeliveryDays(
  settings?: { deliveryDaysMin?: number; deliveryDaysMax?: number } | null,
): string | null {
  const min = settings?.deliveryDaysMin;
  const max = settings?.deliveryDaysMax;
  if (min && max) return min === max ? String(min) : `${min}–${max}`;
  const fallback = process.env.NEXT_PUBLIC_DELIVERY_DAYS?.trim();
  return fallback ? fallback : null;
}

/** Discount in whole percent when there is a higher old price, e.g. 25 for 149 000 instead of 199 000. */
export function discountPercent(price?: number | null, original?: number | null): number | null {
  if (!price || !original || original <= price) return null;
  const percent = Math.round((1 - price / original) * 100);
  return percent > 0 ? percent : null;
}

/** Content pages served by GET /api/v1/pages/{slug}. Admins must create pages with these slugs. */
export const CONTENT_PAGES = {
  faq: "faq",
  delivery: "delivery",
  returns: "returns",
  offer: "public-offer",
} as const;

/** True when the search text is a Taobao/Tmall link or share text, which goes to resolve-link. */
export function looksLikeLink(text: string): boolean {
  return /(https?:\/\/|taobao\.com|tmall\.com|tb\.cn|m\.tb\.cn)/i.test(text);
}

/** Uzbek phone: +998 and 9 digits. Returns "+998901234567" or null. */
export function normalizePhone(input: string): string | null {
  const digits = input.replace(/\D/g, "");
  const local = digits.startsWith("998") ? digits.slice(3) : digits;
  return local.length === 9 ? `+998${local}` : null;
}
