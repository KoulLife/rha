import { useAllLessonTypes } from "./queries";
import { todayInSeoul } from "@/api/runtime";
import { PAGE_SIZE } from "@/components/ui/Pagination";
import { useState, type CSSProperties } from "react";
import { toast } from "sonner";
import * as api from "@/api/generated/api";
import type { Screen } from "@/app/routes";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/Dialog";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { MenuPanel } from "@/components/layout/MenuPanel";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import {
  AdminPage,
  AdminLinks,
  AdminForm,
  DataTable,
  QueryState,
  Filters,
  Facts,
  ActionDialog,
  ALL,
  options,
  filterValue,
  dateTime,
  textValue,
  numValue,
  money,
  type Values,
  type FormField,
  type Action,
} from "./AdminUI";
import { PassList, PaymentsList } from "./PassPages";
import { ReservationsList } from "./LessonPages";
import { AbsenceList } from "./InstructorPages";
import {
  fallbackInstructorColor,
  saveInstructorColor,
  useInstructorColors,
} from "@/features/instructor/colors";
export type Navigate = (screen: Screen, id?: number) => void;
const memberStates = [
  ALL,
  { value: "ACTIVE", label: "활성" },
  { value: "INACTIVE", label: "비활성" },
  { value: "WITHDRAWN", label: "탈퇴" },
  { value: "RESIGNED", label: "퇴사" },
];
const instructorStates = [
  ALL,
  { value: "ACTIVE", label: "재직" },
  { value: "INACTIVE", label: "비활성" },
  { value: "RESIGNED", label: "퇴사" },
];
const personFields: FormField[] = [
  { name: "name", label: "이름", required: true, maxLength: 50 },
  {
    name: "phoneNumber",
    label: "휴대전화",
    type: "tel",
    required: true,
    pattern: "01[0-9]{8,9}",
    description: "하이픈 없이 입력해 주세요.",
  },
  { name: "birthDate", label: "생년월일", type: "date", required: true, max: todayInSeoul() },
  { name: "address", label: "주소", maxLength: 200 },
];
const rgbLabel = (hex: string) =>
  `${parseInt(hex.slice(1, 3), 16)}, ${parseInt(hex.slice(3, 5), 16)}, ${parseInt(hex.slice(5, 7), 16)}`;

