import { OrdersView } from "@/components/admin/OrdersView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("orders");

export default function Page() {
  return <OrdersView />;
}
