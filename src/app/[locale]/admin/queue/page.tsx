import { QueueView } from "@/components/admin/QueueView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("queue");

export default function Page() {
  return <QueueView />;
}
