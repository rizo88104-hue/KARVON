import { getTranslations } from "next-intl/server";
import { Link } from "@/i18n/navigation";
import { CONTENT_PAGES } from "@/lib/site";

export async function SiteFooter() {
  const t = await getTranslations("footer");
  const year = new Date().getFullYear();
  const col = "flex flex-col gap-1.5";
  const head = "mb-1 font-bold text-ink";
  const link = "hover:text-navy hover:underline";

  return (
    <footer className="border-t border-line bg-white text-[14px] text-subtle">
      <div className="container-site grid grid-cols-2 gap-6 py-8 min-[901px]:grid-cols-4">
        <div className={col}>
          <span className="text-[22px] font-bold tracking-[-0.02em] text-navy">E-Market</span>
        </div>
        <nav className={col} aria-label={t("buyers")}>
          <b className={head}>{t("buyers")}</b>
          <Link className={link} href={`/pages/${CONTENT_PAGES.faq}`}>
            {t("faq")}
          </Link>
          <Link className={link} href={`/pages/${CONTENT_PAGES.delivery}`}>
            {t("delivery")}
          </Link>
          <Link className={link} href={`/pages/${CONTENT_PAGES.returns}`}>
            {t("returns")}
          </Link>
          <Link className={link} href={`/pages/${CONTENT_PAGES.offer}`}>
            {t("offer")}
          </Link>
        </nav>
        <nav className={col} aria-label={t("account")}>
          <b className={head}>{t("account")}</b>
          <Link className={link} href="/account/orders">
            {t("orders")}
          </Link>
          <Link className={link} href="/account/wallet">
            {t("wallet")}
          </Link>
          <Link className={link} href="/account/settings">
            {t("settings")}
          </Link>
        </nav>
        <div className={col}>
          <b className={head}>{t("payment")}</b>
          <span>{t("paymentMethods")}</span>
        </div>
      </div>
      <div className="border-t border-line">
        <p className="container-site m-0 py-4 text-[13px] text-muted">{t("copyright", { year })}</p>
      </div>
    </footer>
  );
}
