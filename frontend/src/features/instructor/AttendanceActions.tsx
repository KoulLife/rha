import { useId, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/Dialog";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { StatusBadge } from "@/components/ui/StatusBadge";
import type { DashboardReservation } from "../dashboard/model";
import type { AttendanceStatus } from "./model";
import { InstructorConfirm, InstructorSelect } from "./InstructorControls";
import "./attendance-actions.css";

export type RecordAttendance = (id: number, status: AttendanceStatus, reason: string) => void;

export function AttendanceActions({
  reservation,
  restriction,
  onRecord,
}: {
  reservation: DashboardReservation;
  restriction: string | null;
  onRecord: RecordAttendance;
}) {
  const [editing, setEditing] = useState(false);
  const [pending, setPending] = useState<{ status: AttendanceStatus; reason: string } | null>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const recorded = reservation.status === "ATTENDED" || reservation.status === "NO_SHOW";
  return (
    <div
      className="attendance-actions"
      title={restriction ?? undefined}
    >
      {recorded ? (
        <Button
          ref={trigger}
          variant="outline"
          size="sm"
          disabled={!!restriction}
          aria-label={`${reservation.member} 출결 수정`}
          onClick={() => setEditing(true)}
        >
          출결 수정
        </Button>
      ) : reservation.status === "RESERVED" ? (
        <>
          <Button
            size="sm"
            disabled={!!restriction}
            aria-label={`${reservation.member} 출석`}
            onClick={(event) => {
              trigger.current = event.currentTarget;
              setPending({ status: "ATTENDED", reason: "" });
            }}
          >
            출석
          </Button>
          <Button
            variant="destructive"
            size="sm"
            disabled={!!restriction}
            aria-label={`${reservation.member} 노쇼`}
            onClick={(event) => {
              trigger.current = event.currentTarget;
              setPending({ status: "NO_SHOW", reason: "" });
            }}
          >
            노쇼
          </Button>
        </>
      ) : null}
      {editing && (
        <AttendanceCorrection
          reservation={reservation}
          onClose={() => setEditing(false)}
          onSubmit={(status, reason) => {
            setEditing(false);
            setPending({ status, reason });
          }}
        />
      )}
      {pending && (
        <InstructorConfirm
          returnFocus={trigger.current}
          title={recorded ? "출결 수정 확인" : "출결 처리 확인"}
          description={`${reservation.member}님의 출결을 ${pending.status === "ATTENDED" ? "출석으로" : "노쇼로"} ${recorded ? "수정" : "기록"}합니다.${pending.reason ? `\n정정 사유: ${pending.reason}` : ""}\n이용권은 추가로 차감되지 않습니다.`}
          action={recorded ? "수정 확정" : "출결 확정"}
          onClose={() => setPending(null)}
          onConfirm={() => {
            onRecord(reservation.id, pending.status, pending.reason);
            setPending(null);
          }}
        />
      )}
    </div>
  );
}

function AttendanceCorrection({
  reservation,
  onClose,
  onSubmit,
}: {
  reservation: DashboardReservation;
  onClose: () => void;
  onSubmit: (status: AttendanceStatus, reason: string) => void;
}) {
  const id = useId();
  const [status, setStatus] = useState<AttendanceStatus>(
    reservation.status === "ATTENDED" ? "NO_SHOW" : "ATTENDED",
  );
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<{ reason: string }>();
  return (
    <Dialog
      dialog={{
        title: "출결 수정",
        body: "변경할 출결과 정정 사유를 입력해 주세요.",
        action: "변경 내용 확인",
      }}
      formId={id}
      onClose={onClose}
    >
      <form
        id={id}
        className="attendance-correction"
        onSubmit={handleSubmit((values) => onSubmit(status, values.reason.trim()))}
      >
        <div className="attendance-current">
          <strong>{reservation.member}</strong>
          <StatusBadge
            domain="reservation"
            status={reservation.status}
          />
        </div>
        <InstructorSelect
          id={`${id}-status`}
          label="변경할 출결"
          value={status}
          onChange={(value) => setStatus(value as AttendanceStatus)}
          options={[
            { value: "ATTENDED", label: "출석" },
            { value: "NO_SHOW", label: "노쇼" },
          ]}
        />
        <Field data-invalid={!!errors.reason}>
          <FieldLabel htmlFor={`${id}-reason`}>정정 사유</FieldLabel>
          <Textarea
            id={`${id}-reason`}
            maxLength={500}
            placeholder="출결을 변경하는 사유를 입력해 주세요"
            aria-invalid={!!errors.reason}
            aria-describedby={errors.reason ? `${id}-error` : undefined}
            {...register("reason", {
              validate: (value) => !!value.trim() || "정정 사유를 입력해 주세요.",
              maxLength: 500,
            })}
          />
          <FieldError
            id={`${id}-error`}
            errors={[errors.reason]}
          />
        </Field>
      </form>
    </Dialog>
  );
}
