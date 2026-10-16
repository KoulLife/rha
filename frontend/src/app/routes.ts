export type { Screen } from "../config/access";

export type DialogState = {
  title: string;
  body: string;
  action?: string;
  destructive?: boolean;
  actionVariant?: "default" | "ghost" | "warning" | "destructive";
  details?: { label: string; value: string }[];
  notice?: string;
} | null;
