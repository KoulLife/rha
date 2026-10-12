# R.HA shadcn/ui 적용 기준

## 기준과 범위

- 화면 기준: [`wireframes/wireframes.html`](wireframes/wireframes.html) 전체(인증, 역할별 홈, 메뉴 3~11).
- 업무·권한 기준: [`wireframes/wireframe-api-mapping.md`](wireframes/wireframe-api-mapping.md), 같은 폴더의 역할별 API 문서, [`api-spec/openapi.yaml`](../api-spec/openapi.yaml).
- 현재 `frontend/`에는 앱 코드가 없다. 이 문서는 프론트 구현 시 팀이 따라야 할 선택 기준이다. 와이어프레임의 회색·테두리·간격은 구조 참고용이고 확정 디자인 토큰으로 보지 않는다.

목표는 **같은 의미의 UI는 모든 메뉴와 역할에서 같은 컴포넌트·variant·동작을 쓰는 것**이다. 새로운 화면은 아래 적용표에서 화면 유형을 먼저 고르고 그 유형의 조합을 적용한다.

## 1. 전역 결정: 한 역할에 한 패턴

| UI 역할 | 전역에서 사용할 것 | 혼용 금지 |
|---|---|---|
| 앱 내비게이션 | `SidebarProvider` + `Sidebar` + `SidebarMenuButton` | 화면별 수제 사이드바, 별도 아이콘 축소 메뉴 |
| 현재 위치 | `Breadcrumb` + 페이지 제목 | 페이지마다 다른 경로 표기 |
| 요약 지표·독립 섹션 | `Card` | 목록 행까지 카드로 치환 |
| 검색 가능한 업무 목록 | `Table` + 공통 필터 바 + 공통 페이지 바 | 메뉴마다 제각각인 grid 행·카드·표 |
| 수업 탐색·내 예약 카드 | `Card` 목록 | 관리용 수업·예약 목록까지 카드로 변경 |
| 같은 리소스의 하위 정보 | `Tabs` | 탭과 버튼 그룹을 같은 용도로 혼용 |
| 상태 | 공통 `StatusBadge`(shadcn `Badge` 래핑) | 화면별 임의 색상·표현, 원시 enum 노출 |
| 일반 생성·수정·짧은 상세 | 가운데 `Dialog` | 우측 `Sheet`, 바닥 `Drawer`, 임의 overlay |
| 위험하거나 되돌리기 어려운 확정 | 가운데 `AlertDialog` | 일반 `Dialog`만으로 실행 |
| 길고 독립적인 작업 | 전체 페이지 | 긴 폼을 모달에 넣기 |
| 필드 묶음과 오류 | `Field` 계열 + `Input`/`Select` 등 | 수제 label/error 구성, `Field`와 `FormField` 혼용 |
| 짧은 결과 알림 | `Sonner` | 구식 `Toast`, 메뉴별 임의 토스트 |
| 빈 결과·로딩·치명적 오류 | `Empty` · `Skeleton` · `Alert` | 빈 행만 남기거나 모든 경우에 토스트만 표시 |

