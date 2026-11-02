import { NextResponse, type NextRequest } from "next/server";
import { serverApi } from "@/api/server";
import { setAuthCookies } from "@/lib/server/auth-cookie";

/** Phone + SMS code -> session. Answers { accessToken, user }; the refresh token goes into the httpOnly cookie. */
export async function POST(request: NextRequest) {
  const body = (await request.json().catch(() => null)) as { phone?: string; code?: string } | null;
  const lang = request.headers.get("accept-language") ?? "uz";
  const result = await serverApi(lang)
    .POST("/api/v1/auth/otp/verify", { body: { phone: body?.phone ?? "", code: body?.code ?? "" } })
    .catch(() => null);
  if (!result) return NextResponse.json({ code: "BACKEND_UNAVAILABLE" }, { status: 503 });
  const { data, error, response } = result;
  if (!data?.refreshToken) {
    return NextResponse.json(error ?? { code: "AUTH_FAILED" }, { status: response.status || 500 });
  }
  const res = NextResponse.json({ accessToken: data.accessToken, user: data.user });
  setAuthCookies(res, data.refreshToken);
  return res;
}
