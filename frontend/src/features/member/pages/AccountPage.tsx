import type { DialogState } from "@/app/routes";
import { PageTitle } from "@/components/ui/PageTitle";
import { MenuPanel } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";

export function AccountPage({
  onDialog,
  canUpdate,
  canChangePassword,
}: {
  onDialog: (dialog: DialogState) => void;
  canUpdate: boolean;
  canChangePassword: boolean;
}) {
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
              onClick={() =>
                onDialog({
                  title: "내 프로필 수정",
                  body: "프로필 정보 수정 화면입니다.",
                  action: "프로필 수정",
                })
              }
            >
              프로필 수정
            </Button>
          )
        }
      >
        <dl className="menu-facts">
          {[
            ["이름", "홍길동"],
            ["이메일", "hong@example.com"],
            ["휴대전화", "010-1234-5678"],
          ].map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      </MenuPanel>
      <MenuPanel
        variant="settings"
        title="계정 보안"
        action={
          canChangePassword && (
            <Button
              variant="outline"
              onClick={() =>
                onDialog({
                  title: "비밀번호 변경",
                  body: "현재 비밀번호와 새 비밀번호를 입력합니다.",
                  action: "비밀번호 변경",
                })
              }
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
    </div>
  );
}
