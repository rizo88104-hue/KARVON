import createMiddleware from "next-intl/middleware";
import { routing } from "./i18n/routing";

export default createMiddleware(routing);

export const config = {
  // Everything except API routes, Next internals and files with an extension (robots.txt, sitemap.xml, images…).
  // Use "[.]", not "\\.": Next 16 un-escapes "\\." in matchers so it would match any character.
  matcher: ["/((?!api|_next|_vercel|.*[.].*).*)"],
};
