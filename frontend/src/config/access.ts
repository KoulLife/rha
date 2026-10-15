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
  { screen: "admin-lessons", label: "수업 조회·관리", roles: ["ADMIN"], permission: "lesson.read" },
  { screen: "timetable", label: "수업", roles: ["MEMBER", "INSTRUCTOR"], permission: "lesson.read" },
  { screen: "passes", label: "내 이용권", roles: ["MEMBER"], permission: "pass.read" },
  { screen: "reservations", label: "내 예약·출결", roles: ["MEMBER"], permission: "reservation.read" },
  { screen: "profile", label: "내 계정", roles: ["MEMBER"], permission: "profile.read" },
  { screen: "admin-members", label: "회원 관리", roles: ["ADMIN"], permission: "profile.read" },
  { screen: "admin-instructors", label: "강사 관리", roles: ["ADMIN"], permission: "profile.read" },
  { screen: "admin-passes", label: "회원 이용권", roles: ["ADMIN"], permission: "pass.read" },
  { screen: "admin-reports", label: "통계·정산", roles: ["ADMIN"], permission: "profile.read" },
  { screen: "absences", label: "부재 신청", roles: ["INSTRUCTOR"], permission: "profile.read" },
  { screen: "activity-logs", label: "활동 이력", roles: ["ADMIN"], permission: "profile.read" },
  { screen: "admin-settings", label: "스튜디오 설정", roles: ["ADMIN"], permission: "profile.read" },
] as const satisfies readonly MenuDefinition[];

export type MenuScreen = typeof menuItems[number]["screen"];
export type NavigationItem = { screen: MenuScreen; label: string; children?: readonly { screen: Screen; label: string; activeScreens: readonly Screen[] }[] };

// 상세 화면은 상위 메뉴의 접근 조건과 선택 표시를 공유한다.
export const detailScreens = {
  "today-lessons": { parent: "timetable", label: "수업" },
  "attendees": { parent: "timetable", label: "수업" },
  "attendance": { parent: "timetable", label: "수업" },
  "attendance-overview": { parent: "timetable", label: "수업" },
  "attendance-corrections": { parent: "timetable", label: "수업" },
  "absence-requests": { parent: "absences", label: "부재 신청" },
  "admin-member-detail": { parent: "admin-members", label: "회원 상세" },
  "admin-instructor-detail": { parent: "admin-instructors", label: "강사 상세" },
  "admin-absences": { parent: "admin-instructors", label: "부재 관리" },
  "admin-absence-detail": { parent: "admin-instructors", label: "부재 상세" },
  "admin-resignations": { parent: "admin-instructors", label: "퇴사 관리" },
  "admin-lesson-detail": { parent: "admin-lessons", label: "수업 상세" },
  "admin-lesson-create": { parent: "admin-lessons", label: "수업 개설" },
  "admin-lesson-edit": { parent: "admin-lessons", label: "수업 수정" },
  "admin-repeat": { parent: "admin-lessons", label: "반복 일정" },
  "admin-waitlist": { parent: "admin-lessons", label: "승인 신청 대기" },
  "admin-pass-detail": { parent: "admin-passes", label: "이용권 상세" },
  "admin-payments": { parent: "admin-passes", label: "결제 내역" },
  "admin-settlements": { parent: "admin-reports", label: "정산 내역" },
  "admin-statistics": { parent: "admin-reports", label: "운영 통계" },
  "admin-sales-statistics": { parent: "admin-reports", label: "매출 통계" },
  "admin-products": { parent: "admin-settings", label: "이용권 상품" },
  "admin-types": { parent: "admin-settings", label: "수업 유형" },
  "admin-rooms": { parent: "admin-settings", label: "룸" },
  "admin-hours": { parent: "admin-settings", label: "영업시간·휴무일" },
  "admin-policy": { parent: "admin-settings", label: "예약 정책" },
  "admin-settlement-create": { parent: "admin-reports", label: "정산 계산" },
  "admin-settlement-detail": { parent: "admin-reports", label: "정산 상세" },
  "admin-instructor-settlements": { parent: "admin-reports", label: "강사별 정산" },
  "classes": { parent: "timetable", label: "수업 목록" },
  "lesson-detail": { parent: "timetable", label: "수업 상세" },
  "instructor-lesson-detail": { parent: "timetable", label: "수업 상세" },
  "pass-detail": { parent: "passes", label: "이용권 상세" },
  "reservation-detail": { parent: "reservations", label: "예약 상세" },
} as const satisfies Record<string, { parent: MenuScreen; label: string }>;

