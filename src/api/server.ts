import "server-only";

// Typed API client for Server Components and route handlers: talks to the backend directly.
import createClient from "openapi-fetch";
import type { paths } from "./schema";

export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export function serverApi(locale: string) {
  return createClient<paths>({
    baseUrl: BACKEND_URL,
    headers: { "Accept-Language": locale },
    cache: "no-store",
  });
}
