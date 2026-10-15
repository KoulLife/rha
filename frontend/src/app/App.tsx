import { toast } from "sonner";
import { InstructorPage, isInstructorScreen } from "@/features/instructor/InstructorPage";
import { InstructorLessonRoster } from "@/features/instructor/InstructorLessonRoster";
import { InstructorLessonDetail } from "@/features/instructor/InstructorLessonDetail";
import { initialInstructorState, withInstructorPreview } from "@/features/instructor/preview";
import { attendanceChangeError, type AttendanceChange } from "@/features/instructor/model";
import type { RecordAttendance } from "@/features/instructor/AttendanceActions";
import { Collapsible } from "radix-ui";
import { BookOpen, CalendarDays, CalendarOff, ChartColumn, ChevronRight, ClipboardCheck, CreditCard, DoorOpen, GraduationCap, Grid2X2, History, House, ListChecks, Package, Receipt, Settings, SlidersHorizontal, Ticket, TrendingUp, UserMinus, UserRound, UsersRound, type LucideIcon } from "lucide-react";
import { useEffect, useState } from "react";
import * as api from "@/api/generated/api";
import type { CSSProperties } from "react";
import { AppFooter } from "../components/layout/AppFooter";
import { AppHeader } from "../components/layout/AppHeader";
import { BrandLogo } from "../components/layout/BrandLogo";
import { PageBreadcrumb } from "../components/layout/PageBreadcrumb";
import { Dialog } from "../components/ui/Dialog";
import { canAccessScreen, getMenuScreen, getNavigation, type MenuScreen, type NavigationItem, type Permission } from "../config/access";
import { currentUser } from "../mocks/member";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Sidebar, SidebarContent, SidebarGroup, SidebarGroupContent, SidebarHeader, SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarMenuSub, SidebarMenuSubItem, SidebarMenuSubButton, SidebarProvider, useSidebar } from "@/components/ui/sidebar";
import { HomePage } from "../features/member/pages/HomePage";
import { AccountPage } from "../features/member/pages/AccountPage";
import { LessonDetailPage } from "../features/member/pages/LessonsPage";
import { dashboardPreview, dashboardPreviewTime } from "@/features/dashboard/preview";
import { roleLabels, seoulDate, type DashboardUser } from "@/features/dashboard/model";
import { PassDetailPage, PassesPage } from "../features/member/pages/PassesPage";
import { reservationDialog, ReservationsPage } from "../features/member/pages/ReservationsPage";
import { NotificationsPage } from "../features/member/pages/NotificationsPage";
import type { DialogState, Screen } from "./routes";
import { DashboardError, DashboardLoading } from "@/features/dashboard/DashboardState";
import type { HomeView } from "@/features/dashboard/viewState";
import { AdminWorkspace, useAdminDashboard } from "@/features/admin/AdminWorkspace";
import { LessonsPage as AdminLessonsPage, ReservationsList } from "@/features/admin/LessonPages";
import { QueryState } from "@/features/admin/AdminUI";
import { TimetablePage } from "@/features/timetable/TimetablePage";
import "./legacy.css";
import "./shell.css";

const menuIcons: Record<MenuScreen, LucideIcon> = {
  home: House,
  timetable: CalendarDays,
  passes: Ticket,
  reservations: ClipboardCheck,
  profile: UserRound,
  'admin-members': UsersRound,
  'admin-instructors': GraduationCap,
  'admin-lessons': BookOpen,
  'admin-passes': Ticket,
  'admin-reports': ChartColumn,
  absences: CalendarOff,
  'activity-logs': History,
  'admin-settings': Settings,
};

const submenuIcons: Partial<Record<Screen, LucideIcon>> = {
  'admin-instructors': UsersRound,
  'admin-absences': CalendarOff,
  'admin-resignations': UserMinus,
  'admin-passes': Ticket,
  'admin-payments': CreditCard,
  'admin-settlements': Receipt,
  'admin-statistics': ListChecks,
  'admin-sales-statistics': TrendingUp,
  'admin-settings': SlidersHorizontal,
  'admin-products': Package,
  'admin-types': Grid2X2,
  'admin-rooms': DoorOpen,
};

