export type UserRole = "MEMBER" | "INSTRUCTOR" | "ADMIN";

export type Permission =
  | "lesson.read"
  | "reservation.create"
  | "reservation.cancel"
  | "waitlist.create"
  | "waitlist.cancel"
  | "pass.read"
  | "reservation.read"
  | "profile.read"
  | "profile.update"
  | "password.change"
  | "notification.read";

export type MemberMenuItem = {
  screen: "home" | "classes" | "passes" | "reservations" | "profile" | "notifications" | "today-lessons" | "attendance-overview" | "absence-requests";
  label: string;
  roles: UserRole[];
  permission: Permission;
};

export const memberMenuItems: MemberMenuItem[] = [
  { screen: "home", label: "홈", roles: ["MEMBER"], permission: "lesson.read" },
  { screen: "classes", label: "수업", roles: ["MEMBER"], permission: "lesson.read" },
  { screen: "passes", label: "내 이용권", roles: ["MEMBER"], permission: "pass.read" },
  { screen: "reservations", label: "내 예약·출결", roles: ["MEMBER"], permission: "reservation.read" },
  { screen: "profile", label: "내 계정", roles: ["MEMBER"], permission: "profile.read" },
  { screen: "notifications", label: "알림", roles: ["MEMBER"], permission: "notification.read" },
];

export function canAccessMenu(item: MemberMenuItem, role: UserRole, permissions: ReadonlySet<Permission>) {
  return item.roles.includes(role) && permissions.has(item.permission);
}

// 현재 구현된 홈과 연결 조회 화면만 제공한다. 운영 쓰기 권한은 부여하지 않는다.
export const dashboardMenuItems: MemberMenuItem[] = [
  { screen: "home", label: "홈", roles: ["INSTRUCTOR", "ADMIN"], permission: "lesson.read" },
  { screen: "today-lessons", label: "오늘 담당 수업", roles: ["INSTRUCTOR"], permission: "lesson.read" },
  { screen: "today-lessons", label: "오늘 전체 수업", roles: ["ADMIN"], permission: "lesson.read" },
  { screen: "attendance-overview", label: "예약·출결 현황", roles: ["INSTRUCTOR", "ADMIN"], permission: "reservation.read" },
  { screen: "absence-requests", label: "내 부재 승인 대기", roles: ["INSTRUCTOR"], permission: "profile.read" },
  { screen: "absence-requests", label: "부재 승인 대기", roles: ["ADMIN"], permission: "profile.read" },
  { screen: "notifications", label: "알림", roles: ["INSTRUCTOR", "ADMIN"], permission: "notification.read" },
];
