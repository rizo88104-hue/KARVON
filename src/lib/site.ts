// Site-wide constants.

export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000").replace(/\/$/, "");

/**
 * Delivery time shown instead of "[MUDDAT]" (e.g. "12–18").
 * TODO(backend): the API has no public delivery-days setting yet. When it does, read it here from
 * GET /api/v1/settings/… and drop NEXT_PUBLIC_DELIVERY_DAYS. Every caller goes through this function.
 */
export function getDeliveryDays(): string | null {
  const value = process.env.NEXT_PUBLIC_DELIVERY_DAYS?.trim();
  return value ? value : null;
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
