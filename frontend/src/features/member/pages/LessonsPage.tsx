import { lessons } from "../../../mocks/member";
import type { DialogState } from "../../../app/routes";
import { PageTitle } from "../../../components/ui/PageTitle";
import { Pagination } from "../../../components/ui/Pagination";

type Lesson = (typeof lessons)[number];

export function LessonsPage({
  onOpen,
  canReserve,
  canWaitlist,
}: {
  onOpen: (id: number) => void;
  canReserve: boolean;
  canWaitlist: boolean;
}) {
  return (
    <>
      <PageTitle title="수업 목록" description="공개된 수업을 확인하고 나에게 맞는 수업을 예약하세요." />
      <form className="filter-bar">
        <label>시작일<input type="date" defaultValue="2026-10-12" /></label>
        <label>종료일<input type="date" defaultValue="2026-10-18" /></label>
        <label>수업 유형<select defaultValue="all"><option value="all">전체 유형</option><option>기구 필라테스</option><option>리포머 그룹</option></select></label>
        <label>룸<select defaultValue="all"><option value="all">전체 룸</option><option>룸 A</option><option>룸 B</option></select></label>
        <button className="button outline" type="button">조회</button>
        <button className="button ghost" type="button">초기화</button>
      </form>
      <div className="result-line"><strong>수업 {lessons.length}개</strong><span>공개 수업만 표시 · 서울 시간 기준</span></div>
      <div className="lesson-list">
        {lessons.map((lesson) => (
          <LessonCard key={lesson.id} lesson={lesson} onOpen={onOpen} canReserve={canReserve} canWaitlist={canWaitlist} />
        ))}
      </div>
      <Pagination />
    </>
  );
}

function LessonCard({ lesson, onOpen, canReserve, canWaitlist }: { lesson: Lesson; onOpen: (id: number) => void; canReserve: boolean; canWaitlist: boolean }) {
  const canAct = lesson.status === "예약 가능" ? canReserve : lesson.status === "대기 신청 가능" ? canWaitlist : false;
  const actionLabel = lesson.status === "예약 가능" ? "수업 예약하기" : "대기 신청하기";

  return (
    <article className={`card lesson-card ${lesson.status === "예약 마감" ? "is-closed" : ""}`}>
      <div className="lesson-date"><strong>{lesson.date}</strong><span>{lesson.time}</span></div>
      <div><span className="eyebrow">{lesson.type}</span><h2>{lesson.type}</h2><p>{lesson.teacher} · {lesson.room}</p></div>
      <div className="lesson-meta"><strong>예약 {lesson.booked}</strong><span className={`badge ${lesson.status === "예약 가능" ? "success" : lesson.status === "예약 마감" ? "danger" : "warning"}`}>{lesson.status}</span></div>
      <div className="card-actions">
        <button className="button outline" type="button" onClick={() => onOpen(lesson.id)}>상세 보기</button>
        <button className={`button reservation-action ${lesson.status === "예약 마감" ? "outline" : lesson.status === "대기 신청 가능" ? "wait-button" : "primary"}`} type="button" disabled={lesson.status === "예약 마감" || !canAct} onClick={() => onOpen(lesson.id)}>
          {lesson.status === "예약 마감" ? "수업 마감" : canAct ? actionLabel : "권한 없음"}
        </button>
      </div>
    </article>
  );
}

export function LessonDetailPage({ lessonId, onBack, onDialog, canReserve, canWaitlist }: { lessonId: number; onBack: () => void; onDialog: (dialog: DialogState) => void; canReserve: boolean; canWaitlist: boolean }) {
  const lesson = lessons.find((item) => item.id === lessonId) ?? lessons[0];
  const full = lesson.status === "예약 마감";
  const canAct = full ? canWaitlist : canReserve;

  return (
    <>
      <PageTitle title="수업 상세" description={`${lesson.date} · ${lesson.type}`} action={<button className="button outline" type="button" onClick={onBack}>목록으로</button>} />
      <div className="detail-grid">
        <section className="card">
          <div className="detail-hero"><span className={`badge ${full ? "danger" : "success"}`}>{full ? "예약 마감" : "공개"}</span><h2>{lesson.type}</h2><p>{lesson.teacher} · {lesson.room}</p></div>
          <dl className="facts"><div><dt>수업 유형</dt><dd>{lesson.type}</dd></div><div><dt>담당 강사</dt><dd>{lesson.teacher}</dd></div><div><dt>룸</dt><dd>{lesson.room}</dd></div><div><dt>수업 일시</dt><dd>2026.{lesson.date} {lesson.time}</dd></div><div><dt>정원 / 예약 인원</dt><dd>{lesson.booked}</dd></div></dl>
        </section>
        <aside className="card side-card application-card"><div className="panel-heading"><h2>내 신청 상태</h2><span className="badge neutral">미신청</span></div><div className="application-body"><p>{full ? "정원이 마감되었습니다. 대기를 신청할 수 있습니다." : "아직 예약하지 않은 수업입니다."}</p><p className="hint">예약 시 사용 가능한 이용권에서 1회가 차감됩니다.</p><button className={`button ${full ? "wait-button" : "primary"} full`} type="button" disabled={lesson.id === 103 || !canAct} onClick={() => onDialog({ title: full ? "대기 신청 확인" : "수업 예약 확인", body: `${lesson.type} · ${lesson.date} ${lesson.time}`, action: full ? "대기 신청하기" : "수업 예약하기" })}>{!canAct ? "권한 없음" : full ? "대기 신청하기" : "수업 예약하기"}</button></div></aside>
      </div>
    </>
  );
}

