import "server-only";

import { cache } from "react";
import { serverApi } from "@/api/server";
import type { Banner, Category, ContentPage, PricingSettings, ProductCard, ProductDetail, TickerItem } from "@/api/types";

// Server-side reads used by pages and metadata. React `cache` dedupes calls within one request
// (e.g. generateMetadata and the page both load the product). A backend outage never crashes the layout.

export const getCategories = cache(async (locale: string): Promise<Category[]> => {
  try {
    const { data } = await serverApi(locale).GET("/api/v1/catalog/categories");
    return data ?? [];
  } catch {
    return [];
  }
});

export const getPricingSettings = cache(async (): Promise<PricingSettings | null> => {
  try {
    const { data } = await serverApi("uz").GET("/api/v1/settings/pricing");
    return data ?? null;
  } catch {
    return null;
  }
});

/** null when the product does not exist (404); throws on other failures so the error page shows. */
export const getProduct = cache(async (externalId: string, locale: string): Promise<ProductDetail | null> => {
  const { data, response } = await serverApi(locale).GET("/api/v1/catalog/products/{externalId}", {
    params: { path: { externalId } },
  });
  if (response.status === 404 || response.status === 400) return null;
  if (!data) throw new Error(`Product ${externalId}: HTTP ${response.status}`);
  return data;
});

export const getBanners = cache(async (locale: string): Promise<Banner[]> => {
  try {
    const { data } = await serverApi(locale).GET("/api/v1/banners");
    return data ?? [];
  } catch {
    return [];
  }
});

/** null when there is no page with this slug. */
export const getContentPage = cache(async (slug: string, locale: string): Promise<ContentPage | null> => {
  const { data, response } = await serverApi(locale).GET("/api/v1/pages/{slug}", { params: { path: { slug } } });
  if (response.status >= 400 && response.status < 500) return null;
  if (!data) throw new Error(`Page ${slug}: HTTP ${response.status}`);
  return data;
});

/**
 * Best-selling products of a category for its home page block, searched by its Chinese keyword.
 * Empty when the category has none or the backend is unreachable (the block is then left out).
 */
export const getCategoryProducts = cache(async (locale: string, keywordZh: string): Promise<ProductCard[]> => {
  try {
    const { data } = await serverApi(locale).GET("/api/v1/catalog/search", {
      params: { query: { q: keywordZh, sort: "SALES", page: 0 } },
    });
    return data?.content ?? [];
  } catch {
    return [];
  }
});

/** Messages of the running line in the top bar, in the page language; empty when there are none or on failure. */
export const getTicker = cache(async (locale: string): Promise<TickerItem[]> => {
  try {
    const { data } = await serverApi(locale).GET("/api/v1/ticker");
    return data ?? [];
  } catch {
    return [];
  }
});
