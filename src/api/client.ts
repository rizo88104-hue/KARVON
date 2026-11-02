"use client";

// Typed API client for the browser. Requests go to same-origin /api/v1/** (rewritten to the backend by
// next.config.ts). Adds the in-memory access token and, on 401, refreshes the session once and retries.
import createClient from "openapi-fetch";
import { getAccessToken, refreshSession } from "@/lib/session";
import type { paths } from "./schema";

let currentLocale = "uz";

/** Backend texts (translations, error messages) follow Accept-Language. */
export function setApiLocale(locale: string) {
  currentLocale = locale;
}

async function authFetch(input: Request): Promise<Response> {
  const retry = input.clone();
  const send = (request: Request) => {
    const token = getAccessToken();
    request.headers.set("Accept-Language", currentLocale);
    if (token) request.headers.set("Authorization", `Bearer ${token}`);
    return fetch(request);
  };

  const res = await send(input);
  if (res.status !== 401 || new URL(input.url).pathname.startsWith("/api/v1/auth/")) return res;
  // The access token is missing or expired (15 min): refresh with the httpOnly cookie, then retry once.
  const refreshed = await refreshSession();
  return refreshed ? send(retry) : res;
}

export const api = createClient<paths>({
  baseUrl: "",
  fetch: authFetch,
});
