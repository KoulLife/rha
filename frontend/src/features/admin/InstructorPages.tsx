import { PAGE_SIZE, Pagination } from '@/components/ui/Pagination';
import { useState } from 'react';
import * as api from '@/api/generated/api';
import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { Badge } from '@/components/ui/badge';
import { Field, FieldLabel } from '@/components/ui/field';
import { MenuPanel, MenuEmpty } from '@/components/layout/MenuPanel';
import { Dialog } from '@/components/ui/Dialog';
import { AdminPage, AdminLinks, DataTable, QueryState, Filters, Facts, ActionDialog, SearchChoice, ALL, options, filterValue, dateTime, dayStart, dayEnd, toLocalInput, toUTC, textValue, numValue, type Values, type Action } from './AdminUI';
import type { Navigate } from './MemberPages';

const resignationActionStyle = {
  width: 'var(--table-action-width)',
  minWidth: 'var(--table-action-width)',
  height: 'var(--table-action-height)',
  minHeight: 'var(--table-action-height)',
  paddingInline: 'var(--space-control)',
  fontSize: 'var(--font-size-caption)',
} as const;

export function AbsenceList({ instructorId, onNavigate }: { instructorId?:number; onNavigate:Navigate }) {
  const [filters,setFilters]=useState<Values>({}),[page,setPage]=useState(1);
  const instructors=api.useGetMembers({role:'INSTRUCTOR',size:100});
  const query=api.useGetAbsences({instructorId:instructorId??(filterValue(filters,'instructorId')?Number(filters.instructorId):undefined),status:filterValue(filters,'status') as api.AbsenceStatus|undefined,from:dayStart(textValue(filters,'from')),to:dayEnd(textValue(filters,'to')),page,size:PAGE_SIZE});
  return <div className="menu-stack"><Filters fields={[{name:'from',label:'시작일',type:'date'},{name:'to',label:'종료일',type:'date'},...(!instructorId?[{name:'instructorId',label:'강사',type:'search' as const,options:[ALL,...options(instructors.data?.content)]}]:[]),{name:'status',label:'상태',type:'select',options:[ALL,{value:'REQUESTED',label:'승인 대기'},{value:'APPROVED',label:'승인'},{value:'REJECTED',label:'반려'},{value:'CANCELED',label:'취소'}]}]} onApply={v=>{setFilters(v);setPage(1);}}/><QueryState query={query}><DataTable rows={query.data?.content??[]} pageInfo={query.data?.pageInfo} onPage={setPage} columns={[{label:'강사',render:r=>r.instructor.name},{label:'부재 시작',render:r=>dateTime(r.startAt)},{label:'부재 종료',render:r=>dateTime(r.endAt)},{label:'사유',render:r=>r.reason??'—'},{label:'상태',render:r=><StatusBadge domain="absence" status={r.status}/>},{label:'상세',render:r=><Button variant="ghost" onClick={()=>onNavigate('admin-absence-detail',r.id)}>상세 보기</Button>}]}/></QueryState></div>;
}
export function AbsencesPage({onNavigate}:{onNavigate:Navigate}) { return <AdminPage title="부재 관리" description="강사의 신청 내용을 확인하고 수업을 조정한 뒤 승인합니다." action={<Button variant="ghost" onClick={()=>onNavigate('admin-instructors')}>강사 목록으로</Button>}><AbsenceList onNavigate={onNavigate}/></AdminPage>; }
export function AbsenceDetail({id,onNavigate}:{id:number;onNavigate:Navigate}) {
  const query=api.useGetAbsence(id), approve=api.useApproveAbsence(), reject=api.useRejectAbsence(), cancel=api.useCancelAbsence();
  const instructors=api.useGetInstructors({size:100}),rooms=api.useGetRooms({size:100}),updateLesson=api.useUpdateLesson();
  const [action,setAction]=useState<Action|null>(null),[page,setPage]=useState(1);
  const a=query.data;
  // No lower start bound: a class that starts before the absence may still overlap.
  const lessons=api.useGetLessons({instructorId:a?.instructor.id,to:a?.endAt,page,size:PAGE_SIZE},{query:{enabled:!!a}});
  const rows=(lessons.data?.content??[]).filter(l=>a&&l.endAt>a.startAt&&l.status!=='CANCELED');
  const openLessonAdjustment=(lesson:api.Lesson)=>setAction({
    title:`${lesson.title} · 수업 조정`,description:'겹치는 수업의 담당 강사·룸 또는 수업 시간을 조정합니다.',submitLabel:'수업 조정 저장',
    fields:[{name:'title',label:'수업명',required:true,maxLength:100},{name:'instructorId',label:'담당 강사',type:'search',required:true,options:options(instructors.data?.content)},{name:'roomId',label:'룸',type:'select',required:true,options:options(rooms.data?.content)},{name:'startAt',label:'시작 시각 (서울)',type:'datetime-local',required:true,disabled:!!lesson.reservedCount},{name:'endAt',label:'종료 시각 (서울)',type:'datetime-local',required:true,disabled:!!lesson.reservedCount}],
    initial:{title:lesson.title,instructorId:String(lesson.instructor.id),roomId:String(lesson.room.id),startAt:toLocalInput(lesson.startAt),endAt:toLocalInput(lesson.endAt)},
    content:<><Facts items={[["수업 유형",lesson.lessonType.name],["기존 시간",`${dateTime(lesson.startAt)} ~ ${dateTime(lesson.endAt)}`],["예약 인원",`${lesson.reservedCount}명`]]}/>{!!lesson.reservedCount&&<p className="menu-note">예약이 있는 수업은 시간을 변경할 수 없습니다. 담당 강사나 룸을 조정해 주세요.</p>}</>,
    onSubmit:async v=>{const startAt=lesson.reservedCount?undefined:toUTC(textValue(v,'startAt')),endAt=lesson.reservedCount?undefined:toUTC(textValue(v,'endAt'));if(startAt&&endAt&&endAt<=startAt)throw new Error('종료 시각은 시작 이후여야 합니다.');await updateLesson.mutateAsync({lessonId:lesson.id,data:{title:textValue(v,'title').trim(),instructorId:numValue(v,'instructorId'),roomId:numValue(v,'roomId'),...(startAt&&endAt?{startAt,endAt}:{})}});}
  });
  return <AdminPage title="부재 상세" description="승인 전 겹치는 수업과 예약을 확인해 주세요." action={<Button variant="ghost" onClick={()=>onNavigate('admin-absences')}>목록으로</Button>}><QueryState query={query}>{a&&<>
    <MenuPanel title={a.instructor.name} className="admin-absence-detail-card"><Facts items={[["기간",`${dateTime(a.startAt)} ~ ${dateTime(a.endAt)}`],["신청 사유",a.reason],["상태",<StatusBadge domain="absence" status={a.status}/>],["처리자",a.processedBy?.name],["처리 시각",dateTime(a.processedAt)],["반려 사유",a.rejectReason]]}/><div className="menu-actions admin-absence-actions">{a.status==='REQUESTED'&&<><Button onClick={()=>setAction({title:'부재 승인',description:'예약이 있는 수업은 먼저 인계 또는 취소해야 합니다. 예약 없는 수업은 승인과 함께 취소됩니다.',submitLabel:'부재 승인',danger:true,onSubmit:()=>approve.mutateAsync({absenceId:id})})}>부재 승인</Button><Button variant="destructive" onClick={()=>setAction({title:'부재 반려',description:'반려하면 신청이 거절 상태로 종료됩니다. 반려 사유는 신청한 강사에게 표시됩니다.',submitLabel:'부재 반려',submitVariant:'destructive',danger:true,fields:[{name:'reason',label:'반려 사유',type:'textarea'}],onSubmit:v=>reject.mutateAsync({absenceId:id,data:{reason:textValue(v,'reason')}})})}>부재 반려</Button></>}{a.status==='APPROVED'&&<Button variant="warning" onClick={()=>setAction({title:'승인된 부재 취소',description:'부재 승인을 취소해 담당 강사의 배정 제한을 해제합니다. 승인과 함께 취소된 수업은 복구되지 않습니다.',submitLabel:'부재 취소',submitVariant:'warning',danger:true,onSubmit:()=>cancel.mutateAsync({absenceId:id})})}>부재 취소</Button>}</div></MenuPanel>
    <h2>겹치는 수업</h2><QueryState query={lessons}><DataTable rows={rows} pageInfo={lessons.data?.pageInfo} onPage={setPage} empty="이 페이지에 겹치는 수업이 없습니다" columns={[{label:'수업',render:l=>l.title},{label:'시작',render:l=>dateTime(l.startAt)},{label:'예약 인원',render:l=>`${l.reservedCount}명`},{label:'조정',render:l=><Button variant="outline" onClick={()=>openLessonAdjustment(l)} disabled={!instructors.data||!rooms.data}>수업 조정</Button>}]}/></QueryState>
  </>}</QueryState>{action&&<ActionDialog action={action} onClose={()=>setAction(null)}/>}</AdminPage>;
}
export function ResignationsPage({onNavigate}:{onNavigate:Navigate}) {
  const [page,setPage]=useState(1),[action,setAction]=useState<Action|null>(null);
  const [historyInstructor,setHistoryInstructor]=useState<{id:number;name:string}|null>(null);
  const [handoffInstructor,setHandoffInstructor]=useState<api.Member|null>(null),[handoffRecipient,setHandoffRecipient]=useState('');
  const query=api.useGetMembers({role:'INSTRUCTOR',page,size:PAGE_SIZE}), candidates=api.useGetInstructors({size:100});
  const register=api.useRegisterResignation(),cancel=api.useCancelResignation(),complete=api.useCompleteResignation(),reassign=api.useReassignLessons();
  return <AdminPage title="퇴사 관리" description="퇴사일 등록, 시작 전 수업 인계, 퇴사 처리와 정산을 진행합니다." action={<Button variant="ghost" onClick={()=>onNavigate('admin-instructors')}>강사 목록으로</Button>}>
    <p className="menu-note">수업 인계는 즉시 미래 수업의 담당 강사를 변경합니다. 담당 이용권은 자동으로 변경되지 않으므로 회원 이용권에서 별도로 조정해 주세요.</p>
    <QueryState query={query}><DataTable rows={query.data?.content??[]} pageInfo={query.data?.pageInfo} onPage={setPage} columns={[
      {label:'강사',render:r=>r.name},
      {label:'입사일 (등록일)',render:r=>dateTime(r.createdAt)},
      {label:'상태',render:r=><StatusBadge domain="member" status={r.status}/>},
      {label:'퇴사일',render:r=>r.resignationDate??''},
      {label:'수업 인계 현황',render:r=>r.resignationDate?<InstructorHandoffStatus resigned={r.status==='RESIGNED'}/>:null},
      {label:'처리',render:r=><div className="menu-actions">
        <DropdownMenu>
          <DropdownMenuTrigger asChild><Button variant="outline" style={resignationActionStyle} aria-label={`${r.name} 퇴사 관리`}>관리</Button></DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="admin-resignation-menu">
            {r.status==='ACTIVE'&&<>
            <DropdownMenuItem onSelect={()=>setAction({title:'퇴사일 등록',description:'퇴사일 당일부터 새 수업 배정이 제한됩니다. 계정은 퇴사 처리 전까지 유지됩니다.',submitLabel:'퇴사일 저장',fields:[{name:'resignationDate',label:'퇴사일',type:'date',required:true,min:'2026-10-13'}],initial:{resignationDate:r.resignationDate??'2026-10-13'},onSubmit:v=>register.mutateAsync({memberId:r.id,data:{resignationDate:textValue(v,'resignationDate')}})})}>퇴사일 {r.resignationDate?'변경':'등록'}</DropdownMenuItem>
            <DropdownMenuItem disabled={!candidates.data} onSelect={()=>setAction({title:'미래 수업 인계',description:'이후의 시작 전 수업을 모두 인계합니다. 담당 이용권은 유지됩니다.',submitLabel:'수업 인계',danger:true,fields:[{name:'toInstructorId',label:'인계받을 강사',type:'search',required:true,options:options(candidates.data?.content.filter(m=>m.id!==r.id))}],onSubmit:v=>reassign.mutateAsync({memberId:r.id,data:{toInstructorId:numValue(v,'toInstructorId')}})})}>수업 인계</DropdownMenuItem>
            {r.resignationDate&&<>
              <DropdownMenuItem onSelect={()=>{setHandoffInstructor(r);setHandoffRecipient('');}}>인계 대상 등록</DropdownMenuItem>
              <DropdownMenuSeparator/>
              <DropdownMenuItem className="admin-resignation-warning" onSelect={()=>setAction({title:'퇴사일 취소',description:'등록된 퇴사일을 제거합니다.',submitLabel:'퇴사일 취소',submitVariant:'warning',danger:true,onSubmit:()=>cancel.mutateAsync({memberId:r.id})})}>퇴사일 취소</DropdownMenuItem>
              <DropdownMenuItem variant="destructive" onSelect={()=>setAction({title:'퇴사 처리',description:'퇴사일 도래 및 시작 전 수업이 없어야 합니다. 계정 로그인과 권한이 해제되고 대기·미래 부재가 취소됩니다.',submitLabel:'퇴사 처리',submitVariant:'destructive',danger:true,onSubmit:()=>complete.mutateAsync({memberId:r.id})})}>퇴사 처리</DropdownMenuItem>
            </>}
            <DropdownMenuSeparator/>
            </>}
            <DropdownMenuItem onSelect={()=>setHistoryInstructor({id:r.id,name:r.name})}>수업 인계 정보</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
        <Button variant="ghost" style={resignationActionStyle} onClick={()=>onNavigate('admin-instructor-settlements',r.id)}>정산 보기</Button>
      </div>}
    ]}/></QueryState>
    {action&&<ActionDialog action={action} onClose={()=>setAction(null)}/>}
    {handoffInstructor&&<Dialog dialog={{title:`${handoffInstructor.name} · 수업 인계 대상 등록`,body:'퇴사 예정 강사의 수업을 인계받을 강사를 지정합니다.'}} onClose={()=>setHandoffInstructor(null)}><div className="menu-stack">
      <Field><FieldLabel htmlFor="handoff-recipient">인계받을 강사</FieldLabel><SearchChoice id="handoff-recipient" value={handoffRecipient} onChange={setHandoffRecipient} choices={options(candidates.data?.content.filter(member=>member.id!==handoffInstructor.id))} disabled={!candidates.data}/></Field>
      <p className="menu-note">기능 적용 필요 · 현재 수업 인계 기능은 대상을 미리 등록하지 않고 수업 담당 강사를 즉시 변경합니다.</p>
      {/* TODO(api): 퇴사 예정 강사의 인계 대상 조회·저장 계약이 추가되면 선택한 강사를 저장하고 상태 CHIP을 갱신한다. */}
      <div className="menu-actions"><Button disabled>인계 대상 저장 · 기능 적용 필요</Button></div>
    </div></Dialog>}
    {historyInstructor&&<Dialog dialog={{title:`${historyInstructor.name} · 수업 인계 정보`,body:'인계받은 강사와 수업별 변경 내역'}} onClose={()=>setHistoryInstructor(null)}><ReassignmentHistory instructorId={historyInstructor.id}/></Dialog>}
  </AdminPage>;
}