`StatusBadge`, 필터 바, 페이지 바는 **프로젝트 공통 조합 이름**이며 shadcn/ui의 독립 컴포넌트명이 아니다. `Data Table`도 완제품 하나가 아니라 `Table`을 기반으로 조합하는 예시다. 실제 필요한 표 기능만 추가한다. [공식 Data Table 안내](https://ui.shadcn.com/docs/components/radix/data-table), [Field 안내](https://ui.shadcn.com/docs/components/radix/field).

## 2. 화면 골격과 시각 토큰

### 2.1 공통 셸

1. 로그인·인증은 사이드바 없는 가운데 정렬 `Card` 한 개를 사용한다.
2. 로그인 후 모든 역할은 같은 `SidebarProvider`/`Sidebar` 셸을 쓴다. 역할에 따라 **메뉴 데이터만** 달라진다.
3. 사이드바 메뉴는 텍스트 라벨을 기본으로 한다. 와이어프레임의 네모 아이콘 자리와 이전 샘플의 유니코드 아이콘은 구현하지 않는다. 버튼과 메뉴에 장식 아이콘을 넣지 않는다. 기본 `SidebarTrigger`가 아이콘 버튼이면 텍스트가 보이는 공통 트리거로 감싼다.
4. 모바일은 shadcn `Sidebar`가 제공하는 모바일 열림 상태를 사용한다. 업무 모달은 모바일에서도 화면 중앙의 `Dialog`다. 모바일을 이유로 업무 모달을 `Sheet`/`Drawer`로 바꾸지 않는다. [Sidebar 공식 안내](https://ui.shadcn.com/docs/components/radix/sidebar).
5. 헤더의 사용자 메뉴만 `DropdownMenu`, 알림 숫자는 알림 화면으로 가는 텍스트 버튼 옆 `Badge`를 쓴다. 메뉴 항목은 역할/권한에 따라 구성한다.
6. 본문은 `Breadcrumb` → 제목·한 줄 설명·주요 액션 → 화면 콘텐츠 순서다. 같은 수준의 페이지에서 제목·여백·최대 폭을 바꾸지 않는다.

### 2.2 토큰과 크기

- Pretendard와 `word-break: keep-all`을 전역 적용한다.
- 색상, radius, 그림자, 간격은 `globals.css`의 의미 토큰으로 정의한다. 화면별 hex 값이나 radius 숫자를 추가하지 않는다.
- 기본 버튼·입력 높이는 동일한 크기 체계를 쓴다. 작은 크기는 조밀한 표 내부에만 허용한다.
- `Card`는 요약 지표·독립 섹션·회원용 수업/예약 항목에 사용한다. `CardHeader`, `CardContent`, 필요 시 `CardFooter`의 패딩을 공통 variant로 맞춘다.
- 중앙 `DialogContent`는 좁은 확인/읽기 1열, 일반 편집 1~2열의 두 크기만 둔다. 세로가 길면 **모달 내부 콘텐츠만 스크롤**하고 제목·하단 액션은 유지한다. [Dialog 공식 안내](https://ui.shadcn.com/docs/components/radix/dialog).
- 토큰 이름 예시: `--background`, `--foreground`, `--card`, `--muted`, `--border`, `--primary`, `--destructive`, `--success`, `--warning`, `--radius`. 상태별 전경·배경 토큰은 접근 가능한 명도 대비를 확인해 한곳에서 정의한다.

## 3. 컴포넌트 사용 계약

### 3.1 Button: 의미별 variant 고정

| 의미 | variant | 와이어프레임 예시 |
|---|---|---|
| 페이지에서 가장 중요한 행동·폼 제출 | `default` | 이용권 발급, 수업 개설, 저장, 로그인 |
| 덜 중요한 일반 행동 | `outline` | 수정, 조회, 필터 초기화, 이전·다음 |
| 조용한 화면 이동 | `ghost` 또는 텍스트 링크 | 전체 보기, 목록으로, 관련 화면 이동 |
| 삭제·취소·폐기·거절·환불 표시 | `destructive` | 수업 취소, 예약 취소, 정산 폐기, 결제 환불 표시 |

- 화면 제목 줄에 `default` 주요 버튼은 최대 하나. 같은 영역의 다른 행동은 `outline`로 둔다.
- 버튼은 **텍스트만** 사용한다. `＋`, `→`, `✎`, `×`, `⌄`, 알림 이모지 등 장식 문자도 버튼 문구에서 제거한다. 닫기는 `닫기` 또는 `취소`라는 텍스트를 쓴다.
- `저장`, `확인`만으로 결과를 알기 어려우면 `부재 신청`, `이용권 발급`, `환불 여부 기록`처럼 실제 동작을 쓴다.
- `Button`의 `secondary`는 이번 화면군에서 사용하지 않는다. 필터·보조 동작은 `outline`로 통일한다.
- 내비게이션은 가능하면 링크 의미를 유지하고, 버튼으로 스타일링할 경우에도 역할과 키보드 접근을 보장한다.

### 3.2 Badge: 도메인별 상태 표준

`StatusBadge`에 도메인(`member`, `lesson`, `reservation`, `pass`, `payment`, `absence`, `settlement`, `notification`)과 API 상태를 넘기면 **한국어 라벨 + 동일한 의미 색상**을 반환하게 한다. 화면에서 원시 enum(`ACTIVE`, `PARTIAL` 등)을 그대로 쓰지 않는다.

| 의미 | 공통 톤 | 예시 |
|---|---|---|
| 정상·완료·사용 중 | success | 활성 회원, 공개 수업, 출석, 결제 완료, 승인, 지급 완료 |
| 신청·검토·예정·부분 완료 | warning | 부재 승인 대기, 공개 예약, 결제 일부 완료, 정산 검토 요청 |
| 취소·반려·노쇼·만료·미수·실패 | destructive | 예약 취소, 수업 취소, 부재 반려, 만료 이용권, 미수 결제 |
| 비활성·읽음·초안·정보성 | neutral | 비활성 회원, 읽은 알림, 개설만 된 수업 |

예외가 생기면 각 화면 CSS로 덮지 말고 이 중앙 매핑에 상태를 추가한다. 한 카드 안의 이용권 상태·결제 상태·미수금은 별개로 표시한다. `Badge`에는 상태 텍스트를 반드시 넣고 색상만으로 구분하지 않는다.

### 3.3 목록, 필터, 페이지

- 관리자 회원·강사·이용권·상품·유형·룸·부재·예약·정산·활동 이력과 역할별 알림 목록은 `Table`로 통일한다. 와이어프레임의 `.data-row`, `.pass-table`, `.course-table`, `.notification-table`은 모두 같은 표 셸로 옮긴다.
- 수업 카드 목록과 회원의 내 예약·내 이용권은 **탐색형 카드 목록**으로 유지한다. 예약자/출결처럼 열별 비교가 필요한 곳은 역할과 무관하게 `Table`이다.
- 목록 구조: 제목/설명 → 필터 바 → 결과 건수 → `Table` 또는 카드 목록 → 페이지 바. 필터 바의 필드 순서는 검색어, 날짜 범위, 분류/상태, 조회, 초기화 순서로 고정한다.
- 검색은 `Input`, 고정된 상태·유형·수단은 `Select`, 사람/상품처럼 길고 검색이 필요한 선택지는 `Combobox`를 쓴다. 같은 필드를 메뉴마다 `Select`와 `Combobox`로 바꾸지 않는다. [Combobox 공식 안내](https://ui.shadcn.com/docs/components/radix/combobox).
- 날짜·시간 범위는 첫 구현에서 `Input type="date"`/`type="datetime-local"` + `FieldLabel`을 쓴다. 모든 화면에 달력 `Popover`를 섞지 않는다. 날짜 입력 UI를 교체할 경우 전체 화면에 함께 적용한다.
- 페이지 바는 `Pagination` 셸과 텍스트 `이전`/`다음` 버튼으로 통일한다. 기본 `PaginationPrevious`/`PaginationNext`가 아이콘을 포함하면 그대로 사용하지 않고 텍스트 전용 버튼으로 구성한다. 항상 `현재 페이지 / 전체 페이지`, 전체 건수, 이전·다음 비활성 상태를 같은 위치에 표시한다. API의 1부터 시작하는 `pageInfo.page`를 그대로 쓴다. [Pagination 공식 안내](https://ui.shadcn.com/docs/components/radix/pagination).
- 표의 상태 열은 `StatusBadge`, 금액 열은 우측 정렬, 날짜는 같은 서울 시간 표기, 식별자/상세 이동은 명시적인 링크로 둔다. 행 전체 클릭에만 의존하지 않는다.
- 화면 폭이 좁으면 필터를 줄바꿈하고 표는 가로 스크롤한다. 핵심 정보가 부족한 경우 공통 반응형 표 variant를 정의해 카드로 재배치한다. 메뉴별 즉흥적 재배치는 금지한다.

### 3.4 Tabs, Dialog, AlertDialog

- `Tabs`는 같은 상세 리소스의 하위 섹션에만 쓴다: 회원 상세(기본 정보/이용권/예약·출결/결제), 강사 상세, 이용권 상세의 홀딩/결제, 정산 상세 등. 메뉴 이동은 사이드바, 조회 조건은 필터다.
- 알림 상세, 예약 상세, 활동 이력 상세처럼 짧고 읽기 전용인 내용은 **가운데 `Dialog`**. 복잡하고 공유 URL이 필요한 회원·수업·강사·이용권·정산의 기본 상세는 **전체 페이지**다. 같은 리소스의 기본 상세를 화면에 따라 모달/페이지로 번갈아 구현하지 않는다.
- 회원 수정·강사 부재 신청·홀딩·결제 기록·수당 단가 수정처럼 한 작업으로 끝나는 폼은 **가운데 `Dialog`**. 수업 개설/수정, 반복 일정 편집, 정산 계산처럼 필드와 확인 단계가 긴 작업은 **전체 페이지**다.
- 수업/예약 취소, 회원 비활성화, 부재 승인·반려, 정산 확정·폐기·지급 기록, 환불 여부 기록, 삭제에는 **가운데 `AlertDialog`**를 쓴다. 확인 문구에 영향과 범위를 적는다. [AlertDialog 공식 안내](https://ui.shadcn.com/docs/components/radix/alert-dialog).
- `Sheet`와 `Drawer`는 이 프로젝트의 업무 상세·폼에 사용하지 않는다. 이전 회원 상세 HTML의 `sheet`라는 id/class는 중앙 모달을 그린 임시 이름이며 React로 옮길 때 `Dialog`로 교체한다.
- 모달은 제목·설명·취소·구체적 확정 버튼을 포함하고, 닫힐 때 포커스를 열었던 버튼으로 돌린다. `DialogContent`의 기본 아이콘 닫기 버튼은 숨기고 텍스트 `닫기` 버튼을 제공한다. 별도 모달을 중첩해 띄우지 않는다.

### 3.5 Form과 피드백

- 입력은 `Field` → `FieldLabel` → 입력 컨트롤 → 선택적 `FieldDescription` → 오류 `FieldError` 구조를 기본으로 한다. 현재 shadcn 공식 예시는 React Hook Form과 `Field`의 조합을 안내한다. 프로젝트는 이 한 방식으로 시작하며 같은 앱에서 `FormField` 래퍼와 혼용하지 않는다. [공식 React Hook Form 안내](https://ui.shadcn.com/docs/forms/react-hook-form).
- 자유 텍스트·숫자 `Input`, 여러 줄 사유 `Textarea`, 고정 단일 선택 `Select`, 검색 선택 `Combobox`, 여러 항목 동시 선택 `Checkbox`, 배타적 선택 `RadioGroup`, 즉시 반영되는 이진 설정만 `Switch`를 사용한다.
- 인증 코드 길이와 형태가 스펙으로 확정되기 전에는 `Input`으로 통일한다. 코드 칸 분리가 실제로 필요해지면 인증 화면에서만 `InputOTP`로 변경한다.
- 필드 오류는 `FieldError`, 화면 전체 조회 실패나 권한 없음은 `Alert`, 성공/실패 결과의 짧은 알림은 `Sonner`, 빈 목록은 `Empty`, 로딩은 실제 카드/표와 같은 형태의 `Skeleton`을 쓴다. deprecated `Toast`는 설치하지 않는다. [Sonner 공식 안내](https://ui.shadcn.com/docs/components/radix/sonner), [Empty 공식 안내](https://ui.shadcn.com/docs/components/radix/empty).
- 저장 중 제출 버튼을 비활성화하고 중복 요청을 막는다. 서버의 RFC 7807 `code`는 공통 오류 매핑을 통해 한국어 문구로 표시한다.

## 4. 전체 와이어프레임 적용표

아래는 `wireframes.html`의 메뉴 1~11과 역할별 화면을 같은 패턴으로 묶은 구현표다. 한 행의 하위 화면이 늘어나도 같은 유형을 유지한다.

| 와이어프레임 | 기본 화면 유형 | 핵심 조합 | 작업 UI |
|---|---|---|---|
| 1 인증: 로그인·이메일 인증·가입·재설정 | 인증 `Card`/단계별 페이지 | `Field`, `Input`, `Button`, `Alert` | 전환은 페이지, 제출 결과는 `Sonner` |
| 2 홈: 회원·강사·관리자 | 공통 셸 + 요약 `Card` + 짧은 목록 | `Sidebar`, `Card`, `StatusBadge`, `Skeleton`, `Empty` | 빠른 메뉴는 텍스트 버튼/링크 |
| 3 회원 수업 목록·상세·내 예약/대기 | 탐색형 `Card` 목록 + 상세 페이지 | 날짜/유형 필터, `StatusBadge`, 페이지 바 | 예약·취소는 `AlertDialog` 확인, 짧은 내역은 `Dialog` |
| 3 강사·관리자 수업 운영/승인 신청 대기 | 운영 `Table` + 수업 상세 페이지 | 필터 바, `Table`, `Tabs`, 페이지 바 | 출결/확정은 `AlertDialog`, 개설·수정은 전체 페이지 |
| 4 회원 관리 | `Table` 목록 + 회원 상세 페이지 | 요약 `Card`, `Tabs`, 이용권·예약·결제 `Table` | 수정 `Dialog`, 상태 변경 `AlertDialog`, 항목 상세 `Dialog` |
| 5 강사 관리·부재·퇴사 | `Table` 목록 + 강사/부재 상세 페이지 | 필터 바, `Tabs`, `StatusBadge` | 부재 신청·정보 수정 `Dialog`, 승인·반려·퇴사 확정 `AlertDialog` |
| 6 수업 회차·예약/출결·반복 일정 | 운영 `Table` + 수업 상세/편집 페이지 | `Tabs`, 필터 바, `Table`, `StatusBadge` | 취소·승급·출결 확정 `AlertDialog`, 반복 편집 전체 페이지 |
| 7 관리자 회원 이용권·홀딩·결제 | `Table` 목록 + 이용권 상세 페이지 | 잔여/총 횟수 텍스트 + `Progress`, `Tabs`, `StatusBadge` | 발급·조정·홀딩·결제 입력 `Dialog`, 환불 여부 `AlertDialog` |
| 7 회원 내 이용권 | 읽기용 `Card` 목록 + 상세 페이지 | `Card`, `Progress`, `StatusBadge` | 관리자용 변경 버튼 없음 |
| 8 운영 설정: 상품·유형·룸·영업시간·정책 | 설정별 `Table`/`Field` 화면 | 목록은 `Table`, 요일·정책은 `Field` 그룹 | 짧은 편집 `Dialog`, 삭제·일괄 영향 확정 `AlertDialog` |
| 9 강사 수당·정산 | `Table` 목록 + 정산 상세/계산 페이지 | 필터 바, `Table`, 금액 정렬, 상태·이력 | 단가 편집 `Dialog`, 계산 전체 페이지, 확정·폐기·지급 `AlertDialog` |
| 10 알림·활동 이력 | `Table` 목록 | 읽음 필터, `StatusBadge`, 페이지 바 | 알림·이력 상세 `Dialog`; 관련 리소스는 링크 |
| 11 역할별 메뉴 안내 | 공통 셸 + 내 메뉴 `Card` 그리드 | 역할 데이터 기반 `Card`, 텍스트 링크 | 카드의 화면 열기는 해당 기존 화면으로 이동 |

강사·회원에게 관리자 기능을 보여주기 위해 별도 컴포넌트 유형을 만들지 않는다. 같은 데이터의 읽기 전용 화면이면 액션과 필드만 권한에 맞게 줄인다. 메뉴 3의 회의안 신청/승인 예시, 메뉴 6 반복·대기, 메뉴 8 휴무 보상, 메뉴 9 강사 본인 정산처럼 **현재 API가 없는 흐름은 디자인을 같은 패턴으로 잡되, 실제 저장 기능은 OpenAPI 계약이 생긴 뒤 연결**한다. 지원 여부와 권한은 각 역할별 API 문서를 확인한다.

## 5. 회원 상세에 적용한 기준 예시

```text
공통 Sidebar / Header
  Breadcrumb
  제목: 회원 상세 + 회원 정보 수정(outline) + 이용권 부여(default)
  Card: 회원 프로필 / 보유 이용권 / 잔여 횟수 / 미수금
  Tabs: 기본 정보 | 이용권 | 예약·출결 | 결제
    이용권: Card 요약 + StatusBadge + Progress + 항목별 Table
    예약·출결/결제: 공통 Table + 페이지 바
  가운데 Dialog: 회원 수정, 이용권 발급, 홀딩, 결제 기록
  가운데 AlertDialog: 회원 상태 변경 등 영향 큰 확정
```

이 구조의 `Dialog`는 같은 중앙 정렬·크기·버튼 순서를 사용한다. 회원 상세에서 이용권 상세로 이동할 때는 이용권 상세 **페이지**를 열고, 그 페이지에서도 동일한 `Breadcrumb`/`Tabs`/`StatusBadge`를 쓴다.

## 6. 구현·검토 체크리스트

- [ ] 새 화면이 위 적용표의 어느 유형인지 정했다.
- [ ] 동일한 업무가 다른 메뉴에서 다른 기본 컴포넌트로 보이지 않는다.
- [ ] 모든 버튼에 장식 아이콘/유니코드 기호가 없고 variant와 문구가 의미에 맞는다.
- [ ] 모든 업무 모달은 가운데 `Dialog` 또는 `AlertDialog`이며 우측 패널이 없다.
- [ ] 상태는 중앙 `StatusBadge` 매핑을 쓰고 API enum을 화면에 노출하지 않는다.
- [ ] 목록 필터 순서·표 셸·페이지 바·빈 상태·로딩 상태가 공통 규칙과 같다.
- [ ] 역할별 메뉴는 같은 `Sidebar` 데이터를 필터링하며 권한은 서버 결과로도 확인한다.
- [ ] 날짜/시간은 서울 현지 표기로 보여주고 API에는 OpenAPI의 UTC 형식으로 전달한다.
- [ ] API 변경이 필요한 화면은 `api-spec/openapi.yaml`부터 수정한다. 생성된 API 코드는 직접 편집하지 않고 호출은 생성된 훅으로 한다.
- [ ] 와이어프레임의 샘플 데이터·미지원 API 제안을 구현 완료된 기능으로 오인하지 않는다.

## 7. 초기 도입 컴포넌트

필요한 화면을 만들 때 아래 순서로 추가한다. shadcn/ui의 기반 선택(Radix/Base UI 등)은 프로젝트 생성 시 **한 번 결정해 전체에서 유지**한다. 예시 코드가 서로 다른 기반을 섞지 않도록 설치 명령과 import 경로를 프로젝트 README에 고정한다.

1. 공통 셸/화면: `sidebar`, `breadcrumb`, `card`, `button`, `badge`, `separator`, `dropdown-menu`.
2. 목록/상태: `table`, `pagination`, `tabs`, `skeleton`, `empty`, `alert`, `sonner`, `progress`.
3. 폼/작업: `field`, `input`, `textarea`, `select`, `checkbox`, `radio-group`, `switch`, `dialog`, `alert-dialog`.
4. 사람·상품 검색이 실제 필요한 시점: `combobox`.

`sheet`, `drawer`, `toast`, `input-otp`, 날짜 선택용 `calendar`/`popover`, `command`는 이 와이어프레임 기준의 기본 도입 목록에서 제외한다. 화면 요구가 바뀌어 도입한다면 이 문서의 전역 결정을 먼저 갱신한다.