function App({ user, name = user.name, onLogout, onLogin, initialHomeView = "normal" }: { user: DashboardUser; name?: string; onLogout: (trigger?: HTMLElement) => void; onLogin: () => void; initialHomeView?: HomeView }) {
  const [homeView, setHomeView] = useState(initialHomeView);
  const [screen, setScreen] = useState<Screen>("home");
  const [selectedId, setSelectedId] = useState(101);
  const [data, setData] = useState(() => initialHomeView === "empty" ? { lessons: [], reservations: [], absences: [], notifications: [] } : user.role === "INSTRUCTOR" ? withInstructorPreview(dashboardPreview) : dashboardPreview);
  const [instructorState, setInstructorState] = useState(initialInstructorState);
  const admin = useAdminDashboard(user.role === "ADMIN");
  const viewData = user.role === "ADMIN" && homeView !== "empty" ? admin.data : data;
  const unread = api.useGetMyUnreadNotificationCount();
  const unreadCount = homeView === "empty" ? 0 : unread.data?.unreadCount ?? 0;
  const navigation = getNavigation(user.role, currentUser.permissions);
  const [dialog, setDialog] = useState<DialogState>(null);

  const onRecordAttendance: RecordAttendance = (id, status, reason) => {
    const error = attendanceChangeError(data, user, id, status, reason, dashboardPreviewTime);
    if (error) { toast.error(error); return; }
    const reservation = data.reservations.find(item => item.id === id)!;
    setData(previous => ({ ...previous, reservations: previous.reservations.map(item => item.id === id ? { ...item, status } : item) }));
    setInstructorState(previous => ({ ...previous, changes: [{ id: (previous.changes[0]?.id ?? 0) + 1, reservationId: id, before: reservation.status, after: status, reason: reason.trim(), at: dashboardPreviewTime }, ...previous.changes] }));
    toast.success(reservation.status === "RESERVED" ? "출결을 기록했습니다." : "출결을 수정했습니다.");
  };

  const onApiAttendanceRecorded = (reservation: api.Reservation, status: api.AttendanceRequestStatus, reason: string) => {
    setData(previous => ({ ...previous, reservations: previous.reservations.map(item => item.id === reservation.id ? { ...item, status } : item) }));
    setInstructorState(previous => ({ ...previous, changes: [{ id: (previous.changes[0]?.id ?? 0) + 1, reservationId: reservation.id, lessonId: reservation.lesson.id, member: reservation.member.name, before: reservation.status, after: status, reason, at: new Date().toISOString() }, ...previous.changes] }));
  };

  const go = (next: Screen, id?: number) => {
    if (user.role === "ADMIN") {
      if (next === "today-lessons") next = "admin-lessons";
      if (next === "attendance-overview") next = "admin-lessons";
      if (next === "absence-requests") next = "admin-absences";
    }
    if (user.role === "INSTRUCTOR") {
      if (["today-lessons", "attendees", "attendance", "attendance-overview", "attendance-corrections"].includes(next)) {
        if (id === undefined) id = data.lessons.find(item => item.instructorId === user.id && seoulDate(item.startAt) === seoulDate(dashboardPreviewTime))?.id;
        next = "timetable";
      }
      if (next === "absence-requests") next = "absences";
    }
    if (!canAccessScreen(next, user.role, currentUser.permissions)) return;
    if (user.role === "MEMBER" && next === "reservation-detail" && id !== undefined) { setDialog(reservationDialog(id)); return; }
    if (id !== undefined) setSelectedId(id);
    else if (user.role === "INSTRUCTOR" && next === "timetable") setSelectedId(0);
    else if (next === "admin-settlement-create") setSelectedId(0);
    setScreen(next);
    window.scrollTo(0, 0);
  };

  const can = (permission: Permission) => user.role === "MEMBER" && currentUser.permissions.has(permission);

  return (
    <SidebarProvider className="member-sidebar-shell" style={{ "--sidebar-width": "var(--navigation-width)" } as CSSProperties}>
      <AppSidebar user={user} navigation={navigation} screen={screen} onNavigate={go} />
      <main className={`main-content${screen === "home" ? ` home-main${user.role === "MEMBER" ? " member-home-main" : ""}` : ""}`}>
        <AppHeader
          location={<><MobileMenuButton /><PageBreadcrumb screen={screen} role={user.role} /></>}
          name={name}
          unreadCount={unreadCount}
          onNotificationClick={() => go("notifications")}
          onLogout={onLogout}
        />
        <div className="content">
          {user.role === "INSTRUCTOR" && isInstructorScreen(screen) ? <InstructorPage screen={screen} user={user} data={data} setData={setData} state={instructorState} setState={setInstructorState} /> : user.role === "INSTRUCTOR" && screen === "instructor-lesson-detail" ? <InstructorLessonDetail lessonId={selectedId} user={user} canViewReservations={canAccessScreen("attendees", user.role, currentUser.permissions)} changes={instructorState.changes} onRecorded={onApiAttendanceRecorded} onBack={() => go("timetable", selectedId)} /> : screen === "notifications" ? <NotificationsPage role={user.role} onNavigate={go} /> : screen === "timetable" || screen === "classes" || screen === "admin-lessons" ? <LessonViews key={`${screen}-${user.role === "INSTRUCTOR" ? selectedId : ""}`} screen={screen} user={user} initialLessonId={user.role === "INSTRUCTOR" ? selectedId : 0} initialDate={user.role === "INSTRUCTOR" ? data.lessons.find(item => item.id === selectedId)?.startAt : undefined} canViewReservations={canAccessScreen("attendees", user.role, currentUser.permissions)} changes={instructorState.changes} onRecorded={onApiAttendanceRecorded} onNavigate={go} canReserve={can("reservation.create")} /> : screen === "home" && homeView === "loading" ? <DashboardLoading /> : screen === "home" && ["error", "expired", "forbidden"].includes(homeView) ? <DashboardError status={homeView === "expired" ? 401 : homeView === "forbidden" ? 403 : 500} onRetry={() => setHomeView("normal")} onLogin={onLogin} /> : user.role === "ADMIN" ? screen === "home" ? <QueryState query={admin}><HomePage user={user} data={viewData} screen="home" empty={homeView === "empty"} onNavigate={go} /></QueryState> : <AdminWorkspace key={`${screen}-${selectedId}`} screen={screen} id={selectedId} onNavigate={go} /> : screen === "home" || user.role !== "MEMBER" ? <HomePage user={user} data={data} screen={screen} empty={homeView === "empty"} onNavigate={go} onRecordAttendance={onRecordAttendance} /> : <MemberScreen
            screen={screen}
            selectedId={selectedId}
            onNavigate={go}
            onDialog={setDialog}
            canReserve={can("reservation.create")}
            canWaitlist={can("waitlist.create")}
            canCancel={can("reservation.cancel")}
            canUpdate={can("profile.update")}
            canChangePassword={can("password.change")}
          />}
        </div>
        <AppFooter />
      </main>
      {dialog && <Dialog dialog={dialog} onClose={() => setDialog(null)} />}
    </SidebarProvider>
  );
}

