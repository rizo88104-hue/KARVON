import { DashboardView } from "@/components/admin/DashboardView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("dashboard");

export default function Page() {
  return <DashboardView />;
}
