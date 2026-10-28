import { useState, type ComponentProps } from "react";
import { CalendarIcon } from "lucide-react";
import { cn } from "cn";
import type { Matcher } from "react-day-picker";
import { ko } from "react-day-picker/locale";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import "./date-picker.css";

// 값은 네이티브 date 입력과 같은 "YYYY-MM-DD" 문자열이다. 시간대 변환 없이 달력의 현지 날짜로만 다룬다.
const toDate = (value?: string) => {
  const [year, month, day] = (value ?? "").split("-").map(Number);
  return year && month && day ? new Date(year, month - 1, day) : undefined;
};
const pad = (n: number) => String(n).padStart(2, "0");
const toValue = (date: Date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const toLabel = (date: Date) =>
  `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`;

/** 네이티브 `input type="date"`를 대신하는 날짜 선택. 칸을 누르면 달력이 바로 열린다. */
function DatePicker({
  value,
  onChange,
  onBlur,
  min,
  max,
  isDisabled,
  placeholder = "날짜 선택",
  className,
  ...props
}: Omit<ComponentProps<typeof Button>, "value" | "onChange" | "onBlur"> & {
  value?: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  min?: string;
  max?: string;
  /** 고를 수 없는 날짜 (예: 휴무일). 값은 "YYYY-MM-DD"로 받는다. */
  isDisabled?: (value: string) => boolean;
  placeholder?: string;
}) {
  const [open, setOpen] = useState(false);
  const selected = toDate(value);
  const from = toDate(min);
  const to = toDate(max);
  const today = new Date();
  const todayValue = toValue(today);
  const disabled = [
    from && { before: from },
    to && { after: to },
    isDisabled && ((date: Date) => isDisabled(toValue(date))),
  ].filter(Boolean) as Matcher[];
  const close = () => {
    setOpen(false);
    onBlur?.();
  };
  return (
    <Popover
      open={open}
      onOpenChange={(next) => (next ? setOpen(true) : close())}
    >
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          data-slot="date-picker"
          data-empty={!selected}
          className={cn("date-picker-trigger", className)}
          {...props}
        >
          <span>{selected ? toLabel(selected) : placeholder}</span>
          <CalendarIcon aria-hidden />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="date-picker-popup"
        align="start"
      >
        <Calendar
          mode="single"
          locale={ko}
          captionLayout="dropdown"
          selected={selected}
          defaultMonth={selected ?? to ?? today}
          startMonth={from ?? new Date(1900, 0)}
          endMonth={to ?? new Date(today.getFullYear() + 10, 11)}
          disabled={disabled}
          onSelect={(date) => {
            if (date) onChange(toValue(date));
            close();
          }}
        />
        <div className="date-picker-footer">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={!value}
            onClick={() => {
              onChange("");
              close();
            }}
          >
            지우기
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={Boolean(
              (min && todayValue < min) || (max && todayValue > max) || isDisabled?.(todayValue),
            )}
            onClick={() => {
              onChange(todayValue);
              close();
            }}
          >
            오늘
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}

export { DatePicker };
