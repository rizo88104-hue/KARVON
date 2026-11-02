import { ImageResponse } from "next/og";
import { getTranslations } from "next-intl/server";
import { asLocale } from "@/i18n/routing";

export const alt = "E-Market";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

/** Site-wide Open Graph image in the E-Market colors (navy, orange). */
export default async function OpenGraphImage({ params }: { params: Promise<{ locale: string }> }) {
  const t = await getTranslations({ locale: asLocale((await params).locale), namespace: "home" });
  const tb = await getTranslations({ locale: asLocale((await params).locale), namespace: "topbar" });
  const title = t("heroTitle").replace(/<br><\/br>/g, " ");
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          background: "#232F3E",
          color: "#FFFFFF",
          padding: "72px 80px",
        }}
      >
        <div style={{ display: "flex", fontSize: 56, fontWeight: 700, letterSpacing: "-0.02em" }}>E-Market</div>
        <div style={{ display: "flex", flexDirection: "column", gap: 28 }}>
          <div style={{ display: "flex", fontSize: 64, lineHeight: 1.1, fontWeight: 800, maxWidth: 980 }}>{title}</div>
          <div style={{ display: "flex", fontSize: 30, opacity: 0.85 }}>{tb("tagline")}</div>
        </div>
        <div style={{ display: "flex", width: 220, height: 14, borderRadius: 7, background: "#FF9900" }} />
      </div>
    ),
    size,
  );
}
