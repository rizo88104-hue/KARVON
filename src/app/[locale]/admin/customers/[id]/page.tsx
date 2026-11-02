import { CustomerView } from "@/components/admin/CustomersView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("customers");

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <CustomerView id={id} />;
}
