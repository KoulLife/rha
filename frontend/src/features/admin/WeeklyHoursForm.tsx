import { useId, useState, type ReactNode } from "react";
import type { DayOfWeek } from "@/api/generated/api";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Field, FieldLabel } from "@/components/ui/field";
import { AdminForm } from "./AdminUI";

export const weekDays: DayOfWeek[] = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
const labels = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"];
export type HoursDay = { dayOfWeek: DayOfWeek; enabled: boolean; slots: { startTime: string; endTime: string }[] };
const defaultSlot = () => ({ startTime: "09:00", endTime: "21:00" });

export function WeeklyHoursForm({ initial, multiple = false, submitLabel, onSubmit, before, variant = "default", externalDirty = false }: {
  variant?: "default" | "settings";
  externalDirty?: boolean;
  before?: ReactNode;
  initial: HoursDay[];
  multiple?: boolean;
  submitLabel: string;
  onSubmit: (days: HoursDay[]) => Promise<unknown>;
}) {
  const uid = useId();
  const [days, setDays] = useState(initial);
  const [bulk, setBulk] = useState(defaultSlot);
  const [bulkError, setBulkError] = useState("");
  const dirty = externalDirty || JSON.stringify(days) !== JSON.stringify(initial);
  const selected = days.filter((day) => day.enabled).length;
  const select = (mode: "all" | "none" | "weekdays") => setDays((previous) => previous.map((day, i) => ({
    ...day, enabled: mode === "all" || (mode === "weekdays" && i < 5),
  })));
  const updateSlot = (dayIndex: number, slotIndex: number, key: "startTime" | "endTime", value: string) =>
    setDays((previous) => previous.map((day, i) => i === dayIndex ? {
      ...day, slots: day.slots.map((slot, j) => j === slotIndex ? { ...slot, [key]: value } : slot),
    } : day));
  return (
    <AdminForm fields={[]} variant={variant} submitLabel={submitLabel}
      deferSuccess={variant === "settings"} submitDisabled={variant === "settings" && !dirty}
      toolbarAction={variant === "settings" ? <span className="menu-note" role="status">{dirty ? "저장하지 않은 변경사항이 있습니다" : "저장된 설정입니다"}</span> : undefined}
      onSubmit={async () => {
      for (const [i, day] of days.entries()) {
        if (!day.enabled) continue;
        const sorted = [...day.slots].sort((a, b) => a.startTime.localeCompare(b.startTime));
        if (sorted.some((slot, j) => !slot.startTime || !slot.endTime || slot.startTime >= slot.endTime || (j > 0 && sorted[j - 1].endTime > slot.startTime))) {
          throw new Error(`${labels[i]}의 종료 시간은 시작 이후여야 하며, 시간대가 겹치면 안 됩니다.`);
        }
      }
      return onSubmit(days);
    }}>
      <div className={variant === "settings" ? "instructor-settings-grid" : "menu-stack"}>
      {before}
      <section className="weekly-hours" aria-label="요일별 시간 설정">
        {variant === "settings" && <h3>수업 가능시간</h3>}
        <p className="menu-note">{multiple ? "수업 가능한 요일" : "영업하는 요일"}을 선택하세요. 선택하지 않은 요일은 {multiple ? "수업 불가" : "휴무"}입니다. 변경 내용은 저장할 때 적용됩니다.</p>
        <div className="menu-actions">
          <Button type="button" variant="outline" onClick={() => select("all")}>전체 선택</Button>
          <Button type="button" variant="outline" onClick={() => select("none")}>전체 해제</Button>
          <Button type="button" variant="outline" onClick={() => select("weekdays")}>평일만 선택</Button>
          <span aria-live="polite">{selected}일 선택</span>
        </div>
        <div className="weekly-hours-bulk">
          <Field><FieldLabel htmlFor={`${uid}-bulk-start`}>일괄 시작 시간</FieldLabel><Input id={`${uid}-bulk-start`} type="time" value={bulk.startTime} onChange={(e) => setBulk({ ...bulk, startTime: e.target.value })} /></Field>
          <Field><FieldLabel htmlFor={`${uid}-bulk-end`}>일괄 종료 시간</FieldLabel><Input id={`${uid}-bulk-end`} type="time" value={bulk.endTime} onChange={(e) => setBulk({ ...bulk, endTime: e.target.value })} /></Field>
          <Button type="button" variant="outline" disabled={!selected} onClick={() => {
            if (!bulk.startTime || !bulk.endTime || bulk.startTime >= bulk.endTime) {
              setBulkError("종료 시간은 시작 이후여야 합니다."); return;
            }
            setBulkError("");
            setDays((previous) => previous.map((day) => day.enabled ? { ...day, slots: [{ ...bulk }] } : day));
          }}>선택한 요일에 적용</Button>
        </div>
        {bulkError && <p role="alert">{bulkError}</p>}
        {multiple && <p className="menu-note">일괄 적용하면 선택한 요일의 모든 시간대를 위 시간대 하나로 바꿉니다.</p>}
        {days.map((day, i) => (
          <div className="weekly-hours-day" key={day.dayOfWeek}>
            <Field orientation="horizontal">
              <Checkbox id={`${uid}-${day.dayOfWeek}`} checked={day.enabled} onCheckedChange={(checked) => setDays((previous) => previous.map((item, j) => j === i ? { ...item, enabled: checked === true } : item))} />
              <FieldLabel htmlFor={`${uid}-${day.dayOfWeek}`}>{labels[i]}</FieldLabel>
            </Field>
            <div className="weekly-hours-slots">
              {day.enabled ? day.slots.map((slot, j) => (
                <div className="weekly-hours-range" key={j}>
                  <Field><FieldLabel className={variant === "settings" ? "sr-only" : undefined} htmlFor={`${uid}-${i}-${j}-start`}>시작</FieldLabel><Input id={`${uid}-${i}-${j}-start`} aria-label={`${labels[i]} ${j + 1}번째 시작`} type="time" required value={slot.startTime} onChange={(e) => updateSlot(i, j, "startTime", e.target.value)} /></Field>
                  <Field><FieldLabel className={variant === "settings" ? "sr-only" : undefined} htmlFor={`${uid}-${i}-${j}-end`}>종료</FieldLabel><Input id={`${uid}-${i}-${j}-end`} aria-label={`${labels[i]} ${j + 1}번째 종료`} type="time" required value={slot.endTime} onChange={(e) => updateSlot(i, j, "endTime", e.target.value)} /></Field>
                  {multiple && day.slots.length > 1 && <Button type="button" variant="ghost" aria-label={`${labels[i]} ${j + 1}번째 시간대 삭제`} onClick={() => setDays((previous) => previous.map((item, k) => k === i ? { ...item, slots: item.slots.filter((_, n) => n !== j) } : item))}>삭제</Button>}
                </div>
              )) : <p className="menu-note">{multiple ? "수업 불가" : "휴무"}</p>}
              {multiple && day.enabled && <Button type="button" variant="ghost" onClick={() => setDays((previous) => previous.map((item, j) => j === i ? { ...item, slots: [...item.slots, defaultSlot()] } : item))}>시간대 추가</Button>}
            </div>
          </div>
        ))}
      </section>
      </div>
    </AdminForm>
  );
}
