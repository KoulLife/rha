import * as api from '@/api/generated/api';
import { Button } from '@/components/ui/button';
import { PageTitle } from '@/components/ui/PageTitle';
import { QueryState } from '@/features/admin/AdminUI';
import { LessonInfoCard } from '@/features/timetable/LessonInfoCard';
import type { DashboardUser } from '@/features/dashboard/model';
import type { AttendanceChange } from './model';
import { InstructorLessonRoster } from './InstructorLessonRoster';

export function InstructorLessonDetail({ lessonId, user, canViewReservations, changes, onRecorded, onBack }: {
  lessonId: number;
  user: DashboardUser;
  canViewReservations: boolean;
  changes: AttendanceChange[];
  onRecorded: (reservation: api.Reservation, status: api.AttendanceRequestStatus, reason: string) => void;
  onBack: () => void;
}) {
  const lesson = api.useGetLesson(lessonId);
  return <section className="menu-page instructor-page">
    <PageTitle title={lesson.data?.title ?? '수업 상세'} description="수업 정보와 예약자·출결을 확인합니다." action={<Button variant="ghost" onClick={onBack}>수업으로</Button>} />
    <QueryState query={lesson}>{lesson.data && <>
      <LessonInfoCard lesson={lesson.data} />
      <InstructorLessonRoster lessonId={lessonId} user={user} canViewReservations={canViewReservations} changes={changes} onRecorded={onRecorded} />
    </>}</QueryState>
  </section>;
}
