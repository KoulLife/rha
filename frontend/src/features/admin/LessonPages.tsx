import { LessonReservationSummary } from '@/components/ui/ReservationSummary';
import { currentTime } from '@/api/runtime';
import { useAllInstructors, useAllLessonTypes, useAllLessons, useAllRooms } from './queries';
import { PAGE_SIZE } from '@/components/ui/Pagination';
import { useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { toast } from 'sonner';
import * as api from '@/api/generated/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { MenuPanel } from '@/components/layout/MenuPanel';
import { Dialog } from '@/components/ui/Dialog';
import { Field, FieldLabel } from '@/components/ui/field';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { LessonSelectionCard } from '@/features/timetable/LessonSelectionCard';
import { LessonInfoCard } from '@/features/timetable/LessonInfoCard';
import { dateInSeoul, shiftDate, weekRange, weekStart } from '@/features/timetable/model';
import '@/features/timetable/timetable.css';
import { AdminPage, AdminForm, DataTable, QueryState, Filters, Facts, ActionDialog, ALL, options, filterValue, dateTime, dayStart, dayEnd, toLocalInput, toUTC, textValue, numValue, errorMessage, type Values, type Action, type FormField } from './AdminUI';
import type { Navigate } from './MemberPages';
const lessonStates=[ALL,{value:'CREATED',label:'개설'},{value:'PUBLISHED',label:'공개'},{value:'UNPUBLISHED',label:'비공개'},{value:'SCHEDULED',label:'공개 예정'},{value:'CANCELED',label:'취소'}];
export function LessonsPage({onNavigate,embedded=false}:{onNavigate:Navigate;embedded?:boolean}) {
  const today=dateInSeoul();
  const [date,setDate]=useState(today),[filters,setFilters]=useState<Values>({}),[selectedId,setSelectedId]=useState<number|null>(null);
  const start=weekStart(date),range=weekRange(start);
  const instructors=useAllInstructors(),types=useAllLessonTypes(),rooms=useAllRooms();
  const query=useAllLessons({...range,instructorId:filterValue(filters,'instructorId')?Number(filters.instructorId):undefined,lessonTypeId:filterValue(filters,'lessonTypeId')?Number(filters.lessonTypeId):undefined,roomId:filterValue(filters,'roomId')?Number(filters.roomId):undefined,status:filterValue(filters,'status') as api.LessonStatus|undefined,order:'ASC'});
  const lessons=query.data?.content??[];
  const selected=lessons.find(lesson=>lesson.id===selectedId);
  const changeDate=(nextDate:string)=>{setDate(nextDate);setSelectedId(null);};
  const updateSidebarFilter=(name:'status'|'lessonTypeId'|'roomId'|'instructorId',value:string)=>{setFilters(previous=>({...previous,[name]:value}));setSelectedId(null);};
  const content=<>
    <div className="timetable-toolbar timetable-main-toolbar admin-lesson-list-toolbar" data-view="list">
      <div className="timetable-main-controls">
        <div className="menu-actions"><Field className="timetable-month"><FieldLabel htmlFor="admin-lessons-month" className="sr-only">조회 월</FieldLabel><Input id="admin-lessons-month" type="month" value={date.slice(0,7)} min="1900-01" max="9998-12" onChange={event=>{if(event.target.value&&event.target.validity.valid)changeDate(`${event.target.value}-01`);}} /></Field><p className="timetable-period" aria-live="polite">{start.slice(5).replace('-','.')} – {shiftDate(start,6).slice(5).replace('-','.')}</p></div>
        <div className="timetable-main-navigation">
          <Field className="admin-lesson-instructor-filter"><FieldLabel htmlFor="admin-lesson-instructor" className="sr-only">강사</FieldLabel><Select value={String(filters.instructorId??'ALL')} onValueChange={value=>updateSidebarFilter('instructorId',value)}><SelectTrigger id="admin-lesson-instructor"><SelectValue placeholder="전체 강사" /></SelectTrigger><SelectContent><SelectItem value="ALL">전체 강사</SelectItem>{options(instructors.data?.content).map(item=><SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>)}</SelectContent></Select></Field>
          <div className="timetable-week-nav" role="group" aria-label="주 이동"><Button variant="ghost" aria-label="이전 주" title="이전 주" onClick={()=>changeDate(shiftDate(start,-7))}><ChevronLeft aria-hidden="true" /></Button><Button variant="ghost" onClick={()=>changeDate(today)}>이번주</Button><Button variant="ghost" aria-label="다음 주" title="다음 주" onClick={()=>changeDate(shiftDate(start,7))}><ChevronRight aria-hidden="true" /></Button></div>
        </div>
      </div>
      <div className="menu-actions timetable-edit-actions"><Button onClick={()=>onNavigate('admin-lesson-create')}>수업 개설</Button></div>
    </div>
    <div className="timetable-layout admin-lesson-list-layout" data-view="list">
      <div className="timetable-list-panel"><QueryState query={query}><div className="admin-table-area"><DataTable rows={lessons} paginate={false} selectedId={selectedId} onRowClick={lesson=>setSelectedId(lesson.id)} empty="이번 주에 표시할 수업이 없습니다" title="이번 주 수업" titleAction={<span className="timetable-list-count">{lessons.length}건</span>} columns={[{label:'수업',render:r=><strong>{r.title}</strong>},{label:'시작',render:r=>dateTime(r.startAt)},{label:'유형',render:r=>r.lessonType.name},{label:'강사·룸',render:r=>`${r.instructor.name} · ${r.room.name}`},{label:'예약 / 정원',render:r=>`${r.reservedCount} / ${r.capacity}명`},{label:'상태',render:r=><StatusBadge domain="lesson" status={r.status}/>}]}/></div></QueryState></div>
      <aside className="timetable-preview" aria-label="선택한 수업 요약" aria-live="polite">
        <div className="timetable-toolbar timetable-filters"><div className="menu-actions">
          <Field><FieldLabel htmlFor="admin-lesson-status" className="sr-only">수업 상태</FieldLabel><Select value={String(filters.status??'ALL')} onValueChange={value=>updateSidebarFilter('status',value)}><SelectTrigger id="admin-lesson-status"><SelectValue placeholder="전체 수업 상태" /></SelectTrigger><SelectContent>{lessonStates.map(item=><SelectItem key={item.value} value={item.value}>{item.value==='ALL'?'전체 수업 상태':item.label}</SelectItem>)}</SelectContent></Select></Field>
          <Field><FieldLabel htmlFor="admin-lesson-type" className="sr-only">수업 유형</FieldLabel><Select value={String(filters.lessonTypeId??'ALL')} onValueChange={value=>updateSidebarFilter('lessonTypeId',value)}><SelectTrigger id="admin-lesson-type"><SelectValue placeholder="전체 수업 유형" /></SelectTrigger><SelectContent><SelectItem value="ALL">전체 수업 유형</SelectItem>{options(types.data?.content).map(item=><SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>)}</SelectContent></Select></Field>
          <Field><FieldLabel htmlFor="admin-lesson-room" className="sr-only">룸</FieldLabel><Select value={String(filters.roomId??'ALL')} onValueChange={value=>updateSidebarFilter('roomId',value)}><SelectTrigger id="admin-lesson-room"><SelectValue placeholder="전체 룸" /></SelectTrigger><SelectContent><SelectItem value="ALL">전체 룸</SelectItem>{options(rooms.data?.content).map(item=><SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>)}</SelectContent></Select></Field>
        </div></div>
        <LessonSelectionCard lesson={selected}>{selected&&<Button onClick={()=>onNavigate('admin-lesson-detail',selected.id)}>수업 상세 보기</Button>}</LessonSelectionCard>
      </aside>
    </div>
    {selectedId!==null&&<section className="admin-selected-lesson-roster" aria-label="선택한 수업의 예약자와 출결"><ReservationsList key={selectedId} lessonId={selectedId} onNavigate={onNavigate} compact/></section>}
  </>;
  return embedded?<section className="menu-page lesson-admin-list">{content}</section>:<AdminPage title="수업 관리" description="수업 회차와 공개 일정, 예약·출결을 관리합니다.">{content}</AdminPage>;
}
export function LessonDetail({id,onNavigate}:{id:number;onNavigate:Navigate}) {
  const query=api.useGetLesson(id),status=api.useChangeLessonStatus(),cancel=api.useCancelLesson();
  const [action,setAction]=useState<Action|null>(null); const l=query.data;
  const editable=!!l&&Date.parse(l.startAt)>Date.parse(currentTime())&&l.status!=='CANCELED';
  return <AdminPage title={l?.title??'수업 상세'} description="수업 정보와 예약자·출결을 확인합니다." action={<Button variant="ghost" onClick={()=>onNavigate('admin-lessons')}>목록으로</Button>}><QueryState query={query}>{l&&<>
    <LessonInfoCard lesson={l} action={editable&&<div className="menu-actions"><Button variant="outline" onClick={()=>onNavigate('admin-lesson-edit',id)}>수업 수정</Button><Button variant="outline" onClick={()=>setAction({title:'공개 상태 설정',description:'비공개로 변경해도 기존 예약은 유지됩니다.',submitLabel:'공개 상태 저장',fields:[{name:'status',label:'공개 상태',type:'select',required:true,options:l.status==='PUBLISHED'?[{value:'UNPUBLISHED',label:'비공개'}]:[{value:'PUBLISHED',label:'즉시 공개'},{value:'SCHEDULED',label:'공개 예약'},...(l.status==='SCHEDULED'?[{value:'UNPUBLISHED',label:'비공개'}]:[])]},{name:'publishAt',label:'공개 예약 시각',type:'datetime-local',description:'공개 예약을 선택한 경우에만 입력합니다.'}],initial:{status:l.status==='PUBLISHED'?'UNPUBLISHED':'PUBLISHED'},onSubmit:v=>status.mutateAsync({lessonId:id,data:{status:textValue(v,'status') as api.LessonStatusChangeRequestStatus,...(v.status==='SCHEDULED'?{publishAt:toUTC(textValue(v,'publishAt'))}:{})}})})}>공개 설정</Button><Button variant="warning" onClick={()=>setAction({title:'수업 취소',description:`예약 완료 ${l.reservedCount}명의 예약이 함께 취소되고 이용권 횟수가 복구됩니다.`,submitLabel:'수업 취소',submitVariant:'warning',danger:true,fields:[{name:'reason',label:'취소 사유',type:'textarea'}],onSubmit:v=>cancel.mutateAsync({lessonId:id,data:{reason:textValue(v,'reason')}})})}>수업 취소</Button></div>}/>
    <ReservationsList lessonId={id} onNavigate={onNavigate}/></>}</QueryState>{action&&<ActionDialog action={action} onClose={()=>setAction(null)}/>}</AdminPage>;
}
export function LessonEditor({id,onNavigate,initialValues = {},onSaved,onBack}:{id?:number;onNavigate:Navigate;initialValues?:Values;onSaved?:(lesson:api.Lesson)=>void;onBack?:()=>void}) {
  const lesson=api.useGetLesson(id??0,{query:{enabled:!!id}}),types=useAllLessonTypes({active:true,size:100}),instructors=useAllInstructors({size:100}),rooms=useAllRooms({size:100}),create=api.useCreateLesson(),update=api.useUpdateLesson(),changeStatus=api.useChangeLessonStatus();
  const l=lesson.data;
  const fields:FormField[]=[
    {name:'title',label:'수업명',required:true,maxLength:100},
    ...(!id?[{name:'lessonTypeId',label:'수업 유형',type:'select' as const,required:true,options:options(types.data?.content),description:'설정된 정원이 자동으로 적용됩니다.'}]:[]),
    {name:'instructorId',label:'담당 강사',type:'search',required:true,options:options(instructors.data?.content)},
    {name:'roomId',label:'룸',type:'select',required:true,options:options(rooms.data?.content)},
    {name:'startAt',label:'시작 시각 (서울)',type:'datetime-local',required:true,disabled:!!l?.reservedCount},
    {name:'endAt',label:'종료 시각 (서울)',type:'datetime-local',required:true,disabled:!!l?.reservedCount},
    ...(!id?[{name:'publication',label:'개설 후 공개 상태',type:'select' as const,required:true,options:[{value:'PRIVATE',label:'비공개로 유지'},{value:'PUBLISHED',label:'즉시 공개'},{value:'SCHEDULED',label:'공개 예약'}],description:'개설 직후 선택한 공개 상태를 적용합니다.'},{name:'publishAt',label:'공개 예약 시각 (서울)',type:'datetime-local' as const,required:true,showWhen:{name:'publication',value:'SCHEDULED'},description:'수업 시작 전의 미래 시각을 선택해 주세요.'}]:[]),
  ];
  const form=<MenuPanel title={id?'수업 정보 수정':'수업 정보'} className="admin-full-form admin-lesson-form-panel"><AdminForm key={l?.id??'new'} fields={fields} initial={l?{title:l.title,instructorId:String(l.instructor.id),roomId:String(l.room.id),startAt:toLocalInput(l.startAt),endAt:toLocalInput(l.endAt)}:{publication:'PRIVATE',...initialValues}} submitLabel={id?'수업 수정':'수업 개설'} successToast={false} onCancel={()=>onBack ? onBack() : onNavigate(id?'admin-lesson-detail':'admin-lessons',id)} onSubmit={async v=>{
    const data={title:textValue(v,'title').trim(),instructorId:numValue(v,'instructorId'),roomId:numValue(v,'roomId'),...(!l?.reservedCount?{startAt:toUTC(textValue(v,'startAt')),endAt:toUTC(textValue(v,'endAt'))}:{})};
    if(data.startAt&&data.endAt&&data.endAt<=data.startAt)throw new Error('종료 시각은 시작 이후여야 합니다.');
    const publication=textValue(v,'publication');
    if(!id&&publication==='SCHEDULED'&&!textValue(v,'publishAt'))throw new Error('공개 예약 시각을 입력해 주세요.');
    const publishAt=publication==='SCHEDULED'?toUTC(textValue(v,'publishAt')):undefined;
    if(!id&&publishAt&&(publishAt<=new Date().toISOString()||publishAt>=data.startAt!))throw new Error('공개 예약 시각은 현재 이후, 수업 시작 이전이어야 합니다.');
    if(id){const updated=await update.mutateAsync({lessonId:id,data});toast.success('수업을 수정했습니다.');if(onSaved)onSaved(updated);else onNavigate('admin-lesson-detail',updated.id);return;}
    const created=await create.mutateAsync({data:{...data,startAt:data.startAt!,endAt:data.endAt!,lessonTypeId:numValue(v,'lessonTypeId')}});
    let result=created;
    if(publication==='PUBLISHED'||publication==='SCHEDULED'){
      try{result=await changeStatus.mutateAsync({lessonId:created.id,data:{status:publication,...(publishAt?{publishAt}:{})}});}
      catch(error){toast.error(`수업은 비공개로 개설됐지만 공개 설정을 적용하지 못했습니다. ${errorMessage(error)}`);onNavigate('admin-lesson-detail',created.id);return;}
    }
    toast.success('수업을 개설했습니다.');
    if(onSaved)onSaved(result);else onNavigate('admin-lesson-detail',result.id);
  }}>{l?.reservedCount?<p className="menu-note">예약이 있는 수업은 시간을 변경할 수 없습니다.</p>:!id?<p className="menu-note">비공개·즉시 공개·공개 예약 중 선택할 수 있습니다. 개설 후에도 상세 화면에서 공개 상태를 변경할 수 있습니다.</p>:null}</AdminForm></MenuPanel>;
  return <AdminPage title={id?'수업 수정':'수업 개설'} description={id?'수업 정보와 시간·담당 강사를 조정합니다.':'수업 정보와 공개 상태를 설정합니다. 센터 영업시간과 강사 가능시간을 확인해 주세요.'} action={<Button variant="ghost" onClick={()=>onBack ? onBack() : onNavigate('admin-lessons')}>{onBack?'시간표로':'목록으로'}</Button>}>{id?<QueryState query={lesson}>{l&&form}</QueryState>:<QueryState query={types}><QueryState query={instructors}><QueryState query={rooms}>{form}</QueryState></QueryState></QueryState>}</AdminPage>;
}
function MemberInfoDialog({memberId,onClose}:{memberId:number;onClose:()=>void}) {
  const query=api.useGetMember(memberId);
  const member=query.data;
  return <Dialog dialog={{title:'회원 정보',body:'예약 회원의 계정 정보를 확인합니다.'}} onClose={onClose}>
    <QueryState query={query}>{member&&<Facts items={[["이름",member.name],["계정 상태",<StatusBadge domain="member" status={member.status}/>],["이메일",member.email],["휴대전화",member.phoneNumber],["생년월일",member.birthDate],["주소",member.address],["등록 시각",dateTime(member.createdAt)]]}/>}</QueryState>
  </Dialog>;
}
export function ReservationsList({memberId,lessonId,onNavigate,compact=false}:{memberId?:number;lessonId?:number;onNavigate:Navigate;compact?:boolean}) {
  const [filters,setFilters]=useState<Values>({}),[page,setPage]=useState(1),[action,setAction]=useState<Action|null>(null),[selected,setSelected]=useState<api.Reservation|null>(null),[selectedMemberId,setSelectedMemberId]=useState<number|null>(null);
  const params={status:filterValue(filters,'status') as api.ReservationStatus|undefined,page,size:PAGE_SIZE};
  const all=api.useGetReservations({...params,memberId,lessonFrom:dayStart(textValue(filters,'from')),lessonTo:dayEnd(textValue(filters,'to'))},{query:{enabled:!lessonId}}),roster=api.useGetLessonReservations(lessonId??0,params,{query:{enabled:!!lessonId}}),cancel=api.useCancelReservation(),attendance=api.useConfirmAttendance();
  const query=lessonId?roster:all;
  return <div className={lessonId?'menu-stack admin-lesson-roster':'menu-stack'}>{!lessonId&&<Filters fields={[{name:'from',label:'시작일',type:'date' as const},{name:'to',label:'종료일',type:'date' as const},{name:'status',label:'예약 상태',type:'select',options:[ALL,{value:'RESERVED',label:'예약 완료'},{value:'ATTENDED',label:'출석 완료'},{value:'NO_SHOW',label:'노쇼'},{value:'CANCELED',label:'취소'}]}]} onApply={v=>{setFilters(v);setPage(1);}}/>}<QueryState query={query}><DataTable rows={query.data?.content??[]} pageInfo={compact && lessonId && (query.data?.pageInfo.totalElements ?? 0) <= PAGE_SIZE ? undefined : query.data?.pageInfo} onPage={setPage} title={lessonId ? '예약자' : undefined} titleAction={lessonId ? <LessonReservationSummary lessonId={lessonId} /> : undefined} columns={[{label:'회원',render:r=><Button variant="ghost" onClick={()=>onNavigate('admin-member-detail',r.member.id)}>{r.member.name}</Button>},...(lessonId?[{label:'회원 조회',render:(r:api.Reservation)=><Button variant="outline" onClick={()=>setSelectedMemberId(r.member.id)}>회원 정보</Button>}]:[]),{label:'수업',render:r=>r.lesson.title},{label:'시작',render:r=>dateTime(r.lesson.startAt)},{label:'상태',render:r=><StatusBadge domain="reservation" status={r.status}/>},{label:'처리',render:r=><div className="menu-actions"><Button variant="ghost" onClick={()=>setSelected(r)}>예약 상세</Button>{r.status==='RESERVED'&&Date.parse(r.lesson.startAt)>Date.parse(currentTime())&&<Button variant="warning" onClick={()=>setAction({title:'예약 취소',description:`${r.member.name}님의 예약을 취소하고 이용권 1회를 복구합니다.`,submitLabel:'예약 취소',submitVariant:'warning',danger:true,fields:[{name:'reason',label:'취소 사유',type:'textarea'}],onSubmit:v=>cancel.mutateAsync({reservationId:r.id,data:{reason:textValue(v,'reason')}})})}>취소</Button>}{r.status!=='CANCELED'&&Date.parse(r.lesson.startAt)<=Date.parse(currentTime())&&<Button variant="outline" onClick={()=>setAction({title:r.status==='RESERVED'?'출결 기록':'출결 정정',description:'예약 시 이미 차감된 이용권 횟수는 출결 기록으로 추가 차감되지 않습니다.',submitLabel:'출결 저장',fields:[{name:'status',label:'출결',type:'select',required:true,options:[{value:'ATTENDED',label:'출석'},{value:'NO_SHOW',label:'노쇼'}]}],initial:{status:r.status==='NO_SHOW'?'NO_SHOW':'ATTENDED'},onSubmit:v=>attendance.mutateAsync({reservationId:r.id,data:{status:textValue(v,'status') as api.AttendanceRequestStatus}})})}>{r.status==='RESERVED'?'출결 기록':'출결 정정'}</Button>}</div>}]}/></QueryState>{action&&<ActionDialog action={action} onClose={()=>setAction(null)}/>} {selected&&<Dialog dialog={{title:'예약 상세',body:`${selected.member.name}님의 예약 정보`}} onClose={()=>setSelected(null)}><Facts items={[["회원",selected.member.name],["수업",selected.lesson.title],["수업 시간",dateTime(selected.lesson.startAt)],["이용권",selected.pass.passProduct.name],["상태",<StatusBadge domain="reservation" status={selected.status}/>],["취소 사유",selected.cancelReason],["출결 기록",dateTime(selected.attendanceConfirmedAt)]]}/></Dialog>}{selectedMemberId!==null&&<MemberInfoDialog memberId={selectedMemberId} onClose={()=>setSelectedMemberId(null)}/>}</div>;
}
