import "server-only";
import type { NextResponse } from "next/server";

/** httpOnly cookie with the backend refresh token. JavaScript in the page never sees it. */
export const REFRESH_COOKIE = "em_rt";
/** Readable flag cookie: tells the page a session may exist, so guests skip the refresh call. */
export const SESSION_FLAG_COOKIE = "em_session";

const MAX_AGE = 60 * 60 * 24 * 30; // backend refresh-token-ttl: 30d

export function setAuthCookies(res: NextResponse, refreshToken: string) {
  const secure = process.env.NODE_ENV === "production";
  res.cookies.set(REFRESH_COOKIE, refreshToken, {
    httpOnly: true,
    secure,
    sameSite: "lax",
    path: "/api/auth",
    maxAge: MAX_AGE,
  });
  res.cookies.set(SESSION_FLAG_COOKIE, "1", { secure, sameSite: "lax", path: "/", maxAge: MAX_AGE });
}

export function clearAuthCookies(res: NextResponse) {
  res.cookies.set(REFRESH_COOKIE, "", { path: "/api/auth", maxAge: 0 });
  res.cookies.set(SESSION_FLAG_COOKIE, "", { path: "/", maxAge: 0 });
}
