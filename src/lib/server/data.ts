import "server-only";

import { cache } from "react";
import { serverApi } from "@/api/server";
import type { Banner, Category, ContentPage, PricingSettings, ProductCard, ProductDetail } from "@/api/types";

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

/**
 * Popular products for the home page. The catalog search needs a query, so this searches category names
 * (sorted by sales) and uses the first one with results. `products` is null when the backend is unreachable.
 */
export const getPopularProducts = cache(
  async (locale: string, queries: string[]): Promise<{ query: string | null; products: ProductCard[] | null }> => {
    try {
      for (const q of queries.slice(0, 4)) {
        const { data } = await serverApi(locale).GET("/api/v1/catalog/search", {
          params: { query: { q, sort: "SALES", page: 0 } },
        });
        if (data?.content?.length) return { query: q, products: data.content };
      }
      return { query: null, products: [] };
    } catch {
      return { query: null, products: null };
    }
  },
);

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
