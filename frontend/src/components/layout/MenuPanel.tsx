import type { ReactNode } from "react";
import { cva } from "class-variance-authority";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";

const panelVariants = cva("menu-panel", { variants: { variant: {
  default: "menu-panel-default", filter: "menu-panel-filter", lesson: "menu-panel-lesson",
  closed: "menu-panel-closed", table: "menu-panel-table", pass: "menu-panel-pass",
} }, defaultVariants: { variant: "default" } });

export function MenuPanel({ title, action, children, variant = "default" }: {
  title?: string; action?: ReactNode; children: ReactNode;
  variant?: "default" | "filter" | "lesson" | "closed" | "table" | "pass";
}) {
  return <Card size="sm" className={panelVariants({ variant })}>
    {title && <CardHeader className="menu-panel-heading"><CardTitle><h2>{title}</h2></CardTitle>{action}</CardHeader>}
    <CardContent>{children}</CardContent>
  </Card>;
}

export function MenuEmpty({ title, description }: { title: string; description: string }) {
  return <Empty className="menu-empty"><EmptyHeader><EmptyTitle>{title}</EmptyTitle><EmptyDescription>{description}</EmptyDescription></EmptyHeader></Empty>;
}
