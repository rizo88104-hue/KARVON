# E-Market — customer website (karvon-web)

@AGENTS.md

## What this is
The customer website of **E-Market** (Task 11 of the Karvon TZ). Customers in Uzbekistan search Taobao products in
Uzbek/Russian, see the final price in UZS, order, pay (Payme, Click, balance) and track orders.
The backend is the Spring Boot API in `..\KARVON-PAY` (http://localhost:8080, base path `/api/v1`,
OpenAPI at `/v3/api-docs`). **Never change the backend.** Read it only when you need API details.

## Brand and design
- The brand name is **"E-Market"** everywhere in the frontend (never "Karvon").
- Main design reference: `docs/design/e-market-design.html`. Follow it exactly: colors, Manrope font, top bar,
  header, search, hero, steps, product cards, buttons, spacing. Its `:root` variables are the Tailwind theme
  (`src/app/globals.css`, `@theme inline`): `bg-navy`, `text-orange`, `bg-grey-bg`, `border-line`, `text-muted`…
- Pages not in that file (product, cart, checkout, account, orders) take their structure and flows from
  `docs/design/karvon-demo.html`, restyled with the E-Market design (navy #232F3E, orange #FF9900, grey #EAEBED).
  We have **no photo-inspection service** ("foto-tekshiruv"): never add it.
- "[MUDDAT]" = delivery days. Always go through `getDeliveryDays(settings)` in `src/lib/site.ts`: it uses
  `deliveryDaysMin`/`deliveryDaysMax` from `GET /api/v1/settings/pricing`; `NEXT_PUBLIC_DELIVERY_DAYS` is only a
  fallback when the settings cannot be loaded.
- Old price: when the backend sends `originalPriceUzs`, show it crossed out (`PriceTag old`) with the orange `-NN%`
  `DiscountBadge` on the image, as in the design's product cards.
- Mobile first: everything must work at 375px wide.
- Base CSS resets live in `@layer base`; unlayered CSS would override component classes like `.btn-navy`.

## Tech stack (do not change without asking)
- Next.js 16 (App Router, TypeScript, Turbopack). `cacheComponents` is **off** on purpose.
  Middleware is called **proxy** in Next 16: `src/proxy.ts`. In matchers write `[.]`, not `\\.`.
- Tailwind CSS v4, TanStack Query, react-hook-form + zod, next-intl (uz default at `/`, ru at `/ru/…`).
- openapi-typescript + openapi-fetch. **Never hand-write DTO types.** Regenerate after backend changes:
  `npm run api` (fetches `/v3/api-docs` into `openapi/openapi.json`, writes `src/api/schema.d.ts`).
  Short aliases live in `src/api/types.ts`.

## Architecture
- `src/app/[locale]/…` pages; `src/app/api/auth/*` route handlers (verify, refresh, logout).
- Browser API calls: `api` from `src/api/client.ts` → same-origin `/api/v1/**`, rewritten to `BACKEND_URL` in
  `next.config.ts`. Server components use `serverApi(locale)` from `src/api/server.ts` (server-only loaders in
  `src/lib/server/data.ts`).
- Auth: the access token is kept **in memory only** (`src/lib/session.ts`). The refresh token is an httpOnly cookie
  (`em_rt`, path `/api/auth`) set by the route handlers; `em_session` is a readable flag. On 401 the client refreshes
  once and retries. Refreshes are single-flight because the backend rotates refresh tokens and treats reuse as theft.
- Errors: backend ProblemDetail `{code, detail}`; show `detail` (already localized via Accept-Language), see
  `useErrorMessage()`.
- Reusable components: ProductCard, PriceTag, PriceBreakdown, SkuSelector, QuantityInput, StatusTimeline,
  CartShopGroup, CheckoutModal, EmptyState, Toast (+ Modal, ProductImage).
- Content pages come from `GET /api/v1/pages/{slug}`; the slugs are in `CONTENT_PAGES` (`faq`, `delivery`,
  `returns`, `public-offer`). Admins must create pages with these slugs.

## Rules
- All texts through next-intl: `src/messages/uz.json` and `src/messages/ru.json` must both be complete and have
  the same keys. No hardcoded strings in components.
- SEO: metadata per page (`generateMetadata`), `sitemap.ts`, `robots.ts`, `[locale]/opengraph-image.tsx`;
  private pages (cart, login, account, search) are `noindex`.
- Work only inside this folder. Do not search or read outside it except `..\KARVON-PAY` for API details.
- Before saying a task is done: `npm run lint` clean and `npm run build` passes.
- Git: branch `main`.
