import * as api from '@/api/generated/api';
import type { Screen } from '@/app/routes';
import type { DashboardData } from '@/features/dashboard/model';
import { MembersPage,MemberDetail,type Navigate } from './MemberPages';
import { AbsencesPage,AbsenceDetail,ResignationsPage } from './InstructorPages';
import { LessonsPage,LessonDetail,LessonEditor } from './LessonPages';
import { PassesPage,PassDetail,PaymentsPage } from './PassPages';
import { SettingsPage,ProductsPage,LessonTypesPage,RoomsPage,HoursPage,PolicyPage } from './SettingsPages';
import { SettlementsPage,SettlementCreate,SettlementDetail } from './SettlementPages';
import { ActivityPage } from './ActivityPages';
import { StatisticsPage, SalesStatisticsPage } from './StatisticsPage';

export function useAdminDashboard(enabled:boolean) {
  const lessons=api.useGetLessons({from:'2026-10-12T15:00:00Z',to:'2026-10-13T15:00:00Z',size:100},{query:{enabled}});
  const reservations=api.useGetReservations({lessonFrom:'2026-10-12T15:00:00Z',lessonTo:'2026-10-13T15:00:00Z',size:100},{query:{enabled}});
  const absences=api.useGetAbsences({status:'REQUESTED',size:100},{query:{enabled}});
  const data:DashboardData={lessons:(lessons.data?.content??[]).map(l=>({id:l.id,title:l.title,instructorId:l.instructor.id,instructor:l.instructor.name,room:l.room.name,startAt:l.startAt,endAt:l.endAt,capacity:l.capacity,status:l.status})),reservations:(reservations.data?.content??[]).map(r=>({id:r.id,lessonId:r.lesson.id,memberId:r.member.id,member:r.member.name,status:r.status})),absences:(absences.data?.content??[]).map(a=>({id:a.id,instructorId:a.instructor.id,instructor:a.instructor.name,from:a.startAt.slice(0,10),to:a.endAt.slice(0,10),reason:a.reason??'',status:a.status})),notifications:[]};
  const queries=[lessons,reservations,absences];
  return { data,isPending:enabled&&queries.some(q=>q.isPending),isError:queries.some(q=>q.isError),error:queries.find(q=>q.isError)?.error,refetch:()=>Promise.all(queries.map(q=>q.refetch())) };
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
