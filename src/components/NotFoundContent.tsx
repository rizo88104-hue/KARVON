import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { EmptyState } from "./EmptyState";

export function NotFoundContent({ title, text }: { title?: string; text?: string }) {
  const t = useTranslations("errors");
  return (
    <main className="container-site pt-10 pb-16">
      <EmptyState
        title={title ?? t("notFoundTitle")}
        text={text ?? t("notFoundText")}
        action={
          <Link href="/" className="btn btn-orange">
            {t("goHome")}
          </Link>
        }
      />
    </main>
  );
}