function LessonViews({ screen, user, onNavigate, canReserve, initialLessonId, initialDate, canViewReservations, changes, onRecorded }: { screen: Screen; user: DashboardUser; onNavigate: (screen: Screen, id?: number) => void; canReserve: boolean; initialLessonId: number; initialDate?: string; canViewReservations: boolean; changes: AttendanceChange[]; onRecorded: (reservation: api.Reservation, status: api.AttendanceRequestStatus, reason: string) => void }) {
  const [view, setView] = useState<'calendar' | 'list'>(screen === 'classes' || (user.role === 'INSTRUCTOR' && initialLessonId > 0) ? 'list' : 'calendar');
  const [selectedLessonId, setSelectedLessonId] = useState<number | null>(initialLessonId > 0 ? initialLessonId : null);
  return <><Tabs value={view} onValueChange={value => setView(value as 'calendar' | 'list')} className="lesson-views">
    <TabsList aria-label="수업 보기 방식"><TabsTrigger value="calendar">시간표</TabsTrigger><TabsTrigger value="list">목록</TabsTrigger></TabsList>
    <TabsContent value={view}>
      {user.role === 'ADMIN' && view === 'list' ? <AdminLessonsPage embedded onNavigate={onNavigate} /> : <TimetablePage user={user} onNavigate={onNavigate} canReserve={canReserve} view={view} initialDate={initialDate ? seoulDate(initialDate) : undefined} selectedLessonId={user.role === 'INSTRUCTOR' || user.role === 'ADMIN' ? selectedLessonId : undefined} onSelectLesson={user.role === 'INSTRUCTOR' || user.role === 'ADMIN' ? setSelectedLessonId : undefined} />}
    </TabsContent>
  </Tabs>{user.role === 'INSTRUCTOR' && <InstructorLessonRoster lessonId={selectedLessonId} user={user} canViewReservations={canViewReservations} changes={changes} onRecorded={onRecorded} />}{user.role === 'ADMIN' && view === 'calendar' && selectedLessonId !== null && <section className="instructor-roster" aria-label="선택한 수업의 예약자와 출결"><ReservationsList key={selectedLessonId} lessonId={selectedLessonId} onNavigate={onNavigate} compact /></section>}</>;
}

