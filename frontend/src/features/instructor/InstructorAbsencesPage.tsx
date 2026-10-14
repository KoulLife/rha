import { useState } from "react";
import { useForm } from "react-hook-form";
import { MenuPanel } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/Dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { PageTitle } from "@/components/ui/PageTitle";
import { StatusBadge, getStatusLabel } from "@/components/ui/StatusBadge";
import { seoulDate, seoulTime, type DashboardAbsence, type DashboardData, type DashboardUser } from "../dashboard/model";
import { dashboardPreviewTime } from "../dashboard/preview";
import { absenceRestriction, canWithdrawAbsence, type AbsenceDetail, type InstructorState } from "./model";
import { InstructorTable } from "./InstructorTable";
import { InstructorConfirm, InstructorSelect } from "./InstructorControls";

const initialFilters = { search: "", from: "", to: "", status: "all" };
const fullTime = (value: string) => `${seoulDate(value)} ${seoulTime(value)}`;
type Props = { user: DashboardUser; data: DashboardData; state: InstructorState; onCreate: (detail: AbsenceDetail, reason: string) => void; onWithdraw: (id: number) => void };

export function InstructorAbsencesPage({ user, data, state, onCreate, onWithdraw }: Props) {
  const [draft, setDraft] = useState(initialFilters);
  const [filters, setFilters] = useState(initialFilters);
  const [creating, setCreating] = useState(false);
  const [detail, setDetail] = useState<DashboardAbsence | null>(null);
  const [withdrawing, setWithdrawing] = useState<DashboardAbsence | null>(null);
  const own = data.absences.filter(item => item.instructorId === user.id);
  const rows = own.filter(item => (!filters.from || item.to >= filters.from) && (!filters.to || item.from <= filters.to) && (filters.status === "all" || item.status === filters.status) && item.reason.includes(filters.search.trim())).sort((a, b) => (state.absenceDetails[b.id]?.createdAt ?? "").localeCompare(state.absenceDetails[a.id]?.createdAt ?? "") || b.id - a.id);
  const selectedDetail = detail && state.absenceDetails[detail.id];
  return <div className="menu-page instructor-page">
    <PageTitle title="부재 신청" description="부재 일정을 신청하고 승인 결과를 확인하세요." action={<Button onClick={() => setCreating(true)}>부재 신청하기</Button>} />
    <MenuPanel variant="filter"><form className="menu-filters" onSubmit={event => { event.preventDefault(); setFilters(draft); }}>
      <Field><FieldLabel htmlFor="absence-search">신청 사유</FieldLabel><Input id="absence-search" placeholder="사유 검색" value={draft.search} onChange={event => setDraft({ ...draft, search: event.target.value })} /></Field>
      <Field><FieldLabel htmlFor="absence-from">시작일</FieldLabel><Input id="absence-from" type="date" max={draft.to || undefined} value={draft.from} onChange={event => setDraft({ ...draft, from: event.target.value })} /></Field>
      <Field><FieldLabel htmlFor="absence-to">종료일</FieldLabel><Input id="absence-to" type="date" min={draft.from || undefined} value={draft.to} onChange={event => setDraft({ ...draft, to: event.target.value })} /></Field>
      <InstructorSelect id="absence-status" label="처리 상태" value={draft.status} onChange={status => setDraft({ ...draft, status })} options={[{ value: "all", label: "전체 상태" }, ...(["REQUESTED", "APPROVED", "REJECTED", "CANCELED"] as const).map(status => ({ value: status, label: getStatusLabel({ domain: "absence", status }) }))]} />
      <div className="menu-actions"><Button type="submit" variant="outline">조회</Button><Button type="button" variant="outline" onClick={() => { setDraft(initialFilters); setFilters(initialFilters); }}>초기화</Button></div>
    </form></MenuPanel>
    <div className="menu-results"><strong>신청 내역 {rows.length}건</strong><span>승인 대기 {own.filter(item => item.status === "REQUESTED").length}건</span></div>
    <InstructorTable key={JSON.stringify(filters)} rows={rows} label="내 부재 신청 목록" emptyTitle="부재 신청 내역이 없습니다" columns={[
      { key: "number", label: "번호", cell: row => rows.indexOf(row) + 1 },
      { key: "from", label: "시작 일시", value: row => state.absenceDetails[row.id]?.startAt ?? row.from, cell: row => state.absenceDetails[row.id] ? fullTime(state.absenceDetails[row.id].startAt) : row.from },
      { key: "to", label: "종료 일시", value: row => state.absenceDetails[row.id]?.endAt ?? row.to, cell: row => state.absenceDetails[row.id] ? fullTime(state.absenceDetails[row.id].endAt) : row.to },
      { key: "reason", label: "신청 사유", cell: row => <span className="instructor-wrap">{row.reason || "사유 없음"}</span> },
      { key: "created", label: "신청일", value: row => state.absenceDetails[row.id]?.createdAt ?? "", cell: row => state.absenceDetails[row.id] ? seoulDate(state.absenceDetails[row.id].createdAt) : "—" },
      { key: "status", label: "상태", cell: row => <StatusBadge domain="absence" status={row.status} /> },
      { key: "actions", label: "관리", cell: row => <div className="menu-actions"><Button variant="outline" size="sm" aria-label={`${row.from} 부재 상세 보기`} onClick={() => setDetail(row)}>상세 보기</Button>{canWithdrawAbsence(row, user) && <Button variant="destructive" size="sm" onClick={() => setWithdrawing(row)}>신청 철회</Button>}</div> },
    ]} />
    {creating && <AbsenceForm user={user} data={data} state={state} onClose={() => setCreating(false)} onSubmit={(values, reason) => { onCreate(values, reason); setCreating(false); setDraft(initialFilters); setFilters(initialFilters); }} />}
    {detail && <Dialog dialog={{ title: "부재 신청 상세", body: "신청 기간과 처리 결과를 확인하세요." }} onClose={() => setDetail(null)}><div className="instructor-dialog-stack"><div className="instructor-detail-heading"><h2>{user.name}</h2><StatusBadge domain="absence" status={detail.status} /></div>
      <dl className="menu-facts">{[["시작 일시", selectedDetail ? fullTime(selectedDetail.startAt) : detail.from], ["종료 일시", selectedDetail ? fullTime(selectedDetail.endAt) : detail.to], ["신청 사유", detail.reason || "사유 없음"], ["신청 일시", selectedDetail ? fullTime(selectedDetail.createdAt) : "—"], ...(selectedDetail?.processedAt ? [["처리 일시", fullTime(selectedDetail.processedAt)]] : [])].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      {selectedDetail?.rejectionReason && <p className="menu-note">반려 사유: {selectedDetail.rejectionReason}</p>}
      {detail.status === "APPROVED" && <p className="menu-note">승인된 부재의 변경·취소는 관리자에게 문의해 주세요.</p>}
    </div></Dialog>}
    {withdrawing && <InstructorConfirm title="부재 신청 철회" description={`${withdrawing.from} 부재 신청을 철회할까요?\n철회하면 관리자 승인 대상에서 제외됩니다. 다시 신청하려면 새 신청을 등록해 주세요.`} action="신청 철회" destructive onClose={() => setWithdrawing(null)} onConfirm={() => { onWithdraw(withdrawing.id); setWithdrawing(null); }} />}
  </div>;
}

function AbsenceForm({ user, data, state, onClose, onSubmit }: Omit<Props, "onCreate" | "onWithdraw"> & { onClose: () => void; onSubmit: (detail: AbsenceDetail, reason: string) => void }) {
  const { register, handleSubmit, setError, formState: { errors } } = useForm<{ start: string; end: string; reason: string }>();
  return <Dialog dialog={{ title: "부재 신청", body: "부재 기간과 사유를 입력해 주세요.", action: "부재 신청" }} formId="absence-form" onClose={onClose}>
    <form id="absence-form" className="instructor-dialog-stack" onSubmit={handleSubmit(values => {
      const start = new Date(`${values.start}:00+09:00`), end = new Date(`${values.end}:00+09:00`);
      if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime())) { setError("end", { message: "올바른 일시를 입력해 주세요." }); return; }
      const error = absenceRestriction(data.absences, state.absenceDetails, user.id, start.toISOString(), end.toISOString(), dashboardPreviewTime);
      if (error) { setError("end", { message: error }); return; }
      onSubmit({ startAt: start.toISOString(), endAt: end.toISOString(), createdAt: dashboardPreviewTime }, values.reason.trim());
    })}>
      <Field><FieldLabel htmlFor="absence-start-at">시작 일시</FieldLabel><Input id="absence-start-at" type="datetime-local" aria-invalid={!!errors.start} aria-describedby={errors.start ? "absence-start-error" : undefined} {...register("start", { required: "시작 일시를 입력해 주세요." })} /><FieldError id="absence-start-error" errors={[errors.start]} /></Field>
      <Field><FieldLabel htmlFor="absence-end-at">종료 일시</FieldLabel><Input id="absence-end-at" type="datetime-local" aria-invalid={!!errors.end} aria-describedby={errors.end ? "absence-end-error" : undefined} {...register("end", { required: "종료 일시를 입력해 주세요." })} /><FieldError id="absence-end-error" errors={[errors.end]} /></Field>
      <Field><FieldLabel htmlFor="absence-reason">신청 사유 (선택)</FieldLabel><Textarea id="absence-reason" placeholder="부재 사유를 입력해 주세요" maxLength={500} {...register("reason", { maxLength: 500 })} /></Field>
      <p className="menu-note">서울 시간 기준으로 신청합니다. 승인 대기 중인 신청만 철회할 수 있습니다.</p>
    </form>
  </Dialog>;
}
