// 날짜+시각 값은 네이티브 datetime-local 입력과 같은 "YYYY-MM-DDTHH:mm" 문자열이다.
export type DateTimeParts = { date: string; hour: string; minute: string };

const pad = (n: number) => String(n).padStart(2, "0");

export function splitDateTime(value?: string): DateTimeParts {
  const [date = "", time = ""] = (value ?? "").split("T");
  const [hour = "", minute = ""] = time.split(":");
  return { date, hour, minute };
}

// 날짜가 없으면 값이 없는 것으로 보고, 시·분을 아직 고르지 않았으면 00으로 채운다.
export function joinDateTime({ date, hour, minute }: DateTimeParts) {
  return date ? `${date}T${hour || "00"}:${minute || "00"}` : "";
}

export const hourOptions = () => Array.from({ length: 24 }, (_, i) => pad(i));

// 5분 단위. 단위에 맞지 않는 기존 값도 고른 상태로 보이도록 목록에 넣는다.
export function minuteOptions(current?: string) {
  const options = Array.from({ length: 12 }, (_, i) => pad(i * 5));
  return current && !options.includes(current) ? [...options, current].sort() : options;
}
