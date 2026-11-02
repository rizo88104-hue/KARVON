import { NotificationsView } from "@/components/admin/NotificationsView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("notifications");

export default function Page() {
  return <NotificationsView />;
}
