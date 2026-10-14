import { useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { MenuPanel } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/Dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { PageTitle } from "@/components/ui/PageTitle";
import { StatusBadge, getStatusLabel } from "@/components/ui/StatusBadge";
import { seoulDate, seoulTime, type DashboardData, type DashboardReservation, type DashboardUser } from "../dashboard/model";
import { dashboardPreviewTime } from "../dashboard/preview";
import { attendanceRestriction, settledLessonIds, type AttendanceChange, type AttendanceStatus } from "./model";
import { reservationPassNames } from "./preview";
import { InstructorTable } from "./InstructorTable";
import { InstructorConfirm, InstructorSelect } from "./InstructorControls";
import type { Screen } from "@/app/routes";

type Mode = "attendees" | "attendance" | "attendance-corrections";
type Props = { mode: Mode; user: DashboardUser; data: DashboardData; selectedId: number; changes: AttendanceChange[]; onNavigate: (screen: Screen, id?: number) => void; onRecord: (id: number, status: AttendanceStatus, reason: string) => void };
const titles = { attendees: "수업별 예약자", attendance: "출결 처리", "attendance-corrections": "출결 정정" };
const initialFilters = { search: "", from: "2026-10-09", to: "2026-10-18", status: "all" };
const statusName = (status: DashboardReservation["status"]) => getStatusLabel({ domain: "reservation", status });

export function InstructorLessonsPage({ mode, user, data, selectedId, changes, onNavigate, onRecord }: Props) {
  const [draft, setDraft] = useState(initialFilters);
  const [filters, setFilters] = useState(initialFilters);
  const [lessonId, setLessonId] = useState(selectedId);
  const [detail, setDetail] = useState<DashboardReservation | null>(null);
  const [editing, setEditing] = useState<DashboardReservation | null>(null);
  const correctionTrigger = useRef<HTMLButtonElement | null>(null);
  const [confirmation, setConfirmation] = useState<{ reservation: DashboardReservation; status: AttendanceStatus; reason: string } | null>(null);
  const ownLessons = data.lessons.filter(item => item.instructorId === user.id && item.status !== "CANCELED");
  const lessons = ownLessons.filter(item => {
    const day = seoulDate(item.startAt);
    const hasHistory = data.reservations.some(row => row.lessonId === item.id && ["ATTENDED", "NO_SHOW"].includes(row.status));
    return (!filters.from || day >= filters.from) && (!filters.to || day <= filters.to) && item.title.includes(filters.search.trim()) && (mode !== "attendance-corrections" || hasHistory) && (filters.status === "all" || (filters.status === "upcoming" ? Date.parse(item.startAt) > Date.parse(dashboardPreviewTime) : Date.parse(item.startAt) <= Date.parse(dashboardPreviewTime)));
  }).sort((a, b) => Date.parse(b.startAt) - Date.parse(a.startAt));
  const selected = lessons.find(item => item.id === lessonId) ?? lessons[0];
  const selectedReservations = selected ? data.reservations.filter(item => item.lessonId === selected.id) : [];
  const roster = selectedReservations.filter(item => mode === "attendees" || (mode === "attendance" ? item.status !== "CANCELED" : ["ATTENDED", "NO_SHOW"].includes(item.status)));
  const [memberSearch, setMemberSearch] = useState("");
  const [rosterStatus, setRosterStatus] = useState("all");
  const shownRoster = roster.filter(item => item.member.includes(memberSearch.trim()) && (rosterStatus === "all" || item.status === rosterStatus));
  const visibleHistory = changes.filter(item => item.before !== "RESERVED" && data.reservations.some(row => row.id === item.reservationId && row.lessonId === selected?.id));
  const action = mode === "attendees" ? "예약자 보기" : mode === "attendance" ? "출결 보기" : "정정 내역 보기";
  const detailsLesson = ownLessons.find(item => item.id === detail?.lessonId);

  function requestRecord(reservation: DashboardReservation, status: AttendanceStatus, reason = "") {
    const error = attendanceRestriction(data, user, reservation.id, dashboardPreviewTime);
    if (error) { toast.error(error); return; }
    setConfirmation({ reservation, status, reason });
  }

  return <div className="menu-page instructor-page">
    <PageTitle title={titles[mode]} description={mode === "attendees" ? "담당 수업을 선택하고 예약자와 예약 정보를 확인하세요." : mode === "attendance" ? "수업별 예약자의 출석과 노쇼를 기록하세요." : "기록한 출결을 확인하고 필요한 내역을 정정하세요."} />
    <MenuPanel variant="filter"><form className="menu-filters" onSubmit={event => { event.preventDefault(); setFilters(draft); setMemberSearch(""); setRosterStatus("all"); }}>
      <Field><FieldLabel htmlFor="instructor-search">수업명</FieldLabel><Input id="instructor-search" placeholder="수업명 검색" value={draft.search} onChange={event => setDraft({ ...draft, search: event.target.value })} /></Field>
      <Field><FieldLabel htmlFor="instructor-from">시작일</FieldLabel><Input id="instructor-from" type="date" max={draft.to || undefined} value={draft.from} onChange={event => setDraft({ ...draft, from: event.target.value })} /></Field>
      <Field><FieldLabel htmlFor="instructor-to">종료일</FieldLabel><Input id="instructor-to" type="date" min={draft.from || undefined} value={draft.to} onChange={event => setDraft({ ...draft, to: event.target.value })} /></Field>
      <InstructorSelect id="instructor-status" label="수업 상태" value={draft.status} onChange={status => setDraft({ ...draft, status })} options={[{ value: "all", label: "전체 수업" }, { value: "started", label: "시작한 수업" }, { value: "upcoming", label: "예정된 수업" }]} />
      <div className="menu-actions"><Button type="submit" variant="outline">조회</Button><Button type="button" variant="outline" onClick={() => { setDraft(initialFilters); setFilters(initialFilters); setMemberSearch(""); setRosterStatus("all"); }}>초기화</Button></div>
    </form></MenuPanel>
    <div className="menu-results"><strong>담당 수업 {lessons.length}개</strong><span>서울 시간 기준</span></div>
    <InstructorTable key={JSON.stringify(filters)} rows={lessons} selectedId={selected?.id} label="담당 수업 목록" columns={[
      { key: "number", label: "번호", cell: row => lessons.indexOf(row) + 1 },
      { key: "date", label: "수업일", value: row => row.startAt, cell: row => seoulDate(row.startAt) },
      { key: "time", label: "시간", value: row => seoulTime(row.startAt), cell: row => `${seoulTime(row.startAt)}–${seoulTime(row.endAt)}` },
      { key: "title", label: "수업", value: row => row.title, cell: row => <strong>{row.title}</strong> },
      { key: "room", label: "룸", cell: row => row.room },
      { key: "count", label: "예약 / 정원", cell: row => `${data.reservations.filter(item => item.lessonId === row.id && item.status !== "CANCELED").length} / ${row.capacity}명` },
      { key: "detail", label: "선택", cell: row => <Button variant="outline" size="sm" aria-label={`${row.title} ${action}`} aria-pressed={selected?.id === row.id} onClick={() => { setLessonId(row.id); setMemberSearch(""); setRosterStatus("all"); }}>{selected?.id === row.id ? "선택됨" : action}</Button> },
    ]} />
    {selected && <section className="instructor-roster" aria-label={`${selected.title} 예약자·출결`}>
      <MenuPanel><div className="instructor-lesson-summary"><div><h2>{selected.title}</h2><p>{seoulDate(selected.startAt)} · {seoulTime(selected.startAt)}–{seoulTime(selected.endAt)} · {selected.room}</p></div>
        <div className="instructor-summary-counts"><span>예약 <strong>{selectedReservations.filter(item => item.status !== "CANCELED").length}명</strong></span><span>출석 <strong>{selectedReservations.filter(item => item.status === "ATTENDED").length}명</strong></span><span>노쇼 <strong>{selectedReservations.filter(item => item.status === "NO_SHOW").length}명</strong></span></div>
      </div></MenuPanel>
      {mode !== "attendees" && (settledLessonIds.has(selected.id) || Date.parse(selected.startAt) > Date.parse(dashboardPreviewTime)) && <p className="menu-note" role="status">{settledLessonIds.has(selected.id) ? "정산에 포함된 수업은 출결을 변경할 수 없습니다. 관리자에게 정산 확인을 요청해 주세요." : "아직 시작하지 않은 수업입니다. 수업 시작 후 출결을 처리할 수 있습니다."}</p>}
      <div className="instructor-roster-toolbar"><h2>예약자 {shownRoster.length}명</h2><div className="instructor-roster-filters"><Field><FieldLabel htmlFor="roster-member" className="sr-only">회원 이름</FieldLabel><Input id="roster-member" placeholder="회원 이름 검색" value={memberSearch} onChange={event => setMemberSearch(event.target.value)} /></Field>
        <InstructorSelect id="roster-status" label="예약·출결 상태" value={rosterStatus} onChange={setRosterStatus} options={[{ value: "all", label: "전체 상태" }, ...(mode === "attendance-corrections" ? ["ATTENDED", "NO_SHOW"] : mode === "attendance" ? ["RESERVED", "ATTENDED", "NO_SHOW"] : ["RESERVED", "ATTENDED", "NO_SHOW", "CANCELED"]).map(value => ({ value, label: statusName(value as DashboardReservation["status"]) }))]} />
      </div></div>
      <InstructorTable key={`${selected.id}-${memberSearch}-${rosterStatus}`} rows={shownRoster} label={`${selected.title} 예약자 목록`} emptyTitle="표시할 예약자가 없습니다" columns={[
        { key: "number", label: "번호", cell: row => roster.indexOf(row) + 1 },
        { key: "member", label: "회원", value: row => row.member, cell: row => <strong>{row.member}</strong> },
        { key: "pass", label: "이용권", cell: row => reservationPassNames[row.id] ?? "이용권 정보 없음" },
        { key: "status", label: "예약·출결 상태", cell: row => <StatusBadge domain="reservation" status={row.status} /> },
        { key: "action", label: mode === "attendees" ? "상세" : "처리", cell: row => <div className="menu-actions">
          {mode === "attendees" ? <Button variant="outline" size="sm" onClick={() => setDetail(row)}>예약 상세</Button> : mode === "attendance" && row.status === "RESERVED" ? <><Button size="sm" disabled={!!attendanceRestriction(data, user, row.id, dashboardPreviewTime)} onClick={() => requestRecord(row, "ATTENDED")}>출석 처리</Button><Button variant="outline" size="sm" disabled={!!attendanceRestriction(data, user, row.id, dashboardPreviewTime)} onClick={() => requestRecord(row, "NO_SHOW")}>노쇼 처리</Button></> : <Button variant="outline" size="sm" disabled={!!attendanceRestriction(data, user, row.id, dashboardPreviewTime)} onClick={event => { if (mode === "attendance") onNavigate("attendance-corrections", selected.id); else { correctionTrigger.current = event.currentTarget; setEditing(row); } }}>출결 정정</Button>}
        </div> },
      ]} />
      {mode === "attendees" && <div className="instructor-bottom-actions"><Button onClick={() => onNavigate("attendance", selected.id)}>이 수업 출결 처리</Button></div>}
      {mode === "attendance-corrections" && <><div className="menu-results"><h2>정정 이력</h2><span>{visibleHistory.length}건</span></div><InstructorTable rows={visibleHistory} label="출결 정정 이력" emptyTitle="아직 정정한 출결이 없습니다" columns={[
        { key: "number", label: "번호", cell: row => visibleHistory.indexOf(row) + 1 },
        { key: "member", label: "회원", value: row => data.reservations.find(item => item.id === row.reservationId)?.member ?? "", cell: row => data.reservations.find(item => item.id === row.reservationId)?.member },
        { key: "before", label: "변경 전", cell: row => <StatusBadge domain="reservation" status={row.before} /> },
        { key: "after", label: "변경 후", cell: row => <StatusBadge domain="reservation" status={row.after} /> },
        { key: "reason", label: "정정 사유", cell: row => <span className="instructor-wrap">{row.reason}</span> },
        { key: "at", label: "처리 일시", value: row => row.at, cell: row => `${seoulDate(row.at)} ${seoulTime(row.at)}` },
      ]} /></>}
    </section>}
    {detail && detailsLesson && <Dialog dialog={{ title: "예약 상세", body: `${detail.member}님의 수업 예약 정보입니다.` }} onClose={() => setDetail(null)}><div className="instructor-dialog-stack"><div className="instructor-detail-heading"><h2>{detail.member}</h2><StatusBadge domain="reservation" status={detail.status} /></div><dl className="menu-facts">{[["예약 번호", `#${detail.id}`], ["수업", detailsLesson.title], ["수업일", seoulDate(detailsLesson.startAt)], ["시간", `${seoulTime(detailsLesson.startAt)}–${seoulTime(detailsLesson.endAt)}`], ["룸", detailsLesson.room], ["이용권", reservationPassNames[detail.id] ?? "정보 없음"]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl></div></Dialog>}
    {editing && <CorrectionForm reservation={editing} onClose={() => setEditing(null)} onSubmit={(status, reason) => { setEditing(null); requestRecord(editing, status, reason); }} />}
    {confirmation && <InstructorConfirm returnFocus={confirmation.reason ? correctionTrigger.current : undefined} title={confirmation.reason ? "출결 정정 확인" : "출결 처리 확인"} description={`${confirmation.reservation.member}님의 출결을 ${statusName(confirmation.status)}로 ${confirmation.reason ? "정정" : "기록"}합니다.${confirmation.reason ? `\n정정 사유: ${confirmation.reason}` : ""}\n이용권은 추가로 차감되지 않습니다.`} action={confirmation.reason ? "정정 확정" : "출결 확정"} onClose={() => setConfirmation(null)} onConfirm={() => { onRecord(confirmation.reservation.id, confirmation.status, confirmation.reason); setConfirmation(null); }} />}
  </div>;
}

function CorrectionForm({ reservation, onClose, onSubmit }: { reservation: DashboardReservation; onClose: () => void; onSubmit: (status: AttendanceStatus, reason: string) => void }) {
  const { register, handleSubmit, formState: { errors } } = useForm<{ reason: string }>();
  const nextStatus = reservation.status === "ATTENDED" ? "NO_SHOW" : "ATTENDED";
  return <Dialog dialog={{ title: "출결 정정", body: "변경할 출결과 정정 사유를 확인하세요.", action: "변경 내용 확인" }} formId="correction-form" onClose={onClose}>
    <form id="correction-form" className="instructor-dialog-stack" onSubmit={handleSubmit(values => onSubmit(nextStatus, values.reason.trim()))}>
      <div className="instructor-detail-heading"><h2>{reservation.member}</h2><StatusBadge domain="reservation" status={reservation.status} /></div>
      <dl className="menu-facts"><div><dt>현재 출결</dt><dd>{statusName(reservation.status)}</dd></div><div><dt>변경할 출결</dt><dd><StatusBadge domain="reservation" status={nextStatus} /></dd></div></dl>
      <Field data-invalid={!!errors.reason}><FieldLabel htmlFor="correction-reason">정정 사유</FieldLabel><Textarea id="correction-reason" placeholder="출결을 변경하는 사유를 입력해 주세요" maxLength={500} aria-invalid={!!errors.reason} aria-describedby={errors.reason ? "correction-error" : undefined} {...register("reason", { validate: value => !!value.trim() || "정정 사유를 입력해 주세요.", maxLength: 500 })} /><FieldError id="correction-error" errors={[errors.reason]} /></Field>
    </form>
  </Dialog>;
}
