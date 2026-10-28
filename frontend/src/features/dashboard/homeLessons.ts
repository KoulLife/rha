// 회원 홈의 수업 목록. 서버는 시작한 수업의 예약을 거절하므로(LESSON_NOT_RESERVABLE) 화면도 시작 시각으로 판단한다.
export const HOME_LESSON_LIMIT = 6;

type HomeLesson = { startAt: string; reservedCount: number; capacity: number };

export function homeLessonStatus(lesson: HomeLesson, now: number) {
  return Date.parse(lesson.startAt) <= now || lesson.reservedCount >= lesson.capacity
    ? "CLOSED"
    : "AVAILABLE";
}

export function upcomingLessons<T extends Pick<HomeLesson, "startAt">>(lessons: T[], now: number) {
  return lessons
    .filter((lesson) => Date.parse(lesson.startAt) > now)
    .sort((a, b) => Date.parse(a.startAt) - Date.parse(b.startAt))
    .slice(0, HOME_LESSON_LIMIT);
}
