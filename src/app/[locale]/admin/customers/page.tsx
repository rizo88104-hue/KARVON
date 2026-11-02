import { CustomersView } from "@/components/admin/CustomersView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("customers");

export default function Page() {
  return <CustomersView />;
}