export type Screen = MenuScreen | keyof typeof detailScreens | "notifications" | "absences";

const instructorSubmenus = [
  { screen: 'admin-instructors', label: '강사 목록', activeScreens: ['admin-instructors', 'admin-instructor-detail'] },
  { screen: 'admin-absences', label: '부재 관리', activeScreens: ['admin-absences', 'admin-absence-detail'] },
  { screen: 'admin-resignations', label: '퇴사 관리', activeScreens: ['admin-resignations'] },
] as const satisfies NonNullable<NavigationItem['children']>;

const submenus: Partial<Record<MenuScreen, NonNullable<NavigationItem['children']>>> = {
  'admin-instructors': instructorSubmenus,
  'admin-passes': [
    { screen: 'admin-passes', label: '이용권 목록', activeScreens: ['admin-passes', 'admin-pass-detail'] },
    { screen: 'admin-payments', label: '결제 내역', activeScreens: ['admin-payments'] },
  ],
  'admin-reports': [
    { screen: 'admin-settlements', label: '정산 내역', activeScreens: ['admin-settlements', 'admin-instructor-settlements', 'admin-settlement-create', 'admin-settlement-detail'] },
    { screen: 'admin-statistics', label: '운영 통계', activeScreens: ['admin-statistics'] },
    { screen: 'admin-sales-statistics', label: '매출 통계', activeScreens: ['admin-sales-statistics'] },
  ],
  'admin-settings': [
    { screen: 'admin-settings', label: '운영 기본 설정', activeScreens: ['admin-settings'] },
    { screen: 'admin-products', label: '이용권 상품', activeScreens: ['admin-products'] },
    { screen: 'admin-types', label: '수업 유형', activeScreens: ['admin-types'] },
    { screen: 'admin-rooms', label: '룸', activeScreens: ['admin-rooms'] },
  ],
};

function isDetailScreen(screen: Screen): screen is keyof typeof detailScreens {
  return Object.prototype.hasOwnProperty.call(detailScreens, screen);
}

export function getMenuScreen(screen: Screen): Screen {
  if (screen === "notifications") return screen;
  return isDetailScreen(screen) ? detailScreens[screen].parent : screen;
}

export function getNavigation(role: UserRole, permissions: ReadonlySet<Permission>): NavigationItem[] {
  return menuItems.filter((item: MenuDefinition) => item.roles.includes(role) && permissions.has(item.permission))
    .map((item: MenuDefinition & { screen: MenuScreen }) => ({
      screen: item.screen, label: item.roleLabels?.[role] ?? item.label,
      ...(submenus[item.screen] ? { children: submenus[item.screen] } : {}),
    }));
}

export function canAccessScreen(screen: Screen, role: UserRole, permissions: ReadonlySet<Permission>) {
  if (screen === "notifications") return permissions.has("notification.read");
  if (screen === "attendees" || screen === "attendance" || screen === "attendance-corrections") return role === "INSTRUCTOR" && permissions.has("lesson.read") && permissions.has("reservation.read");
  if (screen === "instructor-lesson-detail") return role === "INSTRUCTOR" && permissions.has("lesson.read");
  if (screen === "absences") return role === "INSTRUCTOR" && permissions.has("profile.read");
  return getNavigation(role, permissions).some(item => item.screen === getMenuScreen(screen));
}

export function getScreenLabel(screen: Screen, role: UserRole): string {
  if (screen === "notifications") return "알림";
  if (screen === "attendees") return "수업별 예약자";
  if (screen === "attendance") return "출결";
  if (screen === "attendance-corrections") return "출결";
  if (screen === "absences") return "부재 신청";
  if (isDetailScreen(screen)) return detailScreens[screen].label;
  const item: MenuDefinition = menuItems.find(item => item.screen === screen)!;
  return item.pageLabel ?? item.roleLabels?.[role] ?? item.label;
}
