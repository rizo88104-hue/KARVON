import { NextResponse, type NextRequest } from "next/server";
import { serverApi } from "@/api/server";
import { clearAuthCookies, REFRESH_COOKIE } from "@/lib/server/auth-cookie";

export async function POST(request: NextRequest) {
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  if (refreshToken) {
    await serverApi("uz").POST("/api/v1/auth/logout", { body: { refreshToken } }).catch(() => undefined);
  }
  const res = new NextResponse(null, { status: 204 });
  clearAuthCookies(res);
  return res;
}
