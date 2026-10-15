import { useState } from "react";
import { ReservationSummary } from "@/components/ui/ReservationSummary";
import { MenuPanel } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/Dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { PageTitle } from "@/components/ui/PageTitle";
import { StatusBadge, getStatusLabel } from "@/components/ui/StatusBadge";
import { seoulDate, seoulTime, type DashboardData, type DashboardReservation, type DashboardUser } from "../dashboard/model";
import { dashboardPreviewTime } from "../dashboard/preview";
import { attendanceRestriction, settledLessonIds, type AttendanceChange, type AttendanceStatus } from "./model";
import { reservationPassNames } from "./preview";
import { InstructorTable } from "./InstructorTable";
import { InstructorSelect } from "./InstructorControls";
import { AttendanceActions } from "./AttendanceActions";
import { MemberInfoButton } from "../dashboard/ReservationRoster";
import type { Screen } from "@/app/routes";

type Mode = "attendees" | "attendance";
type Props = { mode: Mode; user: DashboardUser; data: DashboardData; selectedId: number; changes: AttendanceChange[]; onNavigate: (screen: Screen, id?: number) => void; onRecord: (id: number, status: AttendanceStatus, reason: string) => void };
const titles = { attendees: "수업별 예약자", attendance: "출결" };
const initialFilters = { search: "", from: "2026-10-09", to: "2026-10-18", status: "all" };
const statusName = (status: DashboardReservation["status"]) => getStatusLabel({ domain: "reservation", status });

