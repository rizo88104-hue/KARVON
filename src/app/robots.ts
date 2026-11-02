import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

export default function robots(): MetadataRoute.Robots {
  const privatePaths = ["/cart", "/login", "/account", "/search"];
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/", ...privatePaths, ...privatePaths.map((p) => `/ru${p}`)],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
