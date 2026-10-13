export type Screen =
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
} | null;

export const screenLabels: Record<Screen, string> = {
  classes: "수업 목록",
  "lesson-detail": "수업 상세",
  passes: "내 이용권",
  "pass-detail": "이용권 상세",
  reservations: "내 예약·출결",
  "reservation-detail": "예약 상세",
  profile: "내 계정",
  notifications: "알림",
};

