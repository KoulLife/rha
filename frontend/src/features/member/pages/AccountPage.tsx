import { useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  getGetMyProfileQueryKey,
  useChangeMyPassword,
  useGetMyProfile,
  useUpdateMyProfile,
  type MemberUpdateRequest,
} from "@/api/generated/api";
import { PageTitle } from "@/components/ui/PageTitle";
import { Dialog } from "@/components/ui/Dialog";
import { MenuEmpty, MenuPanel } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { DatePicker } from "@/components/ui/date-picker";
import { todayInSeoul } from "@/api/runtime";

export function AccountPage({
  canUpdate,
  canChangePassword,
}: {
  canUpdate: boolean;
  canChangePassword: boolean;
}) {
  const client = useQueryClient();
  const profile = useGetMyProfile();
  const update = useUpdateMyProfile();
  const changePassword = useChangeMyPassword();
  const [dialog, setDialog] = useState<"profile" | "password" | null>(null);
  const [form, setForm] = useState({ name: "", phoneNumber: "", birthDate: "", address: "" });
  const [password, setPassword] = useState({ currentPassword: "", newPassword: "" });

  const openProfile = () => {
    if (!profile.data) return;
    setForm({
      name: profile.data.name,
      phoneNumber: profile.data.phoneNumber,
      birthDate: profile.data.birthDate,
      address: profile.data.address ?? "",
    });
    setDialog("profile");
  };

  const saveProfile = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!form.birthDate) {
      toast.error("생년월일을 선택해 주세요.");
      return;
    }
    const data: MemberUpdateRequest = {
      name: form.name.trim(),
      phoneNumber: form.phoneNumber.replace(/-/g, ""),
      birthDate: form.birthDate,
      address: form.address.trim() || null,
    };
    try {
      await update.mutateAsync({ data });
      await client.invalidateQueries({ queryKey: getGetMyProfileQueryKey() });
      setDialog(null);
      toast.success("프로필을 수정했습니다.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "프로필을 수정하지 못했습니다.");
    }
  };

  const savePassword = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    try {
      await changePassword.mutateAsync({ data: password });
      setPassword({ currentPassword: "", newPassword: "" });
      setDialog(null);
      toast.success("비밀번호를 변경했습니다.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "비밀번호를 변경하지 못했습니다.");
    }
  };

  return (
    <div className="menu-page">
      <PageTitle
        title="내 계정"
        description="내 프로필과 계정 보안을 관리합니다."
      />
      <MenuPanel
        variant="settings"
        title="내 프로필"
        action={
          canUpdate && (
            <Button
              variant="outline"
              disabled={!profile.data}
              onClick={openProfile}
            >
              프로필 수정
            </Button>
          )
        }
      >
        {profile.isPending ? (
          <MenuEmpty
            title="프로필을 불러오는 중입니다"
            description="잠시만 기다려 주세요."
          />
        ) : profile.isError ? (
          <MenuEmpty
            title="프로필을 불러오지 못했습니다"
            description="잠시 후 다시 확인해 주세요."
          />
        ) : (
          <dl className="menu-facts">
            {[
              ["이름", profile.data.name],
              ["이메일", profile.data.email],
              ["휴대전화", profile.data.phoneNumber],
              ["생년월일", profile.data.birthDate],
              ["주소", profile.data.address || "-"],
            ].map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        )}
        {profile.isError && (
          <Button
            variant="outline"
            onClick={() => void profile.refetch()}
          >
            다시 시도
          </Button>
        )}
      </MenuPanel>
      <MenuPanel
        variant="settings"
        title="계정 보안"
        action={
          canChangePassword && (
            <Button
              variant="outline"
              onClick={() => {
                setPassword({ currentPassword: "", newPassword: "" });
                setDialog("password");
              }}
            >
              비밀번호 변경
            </Button>
          )
        }
      >
        <div className="menu-stack">
          <h3>비밀번호</h3>
          <p className="menu-muted">정기적으로 비밀번호를 변경해 계정을 안전하게 보호하세요.</p>
        </div>
      </MenuPanel>
      {dialog === "profile" && (
        <Dialog
          dialog={{
            title: "내 프로필 수정",
            body: "프로필 정보를 수정합니다.",
            action: update.isPending ? "저장 중" : "저장",
          }}
          formId="member-profile-form"
          onClose={() => {
            if (!update.isPending) setDialog(null);
          }}
          footerAction={
            <Button
              type="submit"
              form="member-profile-form"
              disabled={update.isPending}
            >
              {update.isPending ? "저장 중" : "저장"}
            </Button>
          }
        >
          <form
            id="member-profile-form"
            onSubmit={(event) => void saveProfile(event)}
          >
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="member-name">이름</FieldLabel>
                <Input
                  id="member-name"
                  required
                  maxLength={50}
                  value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="member-phone">휴대전화</FieldLabel>
                <Input
                  id="member-phone"
                  required
                  pattern="01[0-9]{8,9}"
                  value={form.phoneNumber}
                  onChange={(event) => setForm({ ...form, phoneNumber: event.target.value })}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="member-birth">생년월일</FieldLabel>
                <DatePicker
                  id="member-birth"
                  value={form.birthDate}
                  max={todayInSeoul()}
                  onChange={(birthDate) => setForm({ ...form, birthDate })}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="member-address">주소</FieldLabel>
                <Input
                  id="member-address"
                  maxLength={200}
                  value={form.address}
                  onChange={(event) => setForm({ ...form, address: event.target.value })}
                />
              </Field>
            </FieldGroup>
          </form>
        </Dialog>
      )}
      {dialog === "password" && (
        <Dialog
          dialog={{
            title: "비밀번호 변경",
            body: "현재 비밀번호와 새 비밀번호를 입력합니다.",
            action: changePassword.isPending ? "변경 중" : "변경",
          }}
          formId="member-password-form"
          onClose={() => {
            if (!changePassword.isPending) {
              setPassword({ currentPassword: "", newPassword: "" });
              setDialog(null);
            }
          }}
          footerAction={
            <Button
              type="submit"
              form="member-password-form"
              disabled={changePassword.isPending}
            >
              {changePassword.isPending ? "변경 중" : "변경"}
            </Button>
          }
        >
          <form
            id="member-password-form"
            onSubmit={(event) => void savePassword(event)}
          >
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="current-password">현재 비밀번호</FieldLabel>
                <Input
                  id="current-password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password.currentPassword}
                  onChange={(event) =>
                    setPassword({ ...password, currentPassword: event.target.value })
                  }
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="new-password">새 비밀번호</FieldLabel>
                <Input
                  id="new-password"
                  type="password"
                  autoComplete="new-password"
                  required
                  minLength={8}
                  maxLength={64}
                  value={password.newPassword}
                  onChange={(event) =>
                    setPassword({ ...password, newPassword: event.target.value })
                  }
                />
              </Field>
            </FieldGroup>
          </form>
        </Dialog>
      )}
    </div>
  );
}
