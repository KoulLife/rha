export type Screen =
  | "today-lessons"
  | "attendance-overview"
  | "absence-requests"
  | "home"
  | "classes"
  | "lesson-detail"
  | "passes"
  | "pass-detail"
  | "reservations"
  | "reservation-detail"
  | "profile"
  | "notifications";

export type DialogState = {
  title: string;
  body: string;
  action?: string;
  destructive?: boolean;
} | null;

export const screenLabels: Record<Screen, string> = {
  home: "홈",
  "today-lessons": "오늘의 수업",
  "attendance-overview": "예약·출결 현황",
  "absence-requests": "부재 승인 대기",
  classes: "수업 목록",
  "lesson-detail": "수업 상세",
  passes: "내 이용권",
  "pass-detail": "이용권 상세",
  reservations: "내 예약·출결",
  "reservation-detail": "예약 상세",
  profile: "내 계정",
  notifications: "알림",
};

