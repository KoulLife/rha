import { useState } from "react";
import { DatePicker } from "@/components/ui/date-picker";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  hourOptions,
  joinDateTime,
  minuteOptions,
  splitDateTime,
  type DateTimeParts,
} from "@/lib/date-time";
import "./date-time-picker.css";

/**
 * 네이티브 `input type="datetime-local"`을 대신하는 날짜+시각 선택.
 * 브라우저의 시각 선택(12시간제, 끝없이 반복되는 스크롤) 대신 00~23시와 5분 단위로 고른다.
 */
function DateTimePicker({
  id,
  label,
  value,
  onChange,
  onBlur,
  disabled,
  ...aria
}: {
  id?: string;
  /** 시·분 선택의 접근성 이름 앞에 붙는다. 예: "시작 시각" → "시작 시각 시" */
  label: string;
  value?: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  disabled?: boolean;
  "aria-invalid"?: boolean;
  "aria-describedby"?: string;
}) {
  // 날짜를 고르기 전에 시·분을 먼저 골라도 잃지 않도록 따로 둔다.
  const [pending, setPending] = useState<DateTimeParts>(() => splitDateTime(value));
  const parts = value ? splitDateTime(value) : pending;
  const update = (patch: Partial<DateTimeParts>) => {
    const next = { ...parts, ...patch };
    setPending(next);
    onChange(joinDateTime(next));
  };
  return (
    <div
      className="date-time-picker"
      data-slot="date-time-picker"
    >
      <DatePicker
        id={id}
        value={parts.date}
        onChange={(date) => update({ date })}
        onBlur={onBlur}
        disabled={disabled}
        {...aria}
      />
      <Select
        value={parts.hour}
        onValueChange={(hour) => update({ hour })}
        disabled={disabled}
      >
        <SelectTrigger
          aria-label={`${label} 시`}
          aria-invalid={aria["aria-invalid"]}
        >
          <SelectValue placeholder="시" />
        </SelectTrigger>
        <SelectContent className="date-time-picker-popup">
          {hourOptions().map((hour) => (
            <SelectItem
              key={hour}
              value={hour}
            >
              {hour}시
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select
        value={parts.minute}
        onValueChange={(minute) => update({ minute })}
        disabled={disabled}
      >
        <SelectTrigger
          aria-label={`${label} 분`}
          aria-invalid={aria["aria-invalid"]}
        >
          <SelectValue placeholder="분" />
        </SelectTrigger>
        <SelectContent className="date-time-picker-popup">
          {minuteOptions(parts.minute).map((minute) => (
            <SelectItem
              key={minute}
              value={minute}
            >
              {minute}분
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

export { DateTimePicker };
