import { authPreview } from '../../features/auth/preview';
import { http, HttpResponse, delay } from 'msw';
import type * as API from '../../api/generated/api';
import { store as db, previewNow, previewToday, days, addDays, localDate } from './store';

function canReadLesson(lesson: API.Lesson, user: { id: number; role: string; permissions?: string[] }) {
  if (user.role === 'ADMIN' || lesson.status === 'PUBLISHED') return true;
  return user.role === 'INSTRUCTOR' && (lesson.instructor.id === user.id ||
    (user.permissions ?? []).some(permission => permission === `LESSON_CREATE:${lesson.lessonType.code}` || permission === `LESSON_UPDATE:${lesson.lessonType.code}`));
}

class MockProblem extends Error { constructor(public code: string, message: string, public status = 409) { super(message); } }
const fail = (code: string, message: string, status = 409): never => { throw new MockProblem(code, message, status); };
const find = <T extends { id: number }>(rows: T[], id: number): T => rows.find(r => r.id === id) ?? fail('RESOURCE_NOT_FOUND', '대상을 찾을 수 없습니다.', 404);
const nextId = () => ++db.sequence;
const summary = (m: API.Member) => ({ id: m.id, name: m.name });
const overlaps = (a: { startAt: string; endAt: string }, b: { startAt: string; endAt: string }) => a.startAt < b.endAt && b.startAt < a.endAt;
const futureLessons = (id: number) => db.lessons.filter(l => l.instructor.id === id && l.startAt > previewNow && l.status !== 'CANCELED');
const inSettlement = (id: number) => db.settlements.some(s => s.items.some(i => i.lesson.id === id));
function page<T>(rows: T[], url: URL) {
  const size = Math.min(100, Math.max(1, Number(url.searchParams.get('size') || 10)));
  const page = Math.max(1, Number(url.searchParams.get('page') || 1));
  return { content: rows.slice((page - 1) * size, page * size), pageInfo: { page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) } };
}
function audit(category: string, actionName: string, targetType: string, targetId: number, before: object = {}, after: object = {}, subjectMember?: API.MemberSummary) {
  const sensitive = ['phoneNumber', 'birthDate', 'address', 'email'];
  const a = before as Record<string, unknown>, b = after as Record<string, unknown>;
  db.logs.unshift({ id: nextId(), occurredAt: previewNow, category, action: `${category}_UPDATED`, actionName, actor: { id: 3, name: '센터 관리자', role: 'ADMIN' }, targetType, targetId, subjectMember, summary: `${actionName} · ${subjectMember?.name ?? `#${targetId}`}`, changes: Object.keys(b).filter(k => JSON.stringify(a[k]) !== JSON.stringify(b[k])).map(field => ({ field, before: sensitive.includes(field) ? '변경 전 (비공개)' : a[field] == null ? null : typeof a[field] === 'object' ? JSON.stringify(a[field]) : String(a[field]), after: sensitive.includes(field) ? '변경 후 (비공개)' : b[field] == null ? null : typeof b[field] === 'object' ? JSON.stringify(b[field]) : String(b[field]) })) });
}
function update<T extends object>(row: T, values: Partial<T>, category: string, label: string, id: number, subject?: API.MemberSummary) { audit(category, label, category, id, structuredClone(row), values, subject); Object.assign(row, values); return row; }
function cancelReservation(r: API.Reservation, reason = '', by: API.Reservation['canceledBy'] = 'ADMIN') {
  if (r.status !== 'RESERVED' || r.lesson.startAt <= previewNow) fail('INVALID_RESERVATION_STATUS', '시작 전 예약 완료 상태만 취소할 수 있습니다.');
  update(r, { status: 'CANCELED', canceledAt: previewNow, canceledBy: by, cancelReason: reason }, 'RESERVATION', '예약 취소', r.id, r.member);
  const pass = find(db.passes, r.pass.id); pass.remainingCount++;
  const lesson = find(db.lessons, r.lesson.id); lesson.reservedCount--;
}
function cancelLesson(l: API.Lesson, reason = '') {
  if (l.startAt <= previewNow || l.status === 'CANCELED') fail('LESSON_ALREADY_STARTED', '시작 전 수업만 취소할 수 있습니다.');
  db.reservations.filter(r => r.lesson.id === l.id && r.status === 'RESERVED').forEach(r => cancelReservation(r, reason, 'LESSON_CANCELED'));
  return update(l, { status: 'CANCELED', cancelReason: reason, canceledAt: previewNow, publishAt: null }, 'LESSON', '수업 취소', l.id);
}
function validateLesson(l: API.Lesson, ignoreIds: number[] = []) {
  if (l.startAt <= previewNow || l.endAt <= l.startAt) fail('INVALID_LESSON_TIME', '시작은 기준 시각 이후, 종료는 시작 이후여야 합니다.', 400);
  const instructor = find(db.members, l.instructor.id);
  if (instructor.status !== 'ACTIVE' || (instructor.resignationDate && localDate(l.startAt) >= instructor.resignationDate)) fail('INSTRUCTOR_UNAVAILABLE', '해당 강사에게 배정할 수 없는 날짜입니다.');
  if (db.absences.some(a => a.instructor.id === instructor.id && a.status === 'APPROVED' && overlaps(a, l))) fail('INSTRUCTOR_ABSENT', '강사의 승인된 부재와 겹칩니다.');
  if (db.lessons.some(other => other.id !== l.id && !ignoreIds.includes(other.id) && other.status !== 'CANCELED' && (other.instructor.id === l.instructor.id || other.room.id === l.room.id) && overlaps(other, l))) fail('INSTRUCTOR_TIME_CONFLICT', '강사 또는 룸의 기존 수업과 시간이 겹칩니다.');
  const start = new Date(Date.parse(l.startAt) + 9 * 3600000), end = new Date(Date.parse(l.endAt) + 9 * 3600000);
  const day = days[(start.getUTCDay() + 6) % 7];
  const open = db.businessHours.find(h => h.dayOfWeek === day)!;
  const from = start.toISOString().slice(11,16), to = end.toISOString().slice(11,16);
  if (localDate(l.startAt) !== localDate(l.endAt) || open.closed || from < open.openTime! || to > open.closeTime!) fail('OUTSIDE_BUSINESS_HOURS', '센터 영업시간 내에서 수업을 개설해 주세요.');
  if (!(db.availability[instructor.id] ?? []).some(s => s.dayOfWeek === day && s.startTime <= from && s.endTime >= to)) fail('INSTRUCTOR_UNAVAILABLE', '강사의 수업 가능시간을 확인해 주세요.');
}
function refreshPass(p: API.Pass) {
  p.paidAmount = db.payments.filter(x => x.pass.id === p.id).reduce((sum, x) => sum + x.amount, 0);
  p.unpaidAmount = Math.max(0, p.price - p.paidAmount);
  p.paymentStatus = !p.unpaidAmount ? 'PAID' : !p.paidAmount ? 'UNPAID' : 'PARTIAL';
  p.status = p.endDate < previewToday ? 'EXPIRED' : p.startDate > previewToday ? 'UPCOMING' : (db.pauses[p.id] ?? []).some(x => x.status === 'ACTIVE') ? 'PAUSED' : 'ACTIVE';
}
const permissions = () => [{ code: 'MEMBER_READ', name: '회원 조회', description: '회원 기본 정보 조회' }, ...db.lessonTypes.filter(t => t.active).flatMap(t => [{ code: `LESSON_CREATE:${t.code}`, name: `${t.name} 개설`, description: '수업 개설' }, { code: `LESSON_UPDATE:${t.code}`, name: `${t.name} 수정`, description: '수정·공개·취소' }])];

export const adminHandlers = [http.all('*/v1/*', async ({ request }) => {
  await delay(100);
  const url = new URL(request.url), path = url.pathname.replace(/^\/v1/, ''), parts = path.split('/').filter(Boolean), id = Number(parts[1]), subId = Number(parts[3]);
  const method = request.method, q = url.searchParams;
  const body = (method === 'GET' || method === 'DELETE' ? {} : await request.json().catch(() => ({}))) as Record<string, unknown>;
  const payload = <T>() => body as T;
  const match = (key: string, value: unknown) => !q.has(key) || String(value) === q.get(key);
  const keyword = (value: string) => value.toLocaleLowerCase().includes((q.get('keyword') ?? '').toLocaleLowerCase());
  const range = (value: string, from = 'from', to = 'to') => (!q.get(from) || value >= q.get(from)!) && (!q.get(to) || value < q.get(to)!);
  try {
    let result: unknown;
    if (path === '/members' && method === 'GET') result = page(db.members.filter(m => match('role', m.role) && match('status', m.status) && keyword(`${m.name} ${m.email}`)), url);
    else if (path === '/members' && method === 'POST') {
      const input = payload<API.MemberCreateRequest>();
      if (input.role !== 'ADMIN' && input.role !== 'INSTRUCTOR') fail('INVALID_INPUT', '현재 계약에서는 강사·관리자 계정만 등록할 수 있습니다.', 400);
      if (db.members.some(m => m.email === input.email)) fail('EMAIL_ALREADY_EXISTS', '이미 등록된 이메일입니다.');
      if (!input.name?.trim() || !/^01[0-9]{8,9}$/.test(input.phoneNumber)) fail('INVALID_INPUT', '이름과 휴대전화 번호를 확인해 주세요.', 400);
      const row: API.Member = { ...input, id: nextId(), status: 'ACTIVE', permissions: [], createdAt: previewNow };
      db.members.unshift(row); audit('MEMBER', '계정 등록', 'MEMBER', row.id, {}, { name: row.name, role: row.role }, row); result = row;
    } else if (parts[0] === 'members' && parts.length === 2) {
      const row = find(db.members, id);
      if (method === 'GET') result = row;
      if (method === 'PATCH') { result = update(row, payload<API.MemberAdminUpdateRequest>(), 'MEMBER', '회원 정보 수정', id, row); for (const p of db.passes) { if (p.member.id === id) p.member.name = row.name; if (p.instructor.id === id) p.instructor.name = row.name; } }
    } else if (parts[0] === 'members' && parts[2] === 'status') {
      const row = find(db.members, id); const input = payload<API.MemberStatusChangeRequest>();
      if (input.status === 'INACTIVE' && row.role === 'MEMBER' && db.reservations.some(r => r.member.id === id && r.status === 'RESERVED' && r.lesson.startAt > previewNow)) fail('MEMBER_HAS_UPCOMING_RESERVATIONS', '시작 전 예약을 먼저 취소해 주세요.');
      if (input.status === 'INACTIVE' && row.role === 'INSTRUCTOR' && futureLessons(id).length) fail('INSTRUCTOR_HAS_UPCOMING_LESSONS', '시작 전 담당 수업을 인계하거나 취소해 주세요.');
      result = update(row, { status: input.status, ...(row.status === 'RESIGNED' && input.status === 'ACTIVE' ? { resignationDate: null } : {}) }, 'MEMBER', '계정 상태 변경', id, row);
    } else if (path === '/permissions') result = { permissions: permissions() };
    else if (parts[0] === 'members' && parts[2] === 'permissions') {
      const row = find(db.members, id);
      if (method === 'PUT') { const input = payload<API.MemberPermissionsRequest>(); if (row.status === 'RESIGNED' || input.permissions.some(p => !permissions().some(x => x.code === p))) fail('INVALID_PERMISSION', '부여 가능한 권한인지 확인해 주세요.'); update(row, input, 'PERMISSION', '강사 권한 변경', id, row); }
      result = { memberId: id, permissions: row.permissions };
    } else if (parts[0] === 'members' && parts[2] === 'availability') {
      const row = find(db.members, id);
      if (method === 'PUT') { const input = payload<API.AvailabilityRequest>(); if (input.slots.some(s => s.endTime <= s.startTime)) fail('INVALID_INPUT', '가능시간의 시작과 종료를 확인해 주세요.', 400); db.availability[id] = input.slots; audit('INSTRUCTOR', '수업 가능시간 변경', 'MEMBER', id); }
      result = { instructor: summary(row), slots: db.availability[id] ?? [] };
    } else if (parts[0] === 'members' && parts[2] === 'resignation') {
      const row = find(db.members, id);
      if (parts[3] === 'completions') {
        if (!row.resignationDate || row.resignationDate > previewToday || futureLessons(id).length) fail('RESIGNATION_DATE_NOT_REACHED', '퇴사일이 도래하고 시작 전 담당 수업이 없어야 합니다.');
        result = update(row, { status: 'RESIGNED', permissions: [] }, 'INSTRUCTOR', '퇴사 처리', id, row);
        db.absences.filter(a => a.instructor.id === id && (a.status === 'REQUESTED' || a.startAt > previewNow)).forEach(a => { a.status = 'CANCELED'; });
      } else if (method === 'DELETE') { if (row.status === 'RESIGNED') fail('MEMBER_RESIGNED', '퇴사 완료 후에는 취소할 수 없습니다.'); result = update(row, { resignationDate: null }, 'INSTRUCTOR', '퇴사일 취소', id, row); }
      else { const input = payload<API.ResignationRequest>(); if (row.status !== 'ACTIVE' || input.resignationDate < previewToday) fail('INVALID_INPUT', '활성 강사의 오늘 이후 날짜를 입력해 주세요.', 400); result = update(row, input, 'INSTRUCTOR', '퇴사일 등록', id, row); }
    } else if (parts[0] === 'members' && parts[2] === 'lesson-reassignments') {
      const input = payload<API.LessonReassignmentRequest>(), source = find(db.members, id), target = find(db.members, input.toInstructorId);
      if (id === target.id || target.role !== 'INSTRUCTOR') fail('INVALID_INPUT', '다른 강사를 선택해 주세요.', 400);
      const lessons = futureLessons(id).filter(l => l.startAt >= (input.from ?? previewNow));
      lessons.forEach(l => validateLesson({ ...l, instructor: summary(target) }, lessons.map(l => l.id)));
      lessons.forEach(l => update(l, { instructor: summary(target) }, 'LESSON', '수업 인계', l.id));
      if (lessons.length) db.logs.unshift({ id: nextId(), occurredAt: previewNow, category: 'INSTRUCTOR', action: 'INSTRUCTOR_LESSONS_REASSIGNED', actionName: '수업 인계', actor: { id: 3, name: '센터 관리자', role: 'ADMIN' }, targetType: 'MEMBER', targetId: id, subjectMember: summary(source), summary: `${source.name} → ${target.name} · ${lessons.length}개 수업`, changes: lessons.map(l => ({ field: `${l.title} · ${new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(l.startAt))}`, before: source.name, after: target.name })) });
      result = { reassignedCount: lessons.length };
    } else if (path === '/instructors') result = page(db.members.filter(m => m.role === 'INSTRUCTOR' && m.status === 'ACTIVE').map(summary), url);
    else if (path === '/absences' && method === 'GET') result = page(db.absences.filter(a => match('instructorId', a.instructor.id) && match('status', a.status) && (!q.get('from') || a.endAt > q.get('from')!) && (!q.get('to') || a.startAt < q.get('to')!)), url);
    else if (parts[0] === 'absences' && id) {
      const row = find(db.absences, id);
      if (parts.length === 2 && method === 'GET') result = row;
      else {
        if (row.status !== 'REQUESTED' && !(parts[2] === 'cancellations' && row.status === 'APPROVED')) fail('INVALID_ABSENCE_STATUS', '현재 상태에서는 처리할 수 없습니다.');
        if (parts[2] === 'approvals') {
          const affected = db.lessons.filter(l => l.instructor.id === row.instructor.id && l.status !== 'CANCELED' && overlaps(l, row));
          if (affected.some(l => l.reservedCount > 0)) fail('ABSENCE_HAS_RESERVATIONS', '예약이 있는 수업을 다른 강사에게 인계하거나 취소한 뒤 승인해 주세요.');
          affected.forEach(l => cancelLesson(l, '강사 부재 승인'));
        }
        result = update(row, { status: parts[2] === 'approvals' ? 'APPROVED' : parts[2] === 'rejections' ? 'REJECTED' : 'CANCELED', rejectReason: payload<API.AbsenceRejectRequest>().reason, processedAt: previewNow, processedBy: { id: 3, name: '센터 관리자' } }, 'ABSENCE', parts[2] === 'approvals' ? '부재 승인' : parts[2] === 'rejections' ? '부재 반려' : '부재 취소', id);
      }
    } else if (path === '/lessons' && method === 'GET') {
      const session = authPreview.session;
      if (!session) fail('UNAUTHORIZED', '로그인이 필요합니다.', 401);
      const viewer = { ...session!, permissions: db.members.find(m => m.id === session!.id)?.permissions ?? [] };
      const rows = db.lessons.filter(l => canReadLesson(l, viewer) && match('instructorId', l.instructor.id) && match('roomId', l.room.id) && match('lessonTypeId', l.lessonType.id) && match('status', l.status) && range(l.startAt));
      rows.sort((a,b) => a.startAt.localeCompare(b.startAt) * (q.get('order') === 'DESC' ? -1 : 1)); result = page(rows, url);
    } else if (path === '/lessons' && method === 'POST') {
      const input = payload<API.LessonCreateRequest>(), type = find(db.lessonTypes, input.lessonTypeId);
      if (!type.active) fail('LESSON_TYPE_INACTIVE', '활성 수업 유형을 선택해 주세요.');
      const row: API.Lesson = { id: nextId(), title: input.title, lessonType: { ...type }, instructor: summary(find(db.members, input.instructorId)), room: { ...find(db.rooms, input.roomId) }, startAt: input.startAt, endAt: input.endAt, capacity: type.capacity, reservedCount: 0, status: 'CREATED', createdAt: previewNow };
      validateLesson(row); db.lessons.push(row); audit('LESSON', '수업 개설', 'LESSON', row.id, {}, row); result = row;
    } else if (parts[0] === 'lessons' && id) {
      const row = find(db.lessons, id);
      if (method === 'GET') {
        const session = authPreview.session;
        if (!session) fail('UNAUTHORIZED', '로그인이 필요합니다.', 401);
        const viewer = { ...session!, permissions: db.members.find(m => m.id === session!.id)?.permissions ?? [] };
        if (!canReadLesson(row, viewer) || (parts[2] === 'reservations' && viewer.role !== 'ADMIN' && !(viewer.role === 'INSTRUCTOR' && row.instructor.id === viewer.id))) fail('FORBIDDEN', '조회 권한이 없습니다.', 403);
      }
      if (parts[2] === 'reservations') result = page(db.reservations.filter(r => r.lesson.id === id && match('status', r.status)), url);
      else if (parts[2] === 'cancellations') result = cancelLesson(row, payload<API.LessonCancelRequest>().reason);
      else if (parts[2] === 'status') {
        const input = payload<API.LessonStatusChangeRequest>();
        const allowed: Record<string, string[]> = { CREATED: ['PUBLISHED','SCHEDULED'], SCHEDULED: ['PUBLISHED','UNPUBLISHED','SCHEDULED'], PUBLISHED: ['UNPUBLISHED'], UNPUBLISHED: ['PUBLISHED','SCHEDULED'], CANCELED: [] };
        if (row.startAt <= previewNow || !allowed[row.status].includes(input.status)) fail('INVALID_STATUS_TRANSITION', '현재 상태에서는 공개 설정을 변경할 수 없습니다.');
        if (input.status === 'SCHEDULED' && (!input.publishAt || input.publishAt <= previewNow || input.publishAt >= row.startAt)) fail('INVALID_PUBLISH_AT', '공개 예약은 현재 이후, 수업 시작 이전이어야 합니다.', 400);
        result = update(row, { status: input.status, publishAt: input.status === 'SCHEDULED' ? input.publishAt : null }, 'LESSON', '수업 공개 설정', id);
      } else if (method === 'PATCH') {
        const input = payload<API.LessonUpdateRequest>();
        if (row.status === 'CANCELED' || row.startAt <= previewNow) fail('LESSON_ALREADY_STARTED', '시작 전 취소되지 않은 수업만 수정할 수 있습니다.');
        if (row.reservedCount && ((input.startAt && input.startAt !== row.startAt) || (input.endAt && input.endAt !== row.endAt))) fail('LESSON_HAS_RESERVATIONS', '예약이 있는 수업의 시간은 변경할 수 없습니다.');
        const candidate = { ...row, ...input, instructor: input.instructorId ? summary(find(db.members, input.instructorId)) : row.instructor, room: input.roomId ? find(db.rooms, input.roomId) : row.room };
        validateLesson(candidate); result = update(row, candidate, 'LESSON', '수업 수정', id);
      } else result = row;
    } else if (path === '/members/me/reservations' && method === 'GET') {
      const session = authPreview.session;
      if (!session) fail('UNAUTHORIZED', '로그인이 필요합니다.', 401);
      const memberId = session!.id;
      result = page(db.reservations.filter(r => r.member.id === memberId && match('status', r.status) && range(r.lesson.startAt, 'lessonFrom', 'lessonTo')), url);
    } else if (path === '/reservations' && method === 'POST') {
      const session = authPreview.session;
      if (!session) fail('UNAUTHORIZED', '로그인이 필요합니다.', 401);
      const memberId = session!.id;
      if (session!.role !== 'MEMBER') fail('FORBIDDEN', '회원만 수업을 신청할 수 있습니다.', 403);
      const lesson = find(db.lessons, payload<API.ReservationCreateRequest>().lessonId);
      if (lesson.status !== 'PUBLISHED' || lesson.startAt <= previewNow) fail('LESSON_NOT_BOOKABLE', '신청할 수 있는 수업이 아닙니다.');
      if (lesson.reservedCount >= lesson.capacity) fail('LESSON_FULL', '정원이 마감된 수업입니다.');
      if (db.reservations.some(r => r.member.id === memberId && r.lesson.id === lesson.id && r.status === 'RESERVED')) fail('RESERVATION_ALREADY_EXISTS', '이미 신청한 수업입니다.');
      const member = find(db.members, memberId);
      const pass = db.passes.filter(p => p.member.id === memberId).map(p => { refreshPass(p); return p; }).find(p => p.status === 'ACTIVE' && p.remainingCount > 0 && p.startDate <= localDate(lesson.startAt) && p.endDate >= localDate(lesson.startAt) && (db.policy.allowUnpaidReservation || p.unpaidAmount === 0));
      const usablePass = pass ?? fail('USABLE_PASS_NOT_FOUND', '수업 일자에 사용할 수 있는 이용권이 없습니다.');
      usablePass.remainingCount--;
      lesson.reservedCount++;
      const row: API.Reservation = { id: nextId(), lesson: structuredClone(lesson), member: summary(member), pass: { id: usablePass.id, passProduct: { id: usablePass.passProduct.id, name: usablePass.passProduct.name }, endDate: usablePass.endDate }, status: 'RESERVED', createdAt: previewNow };
      db.reservations.push(row); audit('RESERVATION', '수업 예약', 'RESERVATION', row.id, {}, row, summary(member)); result = row;
    } else if (path === '/reservations' && method === 'GET') result = page(db.reservations.filter(r => match('memberId', r.member.id) && match('lessonId', r.lesson.id) && match('status', r.status) && range(r.lesson.startAt, 'lessonFrom', 'lessonTo')), url);
    else if (parts[0] === 'reservations' && id) {
      const row = find(db.reservations, id);
      if (parts[2] === 'cancellations') { cancelReservation(row, payload<API.ReservationCancelRequest>().reason); result = row; }
      else if (parts[2] === 'attendance') {
        if (row.lesson.startAt > previewNow || row.status === 'CANCELED') fail('INVALID_RESERVATION_STATUS', '시작된 수업의 취소되지 않은 예약만 출결 처리할 수 있습니다.');
        if (inSettlement(row.lesson.id)) fail('LESSON_SETTLED', '정산에 포함된 출결입니다. 미확정 정산을 폐기한 뒤 정정해 주세요.');
        result = update(row, { ...payload<API.AttendanceRequest>(), attendanceConfirmedAt: previewNow }, 'ATTENDANCE', '출결 기록', id, row.member);
      } else result = row;
    } else if (path === '/pass-products' && method === 'GET') result = page(db.products.filter(p => keyword(p.name)), url);
    else if (path === '/pass-products' && method === 'POST') { const row = { ...payload<API.PassProductCreateRequest>(), id: nextId(), createdAt: previewNow }; db.products.unshift(row); audit('PASS_PRODUCT', '상품 등록', 'PASS_PRODUCT', row.id, {}, row); result = row; }
    else if (parts[0] === 'pass-products' && id) {
      const row = find(db.products, id);
      if (method === 'DELETE') { if (db.passes.some(p => p.passProduct.id === id)) fail('PASS_PRODUCT_IN_USE', '발급 이력이 있는 상품은 삭제할 수 없습니다.'); db.products.splice(db.products.indexOf(row),1); audit('PASS_PRODUCT', '상품 삭제', 'PASS_PRODUCT', id); }
      else if (method === 'PATCH') {
        const { applyToIssued, ...input } = payload<API.PassProductUpdateRequest>(); const oldCount = row.totalCount;
        update(row, input, 'PASS_PRODUCT', '상품 수정', id); let affectedPassCount = 0;
        db.passes.filter(p => p.passProduct.id === id).forEach(p => { p.passProduct.name = row.name; if (applyToIssued && ['ACTIVE','UPCOMING'].includes(p.status)) { if (input.totalCount !== undefined) { p.remainingCount = Math.max(0, p.remainingCount + row.totalCount - oldCount); p.totalCount = row.totalCount; } if (input.validityDays !== undefined) p.endDate = addDays(p.startDate, row.validityDays - 1); refreshPass(p); affectedPassCount++; } }); result = { passProduct: row, affectedPassCount };
      } else result = row;
    } else if (path === '/passes' && method === 'GET') {
      let rows = db.passes.filter(p => match('memberId', p.member.id) && match('instructorId', p.instructor.id) && match('passProductId', p.passProduct.id) && match('status', p.status) && match('paymentStatus', p.paymentStatus) && keyword(p.member.name) && range(p.createdAt, 'createdFrom', 'createdTo'));
      const key = (q.get('sortBy') ?? 'createdAt') as 'createdAt' | 'endDate' | 'totalCount' | 'remainingCount';
      rows = rows.sort((a,b) => (typeof a[key] === 'number' ? Number(a[key]) - Number(b[key]) : String(a[key]).localeCompare(String(b[key]))) * (q.get('order') === 'ASC' ? 1 : -1)); result = page(rows,url);
    } else if (path === '/passes' && method === 'POST') {
      const input = payload<API.PassCreateRequest>(), product = find(db.products, input.passProductId), member = find(db.members, input.memberId), instructor = find(db.members, input.instructorId);
      if (member.role !== 'MEMBER' || member.status !== 'ACTIVE' || instructor.role !== 'INSTRUCTOR' || instructor.status !== 'ACTIVE') fail('INVALID_INPUT', '활성 회원과 강사를 선택해 주세요.', 400);
      const row: API.Pass = { id: nextId(), member: summary(member), instructor: summary(instructor), passProduct: { id: product.id, name: product.name }, price: input.price ?? product.price, totalCount: product.totalCount, remainingCount: product.totalCount, startDate: input.startDate, endDate: addDays(input.startDate, product.validityDays - 1), paidAmount: 0, unpaidAmount: input.price ?? product.price, paymentStatus: 'UNPAID', status: 'ACTIVE', createdAt: previewNow };
      db.passes.unshift(row); refreshPass(row); audit('PASS', '이용권 발급', 'PASS', row.id, {}, row, member); result = row;
    } else if (parts[0] === 'passes' && id) {
      const row = find(db.passes, id);
      if (parts[2] === 'payments') {
        if (parts[4] === 'refund') { const payment = find(db.payments, subId), input = payload<API.PassPaymentRefundRequest>(); if (payment.pass.id !== id) fail('RESOURCE_NOT_FOUND', '결제 기록을 찾을 수 없습니다.',404); result = update(payment, { refunded: input.refunded, refundedAt: input.refunded ? input.refundedAt ?? previewToday : null, refundMemo: input.refunded ? input.memo : null }, 'PAYMENT', '환불 여부 기록', subId, row.member); }
        else if (method === 'GET') result = page(db.payments.filter(p => p.pass.id === id), url);
        else if (method === 'DELETE') { const payment = find(db.payments, subId); if (payment.refunded) fail('PAYMENT_REFUNDED','환불 표시를 먼저 해제해 주세요.'); db.payments.splice(db.payments.indexOf(payment),1); refreshPass(row); audit('PAYMENT', '결제 기록 삭제', 'PASS', id, {}, {}, row.member); }
        else {
          const input = payload<API.PassPaymentCreateRequest>(); if (input.paidAt > previewToday || input.amount < 1) fail('INVALID_INPUT', '결제일과 금액을 확인해 주세요.',400);
          if (method === 'PATCH' && find(db.payments, subId).refunded) fail('PAYMENT_REFUNDED','환불 표시를 먼저 해제해 주세요.');
          const total = db.payments.filter(p => p.pass.id === id && (method !== 'PATCH' || p.id !== subId)).reduce((sum,p) => sum + p.amount,0) + input.amount;
          if (total > row.price) fail('PAYMENT_EXCEEDS_PRICE','결제 합계는 이용권 금액을 초과할 수 없습니다.');
          if (method === 'PATCH') result = update(find(db.payments, subId), input, 'PAYMENT', '결제 기록 수정', subId, row.member);
          else { const payment: API.PassPayment = { ...input, id: nextId(), refunded: false, member: row.member, pass: { id, passProduct: row.passProduct, endDate: row.endDate }, recordedBy: { id: 3, name: '센터 관리자' }, createdAt: previewNow }; db.payments.unshift(payment); audit('PAYMENT', '결제 기록', 'PASS', id, {}, payment, row.member); result = payment; }
          refreshPass(row);
        }
      } else if (parts[2] === 'pauses') {
        const pauses = db.pauses[id] ?? (db.pauses[id] = []);
        if (method === 'GET') result = page(pauses,url);
        else if (parts[4] === 'terminations') {
          const pause = find(pauses,subId); if (!['SCHEDULED','ACTIVE'].includes(pause.status)) fail('INVALID_PAUSE_STATUS', '이미 종료된 홀딩입니다.');
          const used = pause.startDate >= previewToday ? 0 : Math.ceil((Date.parse(previewToday) - Date.parse(pause.startDate)) / 86400000);
          row.endDate = addDays(row.endDate, used - pause.days); result = update(pause, { status: used ? 'ENDED' : 'CANCELED', days: used, endDate: used ? addDays(previewToday,-1) : pause.endDate, terminatedAt: previewNow }, 'PASS', '홀딩 해제', id, row.member); refreshPass(row);
        } else {
          const input = payload<API.PassPauseCreateRequest>();
          if (row.status === 'EXPIRED' || input.startDate < previewToday || input.startDate < row.startDate || input.startDate > row.endDate || input.endDate < input.startDate) fail('INVALID_PAUSE_PERIOD', '이용 기간 안의 오늘 이후 날짜와 종료일을 확인해 주세요.',400);
          if (pauses.some(p => p.status !== 'CANCELED' && p.startDate <= input.endDate && p.endDate >= input.startDate)) fail('PAUSE_PERIOD_OVERLAP', '기존 홀딩 기간과 겹칩니다.');
          if (db.reservations.some(r => r.pass.id === id && r.status === 'RESERVED' && localDate(r.lesson.startAt) >= input.startDate && localDate(r.lesson.startAt) <= input.endDate)) fail('PAUSE_HAS_RESERVATIONS','홀딩 기간에 포함된 예약을 먼저 취소해 주세요.');
          const count = Math.round((Date.parse(input.endDate) - Date.parse(input.startDate)) / 86400000) + 1;
          const pause: API.PassPause = { ...input, id: nextId(), days: count, status: input.startDate === previewToday ? 'ACTIVE' : 'SCHEDULED', createdAt: previewNow };
          pauses.unshift(pause); row.endDate = addDays(row.endDate,count);
          refreshPass(row); audit('PASS', '홀딩 등록', 'PASS', id, {}, pause, row.member); result = pause;
        }
      } else if (method === 'PATCH') {
        const { instructorId, ...input } = payload<API.PassUpdateRequest>();
        if (input.remainingCount !== undefined && (input.remainingCount < 0 || input.remainingCount > row.totalCount)) fail('INVALID_INPUT', '잔여 횟수는 0부터 총 횟수 사이여야 합니다.',400);
        if (input.endDate && input.endDate < row.startDate) fail('INVALID_INPUT', '만료일은 시작일 이후여야 합니다.',400);
        result = update(row, { ...input, ...(instructorId ? { instructor: summary(find(db.members,instructorId)) } : {}) }, 'PASS', '이용권 조정', id, row.member); refreshPass(row);
      } else result = row;
    } else if (path === '/payments') result = page(db.payments.filter(p => match('memberId',p.member.id) && match('method',p.method) && match('refunded',p.refunded) && (!q.get('paidFrom') || p.paidAt >= q.get('paidFrom')!) && (!q.get('paidTo') || p.paidAt <= q.get('paidTo')!)),url);
    else if (path === '/lesson-types' && method === 'GET') result = page(db.lessonTypes.filter(t => match('active',t.active)),url);
    else if (path === '/lesson-types' && method === 'POST') { const input = payload<API.LessonTypeCreateRequest>(); if (db.lessonTypes.some(t => t.code === input.code)) fail('LESSON_TYPE_CODE_DUPLICATED', '이미 등록된 코드입니다.'); const row = { ...input, id: nextId(), active: true, createdAt: previewNow }; db.lessonTypes.push(row); audit('LESSON_TYPE', '수업 유형 등록', 'LESSON_TYPE', row.id, {}, row); result = row; }
    else if (parts[0] === 'lesson-types' && id) result = update(find(db.lessonTypes,id),payload<API.LessonTypeUpdateRequest>(),'LESSON_TYPE','수업 유형 수정',id);
    else if (path === '/rooms' && method === 'GET') result = page([...db.rooms].sort((a,b) => a.name.localeCompare(b.name)),url);
    else if (path === '/rooms' && method === 'POST') { const input = payload<API.RoomCreateRequest>(); if (db.rooms.some(r => r.name === input.name)) fail('ROOM_NAME_DUPLICATED','이미 등록된 룸 이름입니다.'); const row = { ...input, id: nextId(), createdAt: previewNow }; db.rooms.push(row); audit('ROOM','룸 등록','ROOM',row.id,{},row); result = row; }
    else if (parts[0] === 'rooms' && id) {
      const row = find(db.rooms,id);
      if (method === 'DELETE') { if (db.lessons.some(l => l.room.id === id && l.startAt > previewNow && l.status !== 'CANCELED')) fail('ROOM_IN_USE','사용 예정 수업의 룸을 변경하거나 수업을 취소해 주세요.'); db.rooms.splice(db.rooms.indexOf(row),1); audit('ROOM','룸 삭제','ROOM',id); }
      else if (method === 'PATCH') { const input = payload<API.RoomUpdateRequest>(); if (db.rooms.some(r => r.id !== id && r.name === input.name)) fail('ROOM_NAME_DUPLICATED','이미 등록된 룸 이름입니다.'); result = update(row,input,'ROOM','룸 수정',id); }
      else result = row;
    } else if (path === '/business-hours') {
      if (method === 'PUT') { const input = payload<API.BusinessHoursRequest>(); if (input.businessHours.length !== 7 || new Set(input.businessHours.map(h => h.dayOfWeek)).size !== 7 || input.businessHours.some(h => !h.closed && (!h.openTime || !h.closeTime || h.openTime >= h.closeTime))) fail('INVALID_BUSINESS_HOURS','7개 요일의 영업시간을 확인해 주세요.',400); db.businessHours = input.businessHours.map(h => h.closed ? { ...h, openTime: null, closeTime: null } : h); audit('BUSINESS_HOUR','영업시간 변경','BUSINESS_HOURS',0); }
      result = { businessHours: db.businessHours };
    } else if (path === '/reservation-policy') { if (method === 'PUT') { const input = payload<API.ReservationPolicy>(); if (input.cancelDeadlineHours < 0 || input.cancelDeadlineHours > 720) fail('INVALID_INPUT','취소 마감은 0~720시간입니다.',400); update(db.policy,input,'POLICY','예약 정책 변경',0); } result = db.policy; }
    else if (parts[0] === 'members' && parts[2] === 'pay-rates') {
      if (method === 'PUT') { const input = payload<API.PayRatesRequest>(); db.payRates[id] = input.payRates.map(p => ({ lessonType: find(db.lessonTypes,p.lessonTypeId), unitPrice: p.unitPrice })); audit('PAY_RATE','수당 기준 변경','MEMBER',id); }
      result = { instructor: summary(find(db.members,id)), payRates: db.payRates[id] ?? [] };
    } else if (path === '/settlements' && method === 'GET') result = page(db.settlements.filter(s => match('instructorId',s.instructor.id) && match('status',s.status) && (!q.get('periodFrom') || s.periodEnd >= q.get('periodFrom')!) && (!q.get('periodTo') || s.periodStart <= q.get('periodTo')!)),url);
    else if (path === '/settlements' && method === 'POST') {
      const input = payload<API.SettlementCreateRequest>();
      if (input.periodEnd >= previewToday || input.periodEnd < input.periodStart) fail('INVALID_PERIOD','정산 종료일은 오늘 이전, 시작일 이후여야 합니다.',400);
      if (db.settlements.some(s => s.instructor.id === input.instructorId && s.periodStart <= input.periodEnd && s.periodEnd >= input.periodStart)) fail('SETTLEMENT_PERIOD_OVERLAP','기존 정산 기간과 겹칩니다.');
      const lessons = db.lessons.filter(l => l.instructor.id === input.instructorId && l.status !== 'CANCELED' && localDate(l.startAt) >= input.periodStart && localDate(l.startAt) <= input.periodEnd);
      const items: API.SettlementItem[] = lessons.map(lesson => {
        const reservations = db.reservations.filter(r => r.lesson.id === lesson.id && r.status !== 'CANCELED');
        if (reservations.some(r => r.status === 'RESERVED')) fail('UNRECORDED_ATTENDANCE_EXISTS','미처리 출결을 먼저 완료해 주세요.');
        const price = db.payRates[input.instructorId]?.find(p => p.lessonType.id === lesson.lessonType.id)?.unitPrice;
        if (reservations.length && price === undefined) fail('PAY_RATE_NOT_SET','수업 유형별 수당을 먼저 설정해 주세요.');
        return { lesson: structuredClone(lesson), attendedCount: reservations.filter(r => r.status === 'ATTENDED').length, noShowCount: reservations.filter(r => r.status === 'NO_SHOW').length, unitPrice: reservations.length ? price : null, amount: reservations.length ? price! : 0, settled: !!reservations.length, excludedReason: reservations.length ? null : 'NO_RESERVATION' };
      });
      const row: API.SettlementDetail = { id: nextId(), instructor: summary(find(db.members,input.instructorId)), periodStart: input.periodStart, periodEnd: input.periodEnd, status: 'CALCULATED', totalAmount: items.reduce((sum,i) => sum + i.amount,0), settledLessonCount: items.filter(i => i.settled).length, excludedLessonCount: items.filter(i => !i.settled).length, items, createdAt: previewNow };
      db.settlements.unshift(row); audit('SETTLEMENT','정산 계산','SETTLEMENT',row.id,{},row); result = row;
    } else if (parts[0] === 'settlements' && id) {
      const row = find(db.settlements,id);
      if (method === 'GET') result = row;
      else if (method === 'DELETE') { if (!['CALCULATED','REVIEW_REQUESTED'].includes(row.status)) fail('INVALID_SETTLEMENT_STATUS','확정된 정산은 폐기할 수 없습니다.'); db.settlements.splice(db.settlements.indexOf(row),1); audit('SETTLEMENT','정산 폐기','SETTLEMENT',id); }
      else {
        const expected = parts[2] === 'review-requests' ? 'CALCULATED' : parts[2] === 'confirmations' ? 'REVIEW_REQUESTED' : 'CONFIRMED';
        if (row.status !== expected) fail('INVALID_SETTLEMENT_STATUS','정산 처리 순서를 확인해 주세요.');
        const input = payload<API.SettlementPaymentRequest>(); if (parts[2] === 'payments' && (!input.paidAt || input.paidAt > previewToday)) fail('INVALID_INPUT','실제 지급일을 확인해 주세요.',400);
        result = update(row, parts[2] === 'review-requests' ? { status: 'REVIEW_REQUESTED', reviewRequestedAt: previewNow } : parts[2] === 'confirmations' ? { status: 'CONFIRMED', confirmedAt: previewNow } : { status: 'PAID', payment: { ...input, recordedAt: previewNow } },'SETTLEMENT',parts[2] === 'payments' ? '외부 지급 기록' : parts[2] === 'confirmations' ? '정산 확정' : '정산 검토 요청',id);
      }
    } else if (path === '/notifications/unread-count') result = { unreadCount: db.notifications.filter(n => !n.read).length };
    else if (path === '/notifications/read') { db.notifications.forEach(n => { n.read = true; n.readAt = previewNow; }); }
    else if (parts[0] === 'notifications' && parts[2] === 'read') { const row = find(db.notifications,id); row.read = true; row.readAt = previewNow; }
    else if (path === '/notifications') result = page(db.notifications.filter(n => match('read',n.read)),url);
    else if (path === '/activity-log-actions') {
      const names: Record<string,string> = { MEMBER:'회원',LESSON:'수업',RESERVATION:'예약',ATTENDANCE:'출결',PASS:'이용권',PAYMENT:'결제',PERMISSION:'권한',INSTRUCTOR:'강사',ABSENCE:'부재',PASS_PRODUCT:'이용권 상품',LESSON_TYPE:'수업 유형',ROOM:'룸',BUSINESS_HOUR:'영업시간',POLICY:'예약 정책',PAY_RATE:'수당',SETTLEMENT:'정산' };
      result = { categories: Object.entries(names).map(([code,name]) => ({ code,name,actions: Array.from(new Map(db.logs.filter(l => l.category === code).map(l => [l.action,{code:l.action,name:l.actionName}])).values()) })) };
    } else if (path === '/activity-logs') { const rows = db.logs.filter(l => match('category',l.category) && match('action',l.action) && match('actorId',l.actor?.id) && match('actorRole',l.actor?.role) && match('subjectMemberId',l.subjectMember?.id) && match('targetType',l.targetType) && match('targetId',l.targetId) && range(l.occurredAt)); result = page(q.get('order') === 'ASC' ? [...rows].reverse() : rows,url); }
    else fail('RESOURCE_NOT_FOUND','아직 연결되지 않은 요청입니다.',404);
    // Body-less contract operations must return 204, irrespective of the updated mock row.
    const noContent = method === 'DELETE' || (parts[0] === 'notifications' && method !== 'GET');
    return noContent || result === undefined ? new HttpResponse(null,{status:204}) : HttpResponse.json(result,{status: method === 'POST' && parts.length === 1 ? 201 : 200});
  } catch (error) {
    const problem = error instanceof MockProblem ? error : new MockProblem('INTERNAL_ERROR','요청을 처리하지 못했습니다.',500);
    return HttpResponse.json({ type:'about:blank', title:'요청 실패', status:problem.status, code:problem.code, detail:problem.message, timestamp:previewNow },{status:problem.status,headers:{'Content-Type':'application/problem+json'}});
  }
})];
