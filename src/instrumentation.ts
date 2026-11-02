import * as Sentry from "@sentry/nextjs";

export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    await import("./sentry.server.config");
  }
}

// Errors thrown while rendering server components or in route handlers.
export const onRequestError = Sentry.captureRequestError;
