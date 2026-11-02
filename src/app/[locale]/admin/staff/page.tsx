import { StaffView } from "@/components/admin/StaffView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("staff");

export default function Page() {
  return <StaffView />;
}
