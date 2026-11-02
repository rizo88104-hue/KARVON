import { useTranslations } from "next-intl";
import { EmptyState } from "@/components/EmptyState";
import { Link } from "@/i18n/navigation";

export default function AdminNotFound() {
  const t = useTranslations("errors");
  const ta = useTranslations("admin");
  return (
    <EmptyState
      title={t("notFoundTitle")}
      text={t("notFoundText")}
      action={
        <Link href="/admin" className="btn btn-orange">
          {ta("title")}
        </Link>
      }
    />
  );
}
