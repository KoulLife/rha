import { useQueryClient } from '@tanstack/react-query';
import * as api from '@/api/generated/api';
import { Button } from '@/components/ui/button';
import type { Screen } from '@/app/routes';
import type { DashboardData } from '@/features/dashboard/model';
import { MembersPage,MemberDetail,type Navigate } from './MemberPages';
import { AbsencesPage,AbsenceDetail,ResignationsPage } from './InstructorPages';
import { LessonsPage,LessonDetail,LessonEditor } from './LessonPages';
import { PassesPage,PassDetail,PaymentsPage } from './PassPages';
import { SettingsPage,ProductsPage,LessonTypesPage,RoomsPage,HoursPage,PolicyPage } from './SettingsPages';
import { SettlementsPage,SettlementCreate,SettlementDetail } from './SettlementPages';
import { ActivityPage } from './ActivityPages';
import { AdminPage,PendingFeature } from './AdminUI';
import { StatisticsPage, SalesStatisticsPage } from './StatisticsPage';

export function useAdminDashboard(enabled:boolean) {
  const lessons=api.useGetLessons({from:'2026-10-12T15:00:00Z',to:'2026-10-13T15:00:00Z',size:100},{query:{enabled}});
  const reservations=api.useGetReservations({lessonFrom:'2026-10-12T15:00:00Z',lessonTo:'2026-10-13T15:00:00Z',size:100},{query:{enabled}});
  const absences=api.useGetAbsences({status:'REQUESTED',size:100},{query:{enabled}});
  const notifications=api.useGetMyNotifications({size:100},{query:{enabled}});
  const unread=api.useGetMyUnreadNotificationCount({query:{enabled}});
  const read=api.useReadNotification(),client=useQueryClient();
  const data:DashboardData={lessons:(lessons.data?.content??[]).map(l=>({id:l.id,title:l.title,instructorId:l.instructor.id,instructor:l.instructor.name,room:l.room.name,startAt:l.startAt,endAt:l.endAt,capacity:l.capacity,status:l.status})),reservations:(reservations.data?.content??[]).map(r=>({id:r.id,lessonId:r.lesson.id,memberId:r.member.id,member:r.member.name,status:r.status})),absences:(absences.data?.content??[]).map(a=>({id:a.id,instructorId:a.instructor.id,instructor:a.instructor.name,from:a.startAt.slice(0,10),to:a.endAt.slice(0,10),reason:a.reason??'',status:a.status})),notifications:(notifications.data?.content??[]).map(n=>({...n,recipientId:3,category:n.type==='ABSENCE_REQUESTED'?'부재 신청':'안내'}))};
  const queries=[lessons,reservations,absences,notifications];
  return { data,unread:unread.data?.unreadCount??0,isPending:enabled&&queries.some(q=>q.isPending),isError:queries.some(q=>q.isError),error:queries.find(q=>q.isError)?.error,refetch:()=>Promise.all(queries.map(q=>q.refetch())),read:async(id:number)=>{await read.mutateAsync({notificationId:id});await client.invalidateQueries();} };
}
export function AdminWorkspace({screen,id,onNavigate}:{screen:Screen;id:number;onNavigate:Navigate}) {
  let content;
  switch(screen) {
    case 'admin-members':content=<MembersPage onNavigate={onNavigate}/>;break;
    case 'admin-member-detail':content=<MemberDetail id={id} onNavigate={onNavigate}/>;break;
    case 'admin-instructors':content=<MembersPage instructor onNavigate={onNavigate}/>;break;
    case 'admin-instructor-detail':content=<MemberDetail instructor id={id} onNavigate={onNavigate}/>;break;
    case 'admin-absences':content=<AbsencesPage onNavigate={onNavigate}/>;break;
    case 'admin-absence-detail':content=<AbsenceDetail id={id} onNavigate={onNavigate}/>;break;
    case 'admin-resignations':content=<ResignationsPage onNavigate={onNavigate}/>;break;
    case 'admin-lessons':content=<LessonsPage onNavigate={onNavigate}/>;break;
    case 'admin-lesson-detail':content=<LessonDetail id={id} onNavigate={onNavigate}/>;break;
    case 'admin-lesson-create':content=<LessonEditor onNavigate={onNavigate}/>;break;
    case 'admin-lesson-edit':content=<LessonEditor id={id} onNavigate={onNavigate}/>;break;
    case 'admin-repeat':content=<AdminPage title="반복 일정" description="반복 회차를 한 번에 생성·수정하는 기능입니다." action={<Button variant="ghost" onClick={()=>onNavigate('admin-lessons')}>수업 관리로</Button>}><PendingFeature title="반복 일정 생성·회차 수정" description="반복 생성과 이번 회차·선택 회차·이후 회차 수정 범위가 검토 중입니다. 현재는 수업 관리에서 개별 회차를 개설·수정할 수 있습니다."/></AdminPage>;break;
    case 'admin-waitlist':content=<AdminPage title="승인 신청 대기" description="예약 신청 승인과 대기자 운영을 위한 메뉴입니다." action={<Button variant="ghost" onClick={()=>onNavigate('admin-lessons')}>수업 관리로</Button>}><PendingFeature title="신청 승인·거절·대기자 자동 승급" description="예약 승인과 대기 순서, 승급 조건이 확정되면 제공됩니다. 현재 운영 기준은 즉시 예약 확정 및 예약 시 1회 차감입니다."/></AdminPage>;break;
    case 'admin-passes':content=<PassesPage onNavigate={onNavigate}/>;break;
    case 'admin-pass-detail':content=<PassDetail id={id} onNavigate={onNavigate}/>;break;
    case 'admin-payments':content=<PaymentsPage onNavigate={onNavigate}/>;break;
    case 'admin-statistics':content=<StatisticsPage/>;break;
    case 'admin-sales-statistics':content=<SalesStatisticsPage/>;break;
    case 'admin-settings':content=<SettingsPage onNavigate={onNavigate}/>;break;
    case 'admin-products':content=<ProductsPage onNavigate={onNavigate}/>;break;
    case 'admin-types':content=<LessonTypesPage onNavigate={onNavigate}/>;break;
    case 'admin-rooms':content=<RoomsPage onNavigate={onNavigate}/>;break;
    case 'admin-hours':content=<HoursPage onNavigate={onNavigate}/>;break;
    case 'admin-policy':content=<PolicyPage onNavigate={onNavigate}/>;break;
    case 'admin-settlements':content=<SettlementsPage onNavigate={onNavigate}/>;break;
    case 'admin-instructor-settlements':content=<SettlementsPage instructorId={id} onNavigate={onNavigate}/>;break;
    case 'admin-settlement-create':content=<SettlementCreate instructorId={id||undefined} onNavigate={onNavigate}/>;break;
    case 'admin-settlement-detail':content=<SettlementDetail id={id} onNavigate={onNavigate}/>;break;
    case 'activity-logs':content=<ActivityPage onNavigate={onNavigate}/>;break;
    default:content=null;
  }
  return content;
}
