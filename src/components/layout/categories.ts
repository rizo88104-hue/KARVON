import type { Category } from "@/api/types";

export function categoryName(c: Category, locale: string) {
  return (locale === "ru" ? c.nameRu : c.nameUz) ?? c.nameUz ?? c.slug ?? "";
}

/** A category opens a catalog search by its name in the current language. */
export function categoryHref(c: Category, locale: string) {
  return `/search?q=${encodeURIComponent(categoryName(c, locale))}`;
}
