import { getScreenLabel, type UserRole } from "@/config/access";
import { roleLabels } from "@/features/dashboard/model";
import type { Screen } from "../../app/routes";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/Breadcrumb";

export function PageBreadcrumb({ screen, role = "MEMBER" }: { screen: Screen; role?: UserRole }) {
  return (
    <Breadcrumb aria-label="현재 위치">
      <BreadcrumbList>
        <BreadcrumbItem>{roleLabels[role]}</BreadcrumbItem>
        <BreadcrumbSeparator>/</BreadcrumbSeparator>
        <BreadcrumbItem>
          <BreadcrumbPage>{getScreenLabel(screen, role)}</BreadcrumbPage>
        </BreadcrumbItem>
      </BreadcrumbList>
    </Breadcrumb>
  );
}
