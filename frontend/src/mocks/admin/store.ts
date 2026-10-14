import type { Member, Lesson, LessonType, Room, PassProduct, Pass, PassPayment, PassPause, Reservation, Absence, SettlementDetail, ActivityLog, Notification, BusinessHour, ReservationPolicy, PayRate, AvailabilitySlot } from '../../api/generated/api';

export const previewNow = '2026-10-13T03:00:00Z';
export const previewToday = '2026-10-13';
export const days = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] as const;
export const addDays = (date: string, count: number) => new Date(Date.parse(`${date}T00:00:00Z`) + count * 86400000).toISOString().slice(0, 10);
export const localDate = (iso: string) => new Date(Date.parse(iso) + 9 * 3600000).toISOString().slice(0, 10);
const createdAt = '2026-10-01T01:00:00Z';
const person = (id: number, name: string, role: Member['role'] = 'MEMBER'): Member => ({ id, name, email: `user${id}@rha.example`, role, status: 'ACTIVE', permissions: [], phoneNumber: `0101234${String(id).padStart(4, '0')}`, birthDate: '1992-05-20', address: '서울특별시 마포구', createdAt, resignationDate: null });

export function createAdminStore() {
  const members: Member[] = [person(1, '홍길동'), person(2, '김강사', 'INSTRUCTOR'), person(3, '센터 관리자', 'ADMIN'), person(4, '박강사', 'INSTRUCTOR'), ...['이지은', '최서연', '정민지', '한유진', '윤지수', '조수빈', '장하늘', '임서현', '송다은', '문예린', '오지민'].map((name, i) => person(i + 5, name)), { ...person(20, '이강사', 'INSTRUCTOR'), status: 'RESIGNED', resignationDate: '2026-09-30' }];
  members[1].permissions = ['MEMBER_READ', 'LESSON_CREATE:GROUP_5', 'LESSON_UPDATE:GROUP_5'];
  const lessonTypes: LessonType[] = [{ id: 1, code: 'GROUP_5', name: '그룹 5:1', capacity: 5, active: true, createdAt }, { id: 2, code: 'PRIVATE', name: '개인 레슨', capacity: 1, active: true, createdAt }];
  const rooms: Room[] = [{ id: 1, name: '룸 A', capacity: 5, createdAt }, { id: 2, name: '룸 B', capacity: 5, createdAt }];
  const products: PassProduct[] = [{ id: 1, name: '필라테스 10회권', price: 300000, totalCount: 10, validityDays: 90, description: '그룹 수업 이용권', createdAt }, { id: 2, name: '개인 레슨 20회권', price: 1200000, totalCount: 20, validityDays: 120, description: '개인 수업 이용권', createdAt }];
  const lessons: Lesson[] = [
    [101, '기구 필라테스', 2, 1, '2026-10-13T01:00:00Z', 'PUBLISHED'],
    [102, '리포머 그룹', 4, 2, '2026-10-13T05:00:00Z', 'PUBLISHED'],
    [107, '코어 밸런스', 2, 1, '2026-10-13T09:00:00Z', 'PUBLISHED'],
    [103, '개인 레슨', 2, 1, '2026-10-14T10:00:00Z', 'CREATED'],
    [104, '오전 스트레칭', 2, 1, '2026-10-16T01:00:00Z', 'PUBLISHED'],
    [105, '저녁 리포머', 4, 2, '2026-10-20T09:00:00Z', 'SCHEDULED'],
    [90, '9월 그룹 수업', 2, 1, '2026-09-28T01:00:00Z', 'PUBLISHED'],
    [91, '9월 개인 수업', 20, 2, '2026-09-29T02:00:00Z', 'PUBLISHED'],
  ].map(([id, title, instructorId, roomId, startAt, status]) => ({ id: Number(id), title: String(title), instructor: members.find(m => m.id === instructorId)!, lessonType: lessonTypes[Number(id) === 103 || Number(id) === 91 ? 1 : 0], room: rooms.find(r => r.id === roomId)!, startAt: String(startAt), endAt: new Date(Date.parse(String(startAt)) + 3600000).toISOString(), capacity: Number(id) === 103 || Number(id) === 91 ? 1 : 5, reservedCount: 0, status: status as Lesson['status'], publishAt: status === 'SCHEDULED' ? '2026-10-18T00:00:00Z' : null, createdAt }));
  const passes: Pass[] = members.filter(m => m.role === 'MEMBER').map((member, index) => ({ id: 301 + index, member: { id: member.id, name: member.name }, instructor: { id: 2, name: '김강사' }, passProduct: { id: 1, name: products[0].name }, price: 300000, paidAmount: index === 1 ? 100000 : 300000, unpaidAmount: index === 1 ? 200000 : 0, paymentStatus: index === 1 ? 'PARTIAL' : 'PAID', totalCount: 10, remainingCount: 7, startDate: '2026-10-01', endDate: index === 2 ? '2026-10-20' : '2026-12-29', status: 'ACTIVE', createdAt }));
  const reservations: Reservation[] = [[501,101,1,'ATTENDED'],[502,102,1,'RESERVED'],[503,101,5,'NO_SHOW'],[504,101,6,'RESERVED'],[505,102,5,'RESERVED'],[506,107,6,'RESERVED'],[507,104,7,'RESERVED'],[508,90,1,'ATTENDED'],[509,91,5,'ATTENDED']].map(([id, lessonId, memberId, status]) => ({ id: Number(id), lesson: lessons.find(l => l.id === lessonId)!, member: members.find(m => m.id === memberId)!, pass: { id: passes.find(p => p.member.id === memberId)!.id, passProduct: { id: 1, name: products[0].name }, endDate: '2026-12-29' }, status: status as Reservation['status'], createdAt }));
  lessons.forEach(l => { l.reservedCount = reservations.filter(r => r.lesson.id === l.id && r.status !== 'CANCELED').length; });
  const payments: PassPayment[] = passes.map((p, i) => ({ id: 401 + i, method: 'CARD', amount: p.paidAmount, paidAt: '2026-10-01', refunded: false, member: p.member, pass: { id: p.id, passProduct: p.passProduct, endDate: p.endDate }, recordedBy: { id: 3, name: '센터 관리자' }, createdAt }));
  const absences: Absence[] = [{ id: 201, instructor: { id: 2, name: '김강사' }, startAt: '2026-10-16T00:00:00Z', endAt: '2026-10-16T12:00:00Z', reason: '개인 일정', status: 'REQUESTED', createdAt }, { id: 202, instructor: { id: 4, name: '박강사' }, startAt: '2026-10-20T00:00:00Z', endAt: '2026-10-21T12:00:00Z', reason: '교육 참석', status: 'REQUESTED', createdAt }];
  const notifications: Notification[] = absences.map((a, i) => ({ id: 21 + i, title: '새로운 강사 부재 신청이 있습니다.', message: `${a.instructor.name}님의 부재 신청을 확인해 주세요.`, type: 'ABSENCE_REQUESTED', relatedType: 'ABSENCE', relatedId: a.id, read: false, createdAt: previewNow }));
  const logs: ActivityLog[] = [{ id: 1, occurredAt: createdAt, category: 'PASS', action: 'PASS_ISSUED', actionName: '이용권 발급', actor: { id: 3, name: '센터 관리자', role: 'ADMIN' }, subjectMember: passes[0].member, targetType: 'PASS', targetId: 301, summary: '홍길동님에게 필라테스 10회권 발급', changes: [{ field: 'remainingCount', before: null, after: '10' }] }];
  return { members, lessonTypes, rooms, products, lessons, passes, reservations, payments, absences, notifications, logs, pauses: {} as Record<number, PassPause[]>, settlements: [] as SettlementDetail[], payRates: { 2: lessonTypes.map(lessonType => ({ lessonType, unitPrice: 35000 })), 4: lessonTypes.map(lessonType => ({ lessonType, unitPrice: 40000 })), 20: lessonTypes.map(lessonType => ({ lessonType, unitPrice: 45000 })) } as Record<number, PayRate[]>, availability: Object.fromEntries([2,4].map(id => [id, days.map(dayOfWeek => ({ dayOfWeek, startTime: '09:00', endTime: '21:00' }))])) as Record<number, AvailabilitySlot[]>, businessHours: days.map(dayOfWeek => ({ dayOfWeek, closed: false, openTime: '09:00', closeTime: '21:00' })) as BusinessHour[], policy: { cancelDeadlineHours: 24, allowUnpaidReservation: true } as ReservationPolicy, sequence: 1000 };
}
export type AdminStore = ReturnType<typeof createAdminStore>;
export const store = createAdminStore();
