import { NextResponse } from "next/server";

// Liveness of the Next.js server for the Docker healthcheck and the deploy check (no backend call).
export const dynamic = "force-dynamic";

export function GET() {
  return NextResponse.json({ status: "UP" });
}
