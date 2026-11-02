import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

// Built per request: the site URL comes from the runtime environment (SITE_URL), not from the build.
export const dynamic = "force-dynamic";

export default function robots(): MetadataRoute.Robots {
  const privatePaths = ["/cart", "/login", "/account", "/search", "/admin"];
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/", ...privatePaths, ...privatePaths.map((p) => `/ru${p}`)],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
