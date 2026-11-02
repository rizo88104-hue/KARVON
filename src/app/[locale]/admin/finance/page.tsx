import { FinanceView } from "@/components/admin/FinanceView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("finance");

export default function Page() {
  return <FinanceView />;
}