function InstructorHandoffStatus({resigned}:{resigned:boolean}) {
  if (resigned) return <Badge variant="outline" className="status-badge-destructive">퇴사 완료</Badge>;
  return <Badge variant="outline" className="status-badge-neutral">기능 적용 필요</Badge>;
}

function ReassignmentHistory({instructorId}:{instructorId:number}) {
  const [page,setPage]=useState(1);
  const query=api.useGetActivityLogs({targetType:'MEMBER',targetId:instructorId,action:'INSTRUCTOR_LESSONS_REASSIGNED',page,size:PAGE_SIZE});
  return <QueryState query={query}>{query.data?.content.length ? <div className="menu-stack">
    {query.data.content.map(log=><details className="admin-detail-section" key={log.id}>
      <summary>{dateTime(log.occurredAt)} · {log.summary}</summary>
      <ul className="admin-reassignment-lessons">{log.changes.map((change,index)=><li key={index}><strong>{change.field}</strong><span>{change.before} → {change.after}</span></li>)}</ul>
    </details>)}
    <Pagination total={query.data.pageInfo.totalElements} page={query.data.pageInfo.page} totalPages={query.data.pageInfo.totalPages} onPageChange={setPage}/>
  </div> : <MenuEmpty title="수업 인계 기록이 없습니다" description="이 강사의 수업을 인계하면 이곳에 내역이 표시됩니다."/>}</QueryState>;
}
