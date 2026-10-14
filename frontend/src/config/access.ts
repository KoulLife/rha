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
  { screen: "admin-members", label: "회원 관리", roles: ["ADMIN"], permission: "profile.read" },
  { screen: "admin-instructors", label: "강사 관리", roles: ["ADMIN"], permission: "profile.read" },
  { screen: "admin-lessons", label: "수업 운영", roles: ["ADMIN"], permission: "lesson.read" },
  { screen: "admin-passes", label: "회원 이용권", roles: ["ADMIN"], permission: "pass.read" },
  { screen: "admin-settlements", label: "강사 수당·정산", roles: ["ADMIN"], permission: "profile.read" },
  { screen: "today-lessons", label: "오늘 전체 수업", roleLabels: { INSTRUCTOR: "오늘 담당 수업" }, roles: ["INSTRUCTOR"], permission: "lesson.read" },
  { screen: "attendance-overview", label: "예약·출결 현황", roles: ["INSTRUCTOR"], permission: "reservation.read" },
  { screen: "absence-requests", label: "부재 승인 대기", roleLabels: { INSTRUCTOR: "내 부재 승인 대기" }, roles: ["INSTRUCTOR"], permission: "profile.read" },
  { screen: "notifications", label: "알림", roles: ["MEMBER", "INSTRUCTOR", "ADMIN"], permission: "notification.read" },
  { screen: "activity-logs", label: "활동 이력", roles: ["ADMIN"], permission: "profile.read" },
  { screen: "admin-settings", label: "스튜디오 설정", roles: ["ADMIN"], permission: "profile.read" },
] as const satisfies readonly MenuDefinition[];

export type MenuScreen = typeof menuItems[number]["screen"];
export type NavigationItem = { screen: MenuScreen; label: string; children?: readonly { screen: Screen; label: string; activeScreens: readonly Screen[] }[] };

// 상세 화면은 상위 메뉴의 접근 조건과 선택 표시를 공유한다.
export const detailScreens = {
  "admin-member-detail": { parent: "admin-members", label: "회원 상세" },
  "admin-instructor-detail": { parent: "admin-instructors", label: "강사 상세" },
  "admin-absences": { parent: "admin-instructors", label: "부재 관리" },
  "admin-absence-detail": { parent: "admin-instructors", label: "부재 상세" },
  "admin-resignations": { parent: "admin-instructors", label: "퇴사 관리" },
  "admin-lesson-detail": { parent: "admin-lessons", label: "수업 상세" },
  "admin-lesson-create": { parent: "admin-lessons", label: "수업 개설" },
  "admin-lesson-edit": { parent: "admin-lessons", label: "수업 수정" },
  "admin-reservations": { parent: "admin-lessons", label: "예약·출결" },
  "admin-repeat": { parent: "admin-lessons", label: "반복 일정" },
  "admin-waitlist": { parent: "admin-lessons", label: "승인 신청 대기" },
  "admin-pass-detail": { parent: "admin-passes", label: "이용권 상세" },
  "admin-member-passes": { parent: "admin-passes", label: "회원별 이용권" },
  "admin-payments": { parent: "admin-passes", label: "결제 내역" },
  "admin-products": { parent: "admin-settings", label: "이용권 상품" },
  "admin-types": { parent: "admin-settings", label: "수업 유형" },
  "admin-rooms": { parent: "admin-settings", label: "룸" },
  "admin-hours": { parent: "admin-settings", label: "영업시간·휴무일" },
  "admin-policy": { parent: "admin-settings", label: "예약 정책" },
  "admin-settlement-create": { parent: "admin-settlements", label: "정산 계산" },
  "admin-settlement-detail": { parent: "admin-settlements", label: "정산 상세" },
  "admin-instructor-settlements": { parent: "admin-settlements", label: "강사별 정산" },
  "admin-pay-rates": { parent: "admin-settlements", label: "수당 기준" },
  "lesson-detail": { parent: "classes", label: "수업 상세" },
  "pass-detail": { parent: "passes", label: "이용권 상세" },
  "reservation-detail": { parent: "reservations", label: "예약 상세" },
} as const satisfies Record<string, { parent: MenuScreen; label: string }>;

export type Screen = MenuScreen | keyof typeof detailScreens;

const instructorSubmenus = [
  { screen: 'admin-instructors', label: '강사 목록', activeScreens: ['admin-instructors', 'admin-instructor-detail'] },
  { screen: 'admin-absences', label: '부재 관리', activeScreens: ['admin-absences', 'admin-absence-detail'] },
  { screen: 'admin-resignations', label: '퇴사 관리', activeScreens: ['admin-resignations'] },
] as const satisfies NonNullable<NavigationItem['children']>;

const submenus: Partial<Record<MenuScreen, NonNullable<NavigationItem['children']>>> = {
  'admin-instructors': instructorSubmenus,
  'admin-lessons': [
    { screen: 'admin-lessons', label: '수업 목록', activeScreens: ['admin-lessons', 'admin-lesson-detail', 'admin-lesson-create', 'admin-lesson-edit'] },
    { screen: 'admin-reservations', label: '예약·출결', activeScreens: ['admin-reservations'] },
    { screen: 'admin-repeat', label: '반복 일정', activeScreens: ['admin-repeat'] },
    { screen: 'admin-waitlist', label: '승인 신청 대기', activeScreens: ['admin-waitlist'] },
  ],
  'admin-passes': [
    { screen: 'admin-passes', label: '이용권 목록', activeScreens: ['admin-passes', 'admin-member-passes', 'admin-pass-detail'] },
    { screen: 'admin-payments', label: '결제 내역', activeScreens: ['admin-payments'] },
  ],
  'admin-settlements': [
    { screen: 'admin-settlements', label: '정산 내역', activeScreens: ['admin-settlements', 'admin-instructor-settlements', 'admin-settlement-detail', 'admin-settlement-create'] },
    { screen: 'admin-pay-rates', label: '수당 기준', activeScreens: ['admin-pay-rates'] },
  ],
  'admin-settings': [
    { screen: 'admin-settings', label: '운영 기본 설정', activeScreens: ['admin-settings'] },
    { screen: 'admin-products', label: '이용권 상품', activeScreens: ['admin-products'] },
    { screen: 'admin-types', label: '수업 유형', activeScreens: ['admin-types'] },
    { screen: 'admin-rooms', label: '룸', activeScreens: ['admin-rooms'] },
    { screen: 'admin-hours', label: '영업시간·휴무일', activeScreens: ['admin-hours'] },
    { screen: 'admin-policy', label: '예약 정책', activeScreens: ['admin-policy'] },
  ],
};

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
      ...(submenus[item.screen] ? { children: submenus[item.screen] } : {}),
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
