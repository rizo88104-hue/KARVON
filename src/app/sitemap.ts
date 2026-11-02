import type { MetadataRoute } from "next";
import { CONTENT_PAGES, SITE_URL } from "@/lib/site";

// Built per request: the site URL comes from the runtime environment (SITE_URL), not from the build.
export const dynamic = "force-dynamic";

// Public pages in both languages (uz at "/", ru at "/ru"). Product pages are reached through the catalog.
export default function sitemap(): MetadataRoute.Sitemap {
  const paths = ["", ...Object.values(CONTENT_PAGES).map((slug) => `/pages/${slug}`)];
  return paths.map((path) => ({
    url: `${SITE_URL}${path || "/"}`,
    changeFrequency: path ? "monthly" : "daily",
    priority: path ? 0.5 : 1,
    alternates: { languages: { uz: `${SITE_URL}${path || "/"}`, ru: `${SITE_URL}/ru${path}` } },
  }));
}
