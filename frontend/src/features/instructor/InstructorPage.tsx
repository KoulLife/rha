import type { Dispatch, SetStateAction } from "react";
import { toast } from "sonner";
import type { Screen } from "@/app/routes";
import { seoulDate, type DashboardData, type DashboardUser } from "../dashboard/model";
import { dashboardPreviewTime } from "../dashboard/preview";
import { InstructorAbsencesPage } from "./InstructorAbsencesPage";
import { absenceRestriction, canWithdrawAbsence, type InstructorState } from "./model";
import "./instructor.css";

export type InstructorScreen = "absences";
export function isInstructorScreen(screen: Screen): screen is InstructorScreen {
  return screen === "absences";
}

export function InstructorPage({
  user,
  data,
  setData,
  state,
  setState,
}: {
  screen: InstructorScreen;
  user: DashboardUser;
  data: DashboardData;
  setData: Dispatch<SetStateAction<DashboardData>>;
  state: InstructorState;
  setState: Dispatch<SetStateAction<InstructorState>>;
}) {
  return (
    <InstructorAbsencesPage
      user={user}
      data={data}
      state={state}
      onCreate={(detail, reason) => {
        const error = absenceRestriction(
          data.absences,
          state.absenceDetails,
          user.id,
          detail.startAt,
          detail.endAt,
          dashboardPreviewTime,
        );
        if (error) {
          toast.error(error);
          return;
        }
        const id = Math.max(0, ...data.absences.map((item) => item.id)) + 1;
        setState((previous) => ({
          ...previous,
          absenceDetails: { ...previous.absenceDetails, [id]: detail },
        }));
        setData((previous) => ({
          ...previous,
          absences: [
            {
              id,
              instructorId: user.id,
              instructor: user.name,
              from: seoulDate(detail.startAt),
              to: seoulDate(detail.endAt),
              reason,
              status: "REQUESTED",
            },
            ...previous.absences,
          ],
        }));
        toast.success("부재 신청이 접수되었습니다.");
      }}
      onWithdraw={(id) => {
        const absence = data.absences.find((item) => item.id === id);
        if (!absence || !canWithdrawAbsence(absence, user)) {
          toast.error("승인 대기 중인 본인 신청만 철회할 수 있습니다.");
          return;
        }
        setData((previous) => ({
          ...previous,
          absences: previous.absences.map((item) =>
            item.id === id ? { ...item, status: "CANCELED" } : item,
          ),
        }));
        setState((previous) => ({
          ...previous,
          absenceDetails: {
            ...previous.absenceDetails,
            [id]: { ...previous.absenceDetails[id], processedAt: dashboardPreviewTime },
          },
        }));
        toast.success("부재 신청을 철회했습니다.");
      }}
    />
  );
}
