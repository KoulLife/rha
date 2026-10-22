import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import * as api from "@/api/generated/api";
import type { Screen } from "@/app/routes";
import type { DashboardUser } from "../dashboard/model";
import { QueryState } from "../admin/AdminUI";
import { useAllAbsences } from "../admin/queries";
import { InstructorAbsencesPage } from "./InstructorAbsencesPage";
import { instructorAbsenceState, instructorDashboardData } from "./apiData";
import "./instructor.css";

export type InstructorScreen = "absences";
export function isInstructorScreen(screen: Screen): screen is InstructorScreen {
  return screen === "absences";
}

export function InstructorPage({ user }: { screen: InstructorScreen; user: DashboardUser }) {
  const query = useAllAbsences({ instructorId: user.id });
  const client = useQueryClient();
  const create = api.useCreateAbsence();
  const withdraw = api.useCancelAbsence();
  const rows = query.data?.content ?? [];
  const refresh = () =>
    Promise.all([
      client.invalidateQueries({ queryKey: api.getGetAbsencesQueryKey() }),
      client.invalidateQueries({ queryKey: api.getGetAbsencesInfiniteQueryKey() }),
    ]);
  return (
    <QueryState query={query}>
      <InstructorAbsencesPage
        user={user}
        data={instructorDashboardData(user.id, [], [], rows)}
        state={instructorAbsenceState(rows)}
        onCreate={async (detail, reason) => {
          await create.mutateAsync({
            data: { startAt: detail.startAt, endAt: detail.endAt, ...(reason ? { reason } : {}) },
          });
          await refresh();
          toast.success("부재 신청이 접수되었습니다.");
        }}
        onWithdraw={async (id) => {
          await withdraw.mutateAsync({ absenceId: id });
          await refresh();
          toast.success("부재 신청을 철회했습니다.");
        }}
      />
    </QueryState>
  );
}