export function InstructorLessonsPage({ mode, user, data, selectedId, changes, onNavigate, onRecord }: Props) {
  const [draft, setDraft] = useState(initialFilters);
  const filters = draft;
  const [lessonId, setLessonId] = useState(selectedId);
  const [detail, setDetail] = useState<DashboardReservation | null>(null);
  const ownLessons = data.lessons.filter(item => item.instructorId === user.id && item.status !== "CANCELED");
  const lessons = ownLessons.filter(item => {
    const day = seoulDate(item.startAt);
    return (!filters.from || day >= filters.from) && (!filters.to || day <= filters.to) && item.title.includes(filters.search.trim()) && (filters.status === "all" || (filters.status === "upcoming" ? Date.parse(item.startAt) > Date.parse(dashboardPreviewTime) : Date.parse(item.startAt) <= Date.parse(dashboardPreviewTime)));
  }).sort((a, b) => Date.parse(b.startAt) - Date.parse(a.startAt));
  const selected = lessons.find(item => item.id === lessonId) ?? lessons[0];
  const selectedReservations = selected ? data.reservations.filter(item => item.lessonId === selected.id) : [];
  const roster = selectedReservations.filter(item => mode === "attendees" || item.status !== "CANCELED");
  const [memberSearch, setMemberSearch] = useState("");
  const [rosterStatus, setRosterStatus] = useState("all");
  const shownRoster = roster.filter(item => item.member.includes(memberSearch.trim()) && (rosterStatus === "all" || item.status === rosterStatus));
  const visibleHistory = changes.filter(item => item.before !== "RESERVED" && data.reservations.some(row => row.id === item.reservationId && row.lessonId === selected?.id));
  const action = mode === "attendees" ? "예약자 보기" : "출결 보기";
  const detailsLesson = ownLessons.find(item => item.id === detail?.lessonId);

  return <div className="menu-page instructor-page">
    <PageTitle title={titles[mode]} description={mode === "attendees" ? "담당 수업을 선택하고 예약자와 예약 정보를 확인하세요." : "수업별 출석·노쇼를 기록하고, 정정 사유를 입력해 수정하세요."} />
    <MenuPanel variant="filter"><div className="menu-filters">
      <Field><FieldLabel htmlFor="instructor-search">수업명</FieldLabel><Input id="instructor-search" placeholder="수업명 검색" value={draft.search} onChange={event => setDraft({ ...draft, search: event.target.value })} /></Field>
      <Field><FieldLabel htmlFor="instructor-from">시작일</FieldLabel><Input id="instructor-from" type="date" max={draft.to || undefined} value={draft.from} onChange={event => setDraft({ ...draft, from: event.target.value })} /></Field>
      <Field><FieldLabel htmlFor="instructor-to">종료일</FieldLabel><Input id="instructor-to" type="date" min={draft.from || undefined} value={draft.to} onChange={event => setDraft({ ...draft, to: event.target.value })} /></Field>
      <InstructorSelect id="instructor-status" label="수업 상태" value={draft.status} onChange={status => setDraft({ ...draft, status })} options={[{ value: "all", label: "전체 수업" }, { value: "started", label: "시작한 수업" }, { value: "upcoming", label: "예정된 수업" }]} />
      <div className="menu-actions"><Button type="button" variant="outline" onClick={() => { setDraft(initialFilters); setMemberSearch(""); setRosterStatus("all"); }}>초기화</Button></div>
    </div></MenuPanel>
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
      {mode !== "attendees" && (settledLessonIds.has(selected.id) || Date.parse(selected.startAt) > Date.parse(dashboardPreviewTime)) && <p className="menu-note" role="status">{settledLessonIds.has(selected.id) ? "정산에 포함된 수업은 출결을 변경할 수 없습니다. 관리자에게 정산 확인을 요청해 주세요." : "아직 시작하지 않은 수업입니다. 수업 시작 후 출결을 처리할 수 있습니다."}</p>}
      <div className="instructor-roster-toolbar"><ReservationSummary total={shownRoster.length} reserved={selectedReservations.filter(item => item.status !== "CANCELED").length} attended={selectedReservations.filter(item => item.status === "ATTENDED").length} noShow={selectedReservations.filter(item => item.status === "NO_SHOW").length} /><div className="instructor-roster-filters"><Field><FieldLabel htmlFor="roster-member" className="sr-only">회원 이름</FieldLabel><Input id="roster-member" placeholder="회원 이름 검색" value={memberSearch} onChange={event => setMemberSearch(event.target.value)} /></Field>
        <InstructorSelect id="roster-status" label="예약·출결 상태" value={rosterStatus} onChange={setRosterStatus} options={[{ value: "all", label: "전체 상태" }, ...(mode === "attendance" ? ["RESERVED", "ATTENDED", "NO_SHOW"] : ["RESERVED", "ATTENDED", "NO_SHOW", "CANCELED"]).map(value => ({ value, label: statusName(value as DashboardReservation["status"]) }))]} />
      </div></div>
      <InstructorTable key={`${selected.id}-${memberSearch}-${rosterStatus}`} rows={shownRoster} label={`${selected.title} 예약자 목록`} emptyTitle="표시할 예약자가 없습니다" columns={[
        { key: "number", label: "번호", cell: row => roster.indexOf(row) + 1 },
        { key: "member", label: "회원", value: row => row.member, cell: row => <strong>{row.member}</strong> },
        { key: "pass", label: "이용권", cell: row => reservationPassNames[row.id] ?? "이용권 정보 없음" },
        { key: "time", label: "시간", cell: () => seoulTime(selected.startAt) },
        ...(mode === "attendance" ? [{ key: "action", label: "처리", cell: (row: DashboardReservation) => <AttendanceActions reservation={row} restriction={attendanceRestriction(data, user, row.id, dashboardPreviewTime)} onRecord={onRecord} /> }] : []),
        { key: "detail", label: "상세 보기", cell: row => mode === "attendees" ? <Button variant="outline" size="sm" onClick={() => setDetail(row)}>예약 상세</Button> : <span className="attendance-detail"><MemberInfoButton reservation={row} lesson={selected} label="회원정보 보기" /></span> },
        { key: "status", label: mode === "attendees" ? "예약·출결 상태" : "출결 상태", cell: row => (mode === "attendees" || row.status !== "RESERVED") ? <StatusBadge domain="reservation" status={row.status} /> : null },
      ]} />
      {mode === "attendees" && <div className="instructor-bottom-actions"><Button onClick={() => onNavigate("attendance", selected.id)}>이 수업 출결 처리</Button></div>}
      {mode === "attendance" && <><div className="menu-results"><h2>정정 이력</h2><span>{visibleHistory.length}건</span></div><InstructorTable rows={visibleHistory} label="출결 정정 이력" emptyTitle="아직 정정한 출결이 없습니다" columns={[
        { key: "number", label: "번호", cell: row => visibleHistory.indexOf(row) + 1 },
        { key: "member", label: "회원", value: row => data.reservations.find(item => item.id === row.reservationId)?.member ?? "", cell: row => data.reservations.find(item => item.id === row.reservationId)?.member },
        { key: "before", label: "변경 전", cell: row => <StatusBadge domain="reservation" status={row.before} /> },
        { key: "after", label: "변경 후", cell: row => <StatusBadge domain="reservation" status={row.after} /> },
        { key: "reason", label: "정정 사유", cell: row => <span className="instructor-wrap">{row.reason}</span> },
        { key: "at", label: "처리 일시", value: row => row.at, cell: row => `${seoulDate(row.at)} ${seoulTime(row.at)}` },
      ]} /></>}
    </section>}
    {detail && detailsLesson && <Dialog dialog={{ title: "예약 상세", body: `${detail.member}님의 수업 예약 정보입니다.` }} onClose={() => setDetail(null)}><div className="instructor-dialog-stack"><div className="instructor-detail-heading"><h2>{detail.member}</h2><StatusBadge domain="reservation" status={detail.status} /></div><dl className="menu-facts">{[["예약 번호", `#${detail.id}`], ["수업", detailsLesson.title], ["수업일", seoulDate(detailsLesson.startAt)], ["시간", `${seoulTime(detailsLesson.startAt)}–${seoulTime(detailsLesson.endAt)}`], ["룸", detailsLesson.room], ["이용권", reservationPassNames[detail.id] ?? "정보 없음"]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl></div></Dialog>}
  </div>;
}
