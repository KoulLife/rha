import type { ReactNode } from "react";
import { ApiError } from "@/api/client";

export const money = (n: number) => `${n.toLocaleString("ko-KR")}원`;
export const dateTime = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat("ko-KR", {
        timeZone: "Asia/Seoul",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      }).format(new Date(value))
    : "—";
export const toLocalInput = (value: string) =>
  new Date(Date.parse(value) + 9 * 3600000).toISOString().slice(0, 16);
export const toUTC = (value: string) => new Date(`${value}:00+09:00`).toISOString();
export const dayStart = (date: string) =>
  date ? new Date(`${date}T00:00:00+09:00`).toISOString() : undefined;
export const dayEnd = (date: string) =>
  date ? new Date(Date.parse(`${date}T00:00:00+09:00`) + 86400000).toISOString() : undefined;
export type Option = { value: string; label: string };
export const options = (rows: { id: number; name: string }[] = []): Option[] =>
  rows.map((r) => ({ value: String(r.id), label: r.name }));
export type Values = Record<string, string | boolean>;
export type FormField = {
  name: string;
  label: string;
  type?:
    | "text"
    | "email"
    | "tel"
    | "date"
    | "datetime-local"
    | "time"
    | "number"
    | "select"
    | "search"
    | "checkbox"
    | "textarea";
  required?: boolean;
  options?: Option[];
  min?: number | string;
  max?: number | string;
  maxLength?: number;
  pattern?: string;
  description?: string;
  disabled?: boolean;
  showWhen?: { name: string; value: string };
  /** 기본 입력 대신 직접 그린다. 다른 필드 값도 읽고 바꿀 수 있다 (예: 시작·종료 시각을 함께 고르는 칸). */
  render?: (field: CustomFieldProps) => ReactNode;
};
export type CustomFieldProps = {
  id: string;
  value: string;
  onChange: (value: string) => void;
  onBlur: () => void;
  values: Values;
  setValue: (name: string, value: string) => void;
  disabled?: boolean;
  invalid: boolean;
  describedBy?: string;
};
export const textValue = (values: Values, key: string) => String(values[key] ?? "");
export const numValue = (values: Values, key: string) => Number(values[key]);
export const errorMessage = (error: unknown) =>
  error instanceof ApiError
    ? error.problem.status === 401
      ? "로그인이 만료되었습니다. 다시 로그인해 주세요."
      : error.problem.status === 403
        ? "이 작업을 처리할 권한이 없습니다."
        : error.message
    : error instanceof Error
      ? error.message
      : "요청을 처리하지 못했습니다. 다시 시도해 주세요.";

export const ALL: Option = { value: "ALL", label: "전체" };
export const filterValue = (v: Values, key: string) =>
  v[key] && v[key] !== "ALL" ? String(v[key]) : undefined;
