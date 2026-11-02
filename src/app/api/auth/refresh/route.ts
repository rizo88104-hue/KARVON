import { NextResponse, type NextRequest } from "next/server";
import { serverApi } from "@/api/server";
import { clearAuthCookies, REFRESH_COOKIE, setAuthCookies } from "@/lib/server/auth-cookie";

/** Rotates the refresh cookie and answers a new { accessToken, user }. 401 when there is no valid session. */
export async function POST(request: NextRequest) {
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  if (!refreshToken) {
    const res = NextResponse.json({ code: "NO_SESSION" }, { status: 401 });
    clearAuthCookies(res);
    return res;
  }
  const result = await serverApi("uz")
    .POST("/api/v1/auth/refresh", { body: { refreshToken } })
    .catch(() => null);
  const data = result?.data;
  if (!data?.refreshToken) {
    const status = !result || result.response.status >= 500 ? 503 : 401;
    const res = NextResponse.json({ code: "SESSION_EXPIRED" }, { status });
    // Keep the cookie when the backend is just unavailable; drop it when the token was rejected.
    if (status === 401) clearAuthCookies(res);
    return res;
  }
  const res = NextResponse.json({ accessToken: data.accessToken, user: data.user });
  setAuthCookies(res, data.refreshToken);
  return res;
}
