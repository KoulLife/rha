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

type MenuDefinition = {
  screen: string;
  label: string;
  roles: readonly UserRole[];
  permission: Permission;
  roleLabels?: Partial<Record<UserRole, string>>;
  pageLabel?: string;
};

// 배열 순서가 사이드바 순서다. 현재 구현된 화면만 등록한다.
export const menuItems = [
  { screen: "home", label: "홈", roles: ["MEMBER", "INSTRUCTOR", "ADMIN"], permission: "lesson.read" },
  { screen: "classes", label: "수업", pageLabel: "수업 목록", roles: ["MEMBER"], permission: "lesson.read" },
  { screen: "passes", label: "내 이용권", roles: ["MEMBER"], permission: "pass.read" },
  { screen: "reservations", label: "내 예약·출결", roles: ["MEMBER"], permission: "reservation.read" },
  { screen: "profile", label: "내 계정", roles: ["MEMBER"], permission: "profile.read" },
  { screen: "today-lessons", label: "오늘 전체 수업", roleLabels: { INSTRUCTOR: "오늘 담당 수업" }, roles: ["INSTRUCTOR", "ADMIN"], permission: "lesson.read" },
  { screen: "attendance-overview", label: "예약·출결 현황", roles: ["INSTRUCTOR", "ADMIN"], permission: "reservation.read" },
  { screen: "absence-requests", label: "부재 승인 대기", roleLabels: { INSTRUCTOR: "내 부재 승인 대기" }, roles: ["INSTRUCTOR", "ADMIN"], permission: "profile.read" },
  { screen: "notifications", label: "알림", roles: ["MEMBER", "INSTRUCTOR", "ADMIN"], permission: "notification.read" },
] as const satisfies readonly MenuDefinition[];

export type MenuScreen = typeof menuItems[number]["screen"];
export type NavigationItem = { screen: MenuScreen; label: string };

// 상세 화면은 상위 메뉴의 접근 조건과 선택 표시를 공유한다.
export const detailScreens = {
  "lesson-detail": { parent: "classes", label: "수업 상세" },
  "pass-detail": { parent: "passes", label: "이용권 상세" },
  "reservation-detail": { parent: "reservations", label: "예약 상세" },
} as const satisfies Record<string, { parent: MenuScreen; label: string }>;

export type Screen = MenuScreen | keyof typeof detailScreens;

function isDetailScreen(screen: Screen): screen is keyof typeof detailScreens {
  return Object.prototype.hasOwnProperty.call(detailScreens, screen);
}

export function getMenuScreen(screen: Screen): MenuScreen {
  return isDetailScreen(screen) ? detailScreens[screen].parent : screen;
}

export function getNavigation(role: UserRole, permissions: ReadonlySet<Permission>): NavigationItem[] {
  return menuItems.filter((item: MenuDefinition) => item.roles.includes(role) && permissions.has(item.permission))
    .map((item: MenuDefinition & { screen: MenuScreen }) => ({
      screen: item.screen, label: item.roleLabels?.[role] ?? item.label,
    }));
}

export function canAccessScreen(screen: Screen, role: UserRole, permissions: ReadonlySet<Permission>) {
  return getNavigation(role, permissions).some(item => item.screen === getMenuScreen(screen));
}

export function getScreenLabel(screen: Screen, role: UserRole): string {
  if (isDetailScreen(screen)) return detailScreens[screen].label;
  const item: MenuDefinition = menuItems.find(item => item.screen === screen)!;
  return item.pageLabel ?? item.roleLabels?.[role] ?? item.label;
}
