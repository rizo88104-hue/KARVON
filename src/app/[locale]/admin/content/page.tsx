import { ContentView } from "@/components/admin/ContentView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("content");

export default function Page() {
  return <ContentView />;
}
