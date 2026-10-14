import type { Dispatch, SetStateAction } from "react";
import { toast } from "sonner";
import type { Screen } from "@/app/routes";
import { seoulDate, type DashboardData, type DashboardUser } from "../dashboard/model";
import { dashboardPreviewTime } from "../dashboard/preview";
import { InstructorLessonsPage } from "./InstructorLessonsPage";
import { InstructorAbsencesPage } from "./InstructorAbsencesPage";
import { absenceRestriction, attendanceRestriction, canWithdrawAbsence, type InstructorState } from "./model";
import "./instructor.css";

export type InstructorScreen = "attendees" | "attendance" | "attendance-corrections" | "absences";
export function isInstructorScreen(screen: Screen): screen is InstructorScreen {
  return ["attendees", "attendance", "attendance-corrections", "absences"].includes(screen);
}

export function InstructorPage({ screen, user, data, setData, state, setState, selectedId, onNavigate }: {
  screen: InstructorScreen; user: DashboardUser; data: DashboardData; setData: Dispatch<SetStateAction<DashboardData>>;
  state: InstructorState; setState: Dispatch<SetStateAction<InstructorState>>; selectedId: number; onNavigate: (screen: Screen, id?: number) => void;
}) {
  if (screen === "absences") return <InstructorAbsencesPage user={user} data={data} state={state} onCreate={(detail, reason) => {
    const error = absenceRestriction(data.absences, state.absenceDetails, user.id, detail.startAt, detail.endAt, dashboardPreviewTime);
    if (error) { toast.error(error); return; }
    const id = Math.max(0, ...data.absences.map(item => item.id)) + 1;
    setState(previous => ({ ...previous, absenceDetails: { ...previous.absenceDetails, [id]: detail } }));
    setData(previous => ({ ...previous, absences: [{ id, instructorId: user.id, instructor: user.name, from: seoulDate(detail.startAt), to: seoulDate(detail.endAt), reason, status: "REQUESTED" }, ...previous.absences] }));
    toast.success("부재 신청이 접수되었습니다.");
  }} onWithdraw={id => {
    const absence = data.absences.find(item => item.id === id);
    if (!absence || !canWithdrawAbsence(absence, user)) { toast.error("승인 대기 중인 본인 신청만 철회할 수 있습니다."); return; }
    setData(previous => ({ ...previous, absences: previous.absences.map(item => item.id === id ? { ...item, status: "CANCELED" } : item) }));
    setState(previous => ({ ...previous, absenceDetails: { ...previous.absenceDetails, [id]: { ...previous.absenceDetails[id], processedAt: dashboardPreviewTime } } }));
    toast.success("부재 신청을 철회했습니다.");
  }} />;
  return <InstructorLessonsPage key={`${screen}-${selectedId}`} mode={screen} user={user} data={data} selectedId={selectedId} changes={state.changes} onNavigate={onNavigate} onRecord={(id, status, reason) => {
    const error = attendanceRestriction(data, user, id, dashboardPreviewTime);
    if (error) { toast.error(error); return; }
    const reservation = data.reservations.find(item => item.id === id)!;
    if (reservation.status === status) return;
    if (reservation.status !== "RESERVED" && !reason.trim()) { toast.error("정정 사유를 입력해 주세요."); return; }
    setData(previous => ({ ...previous, reservations: previous.reservations.map(item => item.id === id ? { ...item, status } : item) }));
    setState(previous => ({ ...previous, changes: [{ id: (previous.changes[0]?.id ?? 0) + 1, reservationId: id, before: reservation.status, after: status, reason, at: dashboardPreviewTime }, ...previous.changes] }));
    toast.success(reason ? "출결을 정정했습니다." : "출결을 기록했습니다.");
  }} />;
}
