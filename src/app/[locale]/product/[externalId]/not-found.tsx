import { useTranslations } from "next-intl";
import { NotFoundContent } from "@/components/NotFoundContent";

export default function ProductNotFound() {
  const t = useTranslations("product");
  return <NotFoundContent title={t("notFoundTitle")} text={t("notFoundText")} />;
}