function AppSidebar({ user, navigation, screen, onNavigate }: { user: DashboardUser; navigation: NavigationItem[]; screen: Screen; onNavigate: (screen: Screen) => void }) {
  const { isMobile, setOpenMobile } = useSidebar();
  const [openMenu, setOpenMenu] = useState<string | null>(getMenuScreen(screen));
  useEffect(() => {
    setOpenMenu(getMenuScreen(screen));
  }, [screen]);
  const navigate = (target: Screen) => { onNavigate(target); if (isMobile) setOpenMobile(false); };
  return (
    <Sidebar className="member-sidebar" aria-label={`${roleLabels[user.role]} 사이드 메뉴`}>
      <SidebarHeader className="member-sidebar-header">
        <button type="button" className="member-sidebar-brand" aria-label="홈으로 이동" onClick={() => { onNavigate("home"); if (isMobile) setOpenMobile(false); }}>
          <BrandLogo variant="sidebar" />
        </button>
        {isMobile && <Button variant="cancel" onClick={() => setOpenMobile(false)}>메뉴 닫기</Button>}
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup><SidebarGroupContent><nav aria-label={`${roleLabels[user.role]} 메뉴`}><SidebarMenu>
        {navigation.map((item) => {
          const active = getMenuScreen(screen) === item.screen;
          const Icon = menuIcons[item.screen];
          if (item.children) return <Collapsible.Root key={item.screen} asChild open={openMenu === item.screen} onOpenChange={open => setOpenMenu(open ? item.screen : null)}>
            <SidebarMenuItem>
              <Collapsible.Trigger asChild><SidebarMenuButton variant="brand" size="lg" isActive={active}>
                <span className="member-navigation-label"><Icon aria-hidden="true" /><span>{item.label}</span></span><ChevronRight className="navigation-disclosure" aria-hidden="true" />
              </SidebarMenuButton></Collapsible.Trigger>
              <Collapsible.Content><SidebarMenuSub className="member-subnavigation">
                {item.children.map(child => {
                  const selected = child.activeScreens.includes(screen);
                  const ChildIcon = submenuIcons[child.screen] ?? Icon;
                  return <SidebarMenuSubItem key={child.screen}><SidebarMenuSubButton asChild isActive={selected} className="member-subnavigation-button">
                    <button type="button" aria-current={selected ? 'page' : undefined} onClick={() => navigate(child.screen)}><ChildIcon aria-hidden="true" /><span>{child.label}</span></button>
                  </SidebarMenuSubButton></SidebarMenuSubItem>;
                })}
              </SidebarMenuSub></Collapsible.Content>
            </SidebarMenuItem>
          </Collapsible.Root>;
          return <SidebarMenuItem key={item.screen}>
            <SidebarMenuButton variant="brand" size="lg" isActive={active} aria-current={active ? "page" : undefined} onClick={() => { onNavigate(item.screen); if (isMobile) setOpenMobile(false); }}>
              <span className="member-navigation-label"><Icon aria-hidden="true" /><span>{item.label}</span></span>
            </SidebarMenuButton>
          </SidebarMenuItem>;
        })}
        </SidebarMenu></nav></SidebarGroupContent></SidebarGroup>
      </SidebarContent>
    </Sidebar>
  );
}

function MobileMenuButton() {
  const { isMobile, openMobile, toggleSidebar } = useSidebar();
  if (!isMobile) return null;
  return <Button variant="outline" aria-expanded={openMobile} onClick={toggleSidebar}>메뉴 열기</Button>;
}

function MemberScreen({ screen, selectedId, onNavigate, onDialog, canReserve, canWaitlist, canCancel, canUpdate, canChangePassword }: { screen: Screen; selectedId: number; onNavigate: (screen: Screen, id?: number) => void; onDialog: (dialog: DialogState) => void; canReserve: boolean; canWaitlist: boolean; canCancel: boolean; canUpdate: boolean; canChangePassword: boolean }) {
  switch (screen) {

    case "lesson-detail": return <LessonDetailPage lessonId={selectedId} onBack={() => onNavigate("timetable")} onDialog={onDialog} canReserve={canReserve} canWaitlist={canWaitlist} />;
    case "passes": return <PassesPage onOpen={(id) => onNavigate("pass-detail", id)} />;
    case "pass-detail": return <PassDetailPage passId={selectedId} onBack={() => onNavigate("passes")} />;
    case "reservations": return <ReservationsPage onDialog={onDialog} canCancel={canCancel} />;
    case "profile": return <AccountPage onDialog={onDialog} canUpdate={canUpdate} canChangePassword={canChangePassword} />;
    default: return null;
  }
}

export { App };