export function MembersPage({
  instructor = false,
  onNavigate,
}: {
  instructor?: boolean;
  onNavigate: Navigate;
}) {
  const [filters, setFilters] = useState<Values>({}),
    [page, setPage] = useState(1),
    [action, setAction] = useState<Action | null>(null),
    [rateInstructorId, setRateInstructorId] = useState<number | null>(null);
  const [colorInstructor, setColorInstructor] = useState<api.Member | null>(null),
    [previewColor, setPreviewColor] = useState("#526B61");
  const instructorColors = useInstructorColors();
  const query = api.useGetMembers({
    role: instructor ? "INSTRUCTOR" : "MEMBER",
    keyword: filterValue(filters, "keyword"),
    status: filterValue(filters, "status") as api.MemberStatus | undefined,
    page,
    size: PAGE_SIZE,
  });
  const create = api.useCreateMember();
  const label = instructor ? "강사" : "회원";
  const registerInstructor = () =>
    setAction({
      title: "강사 등록",
      description: "비밀번호 설정 링크를 받을 이메일과 기본 정보를 입력해 주세요.",
      submitLabel: "강사 등록",
      fields: [{ name: "email", label: "이메일", type: "email", required: true }, ...personFields],
      onSubmit: async (v) => {
        const created = await create.mutateAsync({
          data: {
            email: textValue(v, "email"),
            name: textValue(v, "name").trim(),
            phoneNumber: textValue(v, "phoneNumber"),
            birthDate: textValue(v, "birthDate"),
            address: textValue(v, "address"),
            role: "INSTRUCTOR",
          },
        });
        onNavigate("admin-instructor-detail", created.id);
      },
    });
  return (
    <AdminPage
      title={`${label} 관리`}
      description={`${label}의 계정 정보와 이용 현황을 한곳에서 확인합니다.`}
    >
      {instructor && (
        <p className="menu-note">
          선택한 색상은 이 브라우저에 저장됩니다. 다른 기기와 공유하는 서버 저장은 기능 적용 필요.
        </p>
      )}
      <Filters
        fields={[
          { name: "keyword", label: "이름·이메일 검색" },
          {
            name: "status",
            label: instructor ? "강사 구분" : "상태",
            type: "select",
            options: instructor ? instructorStates : memberStates,
          },
        ]}
        action={instructor ? <Button onClick={registerInstructor}>강사 등록</Button> : undefined}
        onApply={(v) => {
          setFilters(v);
          setPage(1);
        }}
      />
      <QueryState query={query}>
        <DataTable
          rows={query.data?.content ?? []}
          pageInfo={query.data?.pageInfo}
          onPage={setPage}
          columns={[
            ...(instructor
              ? [
                  {
                    label: "색상 지정",
                    render: (r: api.Member) => (
                      <Button
                        variant="ghost"
                        size="icon"
                        className="admin-color-trigger"
                        aria-label={`${r.name} 시간표 색상 지정`}
                        onClick={() => {
                          setColorInstructor(r);
                          setPreviewColor(instructorColors[r.id] ?? fallbackInstructorColor(r.id));
                        }}
                      >
                        <span
                          className="admin-instructor-swatch"
                          data-color={r.id % 6}
                          style={
                            instructorColors[r.id]
                              ? ({ "--instructor-color": instructorColors[r.id] } as CSSProperties)
                              : undefined
                          }
                          aria-hidden="true"
                        />
                      </Button>
                    ),
                  },
                ]
              : []),
            { label: "이름", render: (r) => <strong>{r.name}</strong> },
            { label: "이메일", render: (r) => r.email },
            { label: "휴대전화", render: (r) => r.phoneNumber },
            {
              label: instructor ? "구분" : "상태",
              render: (r) => (
                <StatusBadge
                  domain="member"
                  status={r.status}
                />
              ),
            },
            { label: "등록일", render: (r) => dateTime(r.createdAt) },
            ...(instructor
              ? [{ label: "퇴사일", render: (r: api.Member) => r.resignationDate ?? "" }]
              : []),
            {
              label: instructor ? "관리" : "상세",
              render: (r) =>
                instructor ? (
                  <div className="menu-actions">
                    <Button
                      variant="outline"
                      onClick={() => setRateInstructorId(r.id)}
                    >
                      수당 기준
                    </Button>
                    <Button
                      variant="ghost"
                      onClick={() => onNavigate("admin-instructor-settlements", r.id)}
                    >
                      정산 내역
                    </Button>
                  </div>
                ) : (
                  <Button
                    variant="ghost"
                    onClick={() => onNavigate("admin-member-detail", r.id)}
                    aria-label={`${r.name} 상세 보기`}
                  >
                    상세 보기
                  </Button>
                ),
            },
          ]}
        />
      </QueryState>
      {action && (
        <ActionDialog
          action={action}
          onClose={() => setAction(null)}
        />
      )}
      {rateInstructorId !== null && (
        <InstructorPayRatesDialog
          instructorId={rateInstructorId}
          onClose={() => setRateInstructorId(null)}
        />
      )}
      {colorInstructor && (
        <Dialog
          dialog={{
            title: `${colorInstructor.name} · 시간표 색상 지정`,
            body: "강사별 시간표 표시 색상을 선택합니다.",
          }}
          onClose={() => setColorInstructor(null)}
          closeLabel="취소"
          footerAction={
            <Button
              onClick={() => {
                if (saveInstructorColor(colorInstructor.id, previewColor)) {
                  toast.success("이 브라우저에 색상을 저장했습니다.");
                  setColorInstructor(null);
                } else toast.error("색상을 저장하지 못했습니다.");
              }}
            >
              색상 저장
            </Button>
          }
        >
          <div className="menu-stack">
            <label
              className="admin-color-picker"
              htmlFor="instructor-color"
            >
              <span>지정 색상</span>
              <input
                id="instructor-color"
                type="color"
                value={previewColor}
                onChange={(event) => setPreviewColor(event.target.value.toUpperCase())}
              />
            </label>
            <p className="menu-note">
              <span
                className="admin-instructor-swatch"
                style={{ "--instructor-color": previewColor } as CSSProperties}
                aria-hidden="true"
              />{" "}
              RGB {rgbLabel(previewColor)} · {previewColor.toUpperCase()}
            </p>
            <p className="menu-note">이 브라우저에 저장됩니다. 서버 연동은 기능 적용 필요.</p>
            {/* TODO(api): 강사 RGB 색상 필드와 저장 API가 계약에 추가되면 브라우저 저장소를 교체한다. */}
          </div>
        </Dialog>
      )}
    </AdminPage>
  );
}
function InstructorPayRatesDialog({
  instructorId,
  onClose,
}: {
  instructorId: number;
  onClose: () => void;
}) {
  const query = api.useGetPayRates(instructorId),
    types = useAllLessonTypes({ size: 100 }),
    save = api.useUpdatePayRates();
  return (
    <Dialog
      dialog={{
        title: query.data ? `${query.data.instructor.name} · 수당 기준` : "강사 수당 기준",
        body: "수업 유형별 한 회당 단가를 등록하거나 수정합니다.",
      }}
      onClose={onClose}
    >
      <QueryState query={query}>
        <QueryState query={types}>
          {query.data && (
            <AdminForm
              key={`${instructorId}-${JSON.stringify(query.data)}-${JSON.stringify(types.data)}`}
              fields={(types.data?.content ?? []).flatMap((type) => [
                {
                  name: `enabled-${type.id}`,
                  label: `${type.name} 단가 사용`,
                  type: "checkbox" as const,
                },
                {
                  name: `price-${type.id}`,
                  label: `${type.name} 단가 (원)`,
                  type: "number" as const,
                  min: 0,
                },
              ])}
              initial={Object.fromEntries(
                (types.data?.content ?? []).flatMap((type) => {
                  const rate = query.data!.payRates.find((item) => item.lessonType.id === type.id);
                  return [
                    [`enabled-${type.id}`, !!rate],
                    [`price-${type.id}`, String(rate?.unitPrice ?? 0)],
                  ];
                }),
              )}
              submitLabel={query.data.payRates.length ? "수당 기준 수정" : "수당 기준 등록"}
              onSubmit={async (values) => {
                const payRates = (types.data?.content ?? [])
                  .filter((type) => values[`enabled-${type.id}`])
                  .map((type) => ({
                    lessonTypeId: type.id,
                    unitPrice: numValue(values, `price-${type.id}`),
                  }));
                await save.mutateAsync({ memberId: instructorId, data: { payRates } });
                onClose();
              }}
            />
          )}
        </QueryState>
      </QueryState>
    </Dialog>
  );
}
export function MemberDetail({
  id,
  instructor = false,
  onNavigate,
}: {
  id: number;
  instructor?: boolean;
  onNavigate: Navigate;
}) {
  const query = api.useGetMember(id),
    update = api.useUpdateMember(),
    change = api.useChangeMemberStatus();
  const [action, setAction] = useState<Action | null>(null),
    [showPayRateDialog, setShowPayRateDialog] = useState(false);
  const p = query.data;
  return (
    <AdminPage
      title={p ? `${p.name} · ${instructor ? "강사" : "회원"} 상세` : "정보 불러오는 중"}
      description="기본 정보와 관련 업무를 확인합니다."
      action={
        <Button
          variant="ghost"
          onClick={() => onNavigate(instructor ? "admin-instructors" : "admin-members")}
        >
          목록으로
        </Button>
      }
    >
      <QueryState query={query}>
        {p && (
          <>
            <MenuPanel
              title="계정 정보"
              action={
                <div className="menu-actions">
                  <Button
                    variant="outline"
                    onClick={() =>
                      setAction({
                        title: "기본 정보 수정",
                        description: "이메일은 변경할 수 없습니다.",
                        submitLabel: "정보 저장",
                        fields: personFields,
                        initial: {
                          name: p.name,
                          phoneNumber: p.phoneNumber,
                          birthDate: p.birthDate,
                          address: p.address ?? "",
                        },
                        onSubmit: (v) =>
                          update.mutateAsync({
                            memberId: id,
                            data: {
                              name: textValue(v, "name").trim(),
                              phoneNumber: textValue(v, "phoneNumber"),
                              birthDate: textValue(v, "birthDate"),
                              address: textValue(v, "address") || null,
                            },
                          }),
                      })
                    }
                  >
                    정보 수정
                  </Button>
                  <Button
                    variant={p.status === "ACTIVE" ? "warning" : "outline"}
                    onClick={() =>
                      setAction({
                        title: "계정 상태 변경",
                        description:
                          p.status === "ACTIVE"
                            ? "비활성 계정은 로그인할 수 없습니다. 강사는 시작 전 수업을 먼저 인계해야 합니다."
                            : "계정을 활성화합니다. 퇴사했던 강사의 권한은 자동으로 복구되지 않습니다.",
                        submitLabel: p.status === "ACTIVE" ? "비활성화" : "활성화",
                        submitVariant: p.status === "ACTIVE" ? "warning" : "default",
                        danger: true,
                        onSubmit: () =>
                          change.mutateAsync({
                            memberId: id,
                            data: { status: p.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" },
                          }),
                      })
                    }
                  >
                    상태 변경
                  </Button>
                </div>
              }
            >
              <Facts
                items={[
                  ["이름", p.name],
                  ["이메일", p.email],
                  ["휴대전화", p.phoneNumber],
                  ["생년월일", p.birthDate],
                  ["주소", p.address],
                  ["등록 시각", dateTime(p.createdAt)],
                  [
                    "계정 상태",
                    <StatusBadge
                      domain="member"
                      status={p.status}
                    />,
                  ],
                  ...(instructor
                    ? [["퇴사일", p.resignationDate ?? "미등록"] as [string, string]]
                    : []),
                ]}
              />
            </MenuPanel>
            {instructor && (
              <AdminLinks
                items={[
                  { label: "퇴사 관리", onClick: () => onNavigate("admin-resignations", id) },
                  {
                    label: "정산 내역",
                    onClick: () => onNavigate("admin-instructor-settlements", id),
                  },
                  { label: "수당 기준", onClick: () => setShowPayRateDialog(true) },
                ]}
              />
            )}
            {instructor ? (
              <>
                <details className="admin-detail-section">
                  <summary>권한 관리</summary>
                  <Permissions member={p} />
                </details>
                <details className="admin-detail-section">
                  <summary>부재 내역</summary>
                  <AbsenceList
                    instructorId={id}
                    onNavigate={onNavigate}
                  />
                </details>
                <details className="admin-detail-section">
                  <summary>수업 가능시간</summary>
                  <Availability id={id} />
                </details>
              </>
            ) : (
              <Tabs
                defaultValue="passes"
                className="menu-tabs"
              >
                <TabsList>
                  <TabsTrigger value="passes">이용권</TabsTrigger>
                  <TabsTrigger value="reservations">예약·출결</TabsTrigger>
                  <TabsTrigger value="payments">결제 내역</TabsTrigger>
                </TabsList>
                <TabsContent value="passes">
                  <PassList
                    memberId={id}
                    onNavigate={onNavigate}
                  />
                </TabsContent>
                <TabsContent value="reservations">
                  <ReservationsList
                    memberId={id}
                    onNavigate={onNavigate}
                  />
                </TabsContent>
                <TabsContent value="payments">
                  <PaymentsList
                    memberId={id}
                    onNavigate={onNavigate}
                  />
                </TabsContent>
              </Tabs>
            )}
          </>
        )}
      </QueryState>
      {action && (
        <ActionDialog
          action={action}
          onClose={() => setAction(null)}
        />
      )}{" "}
      {showPayRateDialog && (
        <InstructorPayRatesDialog
          instructorId={id}
          onClose={() => setShowPayRateDialog(false)}
        />
      )}
    </AdminPage>
  );
}
function Permissions({ member }: { member: api.Member }) {
  const catalog = api.useGetPermissions(),
    current = api.useGetMemberPermissions(member.id),
    save = api.useUpdateMemberPermissions();
  const [action, setAction] = useState<Action | null>(null);
  const unavailable =
    current.data?.permissions.filter(
      (code) => !catalog.data?.permissions.some((p) => p.code === code),
    ) ?? [];
  return (
    <QueryState query={catalog}>
      <QueryState query={current}>
        <MenuPanel title="보유 권한">
          <AdminForm
            key={JSON.stringify(current.data)}
            fields={(catalog.data?.permissions ?? []).map((p) => ({
              name: p.code,
              label: p.name,
              type: "checkbox",
              description: p.description,
              disabled: member.status === "RESIGNED" || unavailable.length > 0,
            }))}
            initial={Object.fromEntries(
              (current.data?.permissions ?? []).map((code) => [code, true]),
            )}
            submitLabel="권한 변경 확인"
            onSubmit={async (v) => {
              setAction({
                title: "권한 변경",
                description:
                  "선택한 권한으로 전체 교체합니다. 선택하지 않은 기존 권한은 회수됩니다.",
                submitLabel: "권한 저장",
                danger: true,
                onSubmit: () =>
                  save.mutateAsync({
                    memberId: member.id,
                    data: {
                      permissions: Object.entries(v)
                        .filter(([, checked]) => checked === true)
                        .map(([code]) => code),
                    },
                  }),
              });
            }}
          >
            {member.status === "RESIGNED" && (
              <p className="menu-note">퇴사한 강사는 활성화 후 권한을 설정할 수 있습니다.</p>
            )}
            {unavailable.length > 0 && (
              <p className="menu-note">
                비활성 유형의 기존 권한이 있어 변경을 제한합니다. 해당 유형을 활성화한 뒤 변경해
                주세요.
              </p>
            )}
          </AdminForm>
        </MenuPanel>
        {action && (
          <ActionDialog
            action={action}
            onClose={() => setAction(null)}
          />
        )}
      </QueryState>
    </QueryState>
  );
}
function Availability({ id }: { id: number }) {
  const query = api.useGetInstructorAvailability(id),
    save = api.useUpdateInstructorAvailability();
  const labels = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"],
    days = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] as const;
  return (
    <QueryState query={query}>
      <MenuPanel title="요일별 수업 가능시간">
        <AdminForm
          key={JSON.stringify(query.data)}
          fields={days.flatMap((day, i) => [
            { name: `${day}-enabled`, label: `${labels[i]} 수업 가능`, type: "checkbox" as const },
            { name: `${day}-start`, label: `${labels[i]} 시작`, type: "time" as const },
            { name: `${day}-end`, label: `${labels[i]} 종료`, type: "time" as const },
          ])}
          initial={Object.fromEntries(
            days.flatMap((day) => {
              const slot = query.data?.slots.find((s) => s.dayOfWeek === day);
              return [
                [`${day}-enabled`, !!slot],
                [`${day}-start`, slot?.startTime ?? "09:00"],
                [`${day}-end`, slot?.endTime ?? "21:00"],
              ];
            }),
          )}
          submitLabel="가능시간 저장"
          onSubmit={(v) =>
            save.mutateAsync({
              memberId: id,
              data: {
                slots: days
                  .filter((day) => v[`${day}-enabled`])
                  .map((day) => ({
                    dayOfWeek: day,
                    startTime: textValue(v, `${day}-start`),
                    endTime: textValue(v, `${day}-end`),
                  })),
              },
            })
          }
        />
      </MenuPanel>
    </QueryState>
  );
}
