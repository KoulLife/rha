import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import * as api from "@/api/generated/api";
import { Dialog } from "@/components/ui/Dialog";
import { dateTime, errorMessage } from "@/features/admin/AdminUI";
import type { Screen } from "@/app/routes";
import type { UserRole } from "@/config/access";

type Navigate = (screen: Screen, id?: number) => void;

function notificationDestination(role: UserRole, notification: api.Notification): [Screen, number?] | null {
  if (!notification.relatedType || !notification.relatedId) return null;
  const id = notification.relatedId;
  switch (notification.relatedType.toUpperCase()) {
    case "LESSON": return [role === "ADMIN" ? "admin-lesson-detail" : role === "MEMBER" ? "lesson-detail" : "today-lessons", id];
    case "RESERVATION": return [role === "MEMBER" ? "reservation-detail" : role === "INSTRUCTOR" ? "attendance-overview" : "admin-lessons", id];
    case "ABSENCE": return role === "ADMIN" ? ["admin-absence-detail", id] : role === "INSTRUCTOR" ? ["absence-requests"] : null;
    case "PASS": return role === "ADMIN" ? ["admin-pass-detail", id] : role === "MEMBER" ? ["pass-detail", id] : null;
    case "SETTLEMENT": return role === "ADMIN" ? ["admin-settlement-detail", id] : null;
    case "MEMBER": return role === "ADMIN" ? ["admin-member-detail", id] : null;
    case "INSTRUCTOR": return role === "ADMIN" ? ["admin-instructor-detail", id] : null;
    case "PAYMENT": return role === "ADMIN" ? ["admin-payments"] : null;
    default: return null;
  }
}

export const notificationCategories: Record<string, string> = {
  ABSENCE_REQUESTED: "부재 신청", ABSENCE_APPROVED: "부재 승인", ABSENCE_REJECTED: "부재 반려",
  LESSON_CANCELED: "수업 취소", RESERVATION_CANCELED: "예약 취소",
};

export function useNotificationDetail(role: UserRole, onNavigate: Navigate) {
  const [selected, setSelected] = useState<api.Notification | null>(null);
  const client = useQueryClient();
  const read = api.useReadNotification();

  const open = async (notification: api.Notification) => {
    try {
      if (!notification.read) {
        await read.mutateAsync({ notificationId: notification.id });
        await client.invalidateQueries();
      }
      setSelected({ ...notification, read: true });
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  const destination = selected && notificationDestination(role, selected);
  const dialog = selected && <Dialog
    dialog={{ title: selected.title, body: `${selected.message}\n\n분류: ${notificationCategories[selected.type] ?? "안내"}\n받은 시간: ${dateTime(selected.createdAt)}`, action: destination ? "관련 페이지로 이동" : undefined, actionVariant: "ghost" }}
    onClose={() => setSelected(null)}
    onConfirm={() => { if (destination) onNavigate(...destination); }}
  />;

  return { open, dialog };
}
