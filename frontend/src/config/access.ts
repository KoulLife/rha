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
  screen: "classes" | "passes" | "reservations" | "profile" | "notifications";
  label: string;
  roles: UserRole[];
  permission: Permission;
};

export const memberMenuItems: MemberMenuItem[] = [
  { screen: "classes", label: "수업", roles: ["MEMBER"], permission: "lesson.read" },
  { screen: "passes", label: "내 이용권", roles: ["MEMBER"], permission: "pass.read" },
  { screen: "reservations", label: "내 예약·출결", roles: ["MEMBER"], permission: "reservation.read" },
  { screen: "profile", label: "내 계정", roles: ["MEMBER"], permission: "profile.read" },
  { screen: "notifications", label: "알림", roles: ["MEMBER"], permission: "notification.read" },
];

export function canAccessMenu(item: MemberMenuItem, role: UserRole, permissions: ReadonlySet<Permission>) {
  return item.roles.includes(role) && permissions.has(item.permission);
}
