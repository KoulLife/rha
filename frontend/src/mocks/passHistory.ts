// TODO 
// 회원용 이용권 횟수 이력 API는 아직 없다. 실제 연동 전 OpenAPI 계약과
// 차감·복구 시점의 잔여 횟수를 보존하는 원장을 먼저 정의해야 한다.
// 예약 시 차감, 취소 시 복구하며 출석·노쇼로 추가 차감하지 않는다.
export type PassHistoryEntry = {
  id: number;
  passId: number;
  occurredAt: string;
  type: "RESERVATION" | "CANCELLATION";
  lesson: string;
  lessonStartAt: string;
  change: number;
  remaining: number;
};

export const passHistory: PassHistoryEntry[] = [
  { id: 1, passId: 1001, occurredAt: "2026-10-02T00:00:00Z", type: "RESERVATION", lesson: "기구 필라테스", lessonStartAt: "2026-10-03T01:00:00Z", change: -1, remaining: 9 },
  { id: 2, passId: 1001, occurredAt: "2026-10-06T00:00:00Z", type: "RESERVATION", lesson: "모닝 리포머", lessonStartAt: "2026-10-08T01:00:00Z", change: -1, remaining: 8 },
  { id: 3, passId: 1001, occurredAt: "2026-10-07T00:00:00Z", type: "CANCELLATION", lesson: "모닝 리포머", lessonStartAt: "2026-10-08T01:00:00Z", change: 1, remaining: 9 },
  { id: 4, passId: 1001, occurredAt: "2026-10-11T00:20:00Z", type: "RESERVATION", lesson: "기구 필라테스", lessonStartAt: "2026-10-13T01:00:00Z", change: -1, remaining: 8 },
  { id: 5, passId: 1001, occurredAt: "2026-10-12T06:35:00Z", type: "RESERVATION", lesson: "리포머 그룹", lessonStartAt: "2026-10-13T05:00:00Z", change: -1, remaining: 7 },
  { id: 6, passId: 1002, occurredAt: "2026-09-16T00:00:00Z", type: "RESERVATION", lesson: "개인 레슨", lessonStartAt: "2026-09-18T10:00:00Z", change: -1, remaining: 4 },
  { id: 7, passId: 1002, occurredAt: "2026-09-23T00:00:00Z", type: "RESERVATION", lesson: "개인 레슨", lessonStartAt: "2026-09-25T10:00:00Z", change: -1, remaining: 3 },
  { id: 8, passId: 1002, occurredAt: "2026-10-01T00:00:00Z", type: "RESERVATION", lesson: "개인 레슨", lessonStartAt: "2026-10-02T10:00:00Z", change: -1, remaining: 2 },
];
