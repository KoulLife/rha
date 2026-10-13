export type { Screen } from "../config/access";

export type DialogState = {
  title: string;
  body: string;
  action?: string;
  destructive?: boolean;
} | null;

