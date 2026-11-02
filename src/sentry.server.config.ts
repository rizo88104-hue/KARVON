// Sentry for the Node.js server (SSR, route handlers). Off unless SENTRY_DSN is set at runtime.
import * as Sentry from "@sentry/nextjs";
import { SENTRY_DATA_COLLECTION } from "@/lib/sentry-privacy";

Sentry.init({
  dsn: process.env.SENTRY_DSN,
  enabled: Boolean(process.env.SENTRY_DSN),
  environment: process.env.SENTRY_ENVIRONMENT ?? "production",
  tracesSampleRate: 0,
  dataCollection: SENTRY_DATA_COLLECTION,
});
