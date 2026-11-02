import { StaffOrderView } from "@/components/admin/StaffOrderView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("orders");

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <StaffOrderView id={id} />;
}
