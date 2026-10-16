// 디자인 검토용 상태. 네트워크 응답이나 서버 권한을 흉내 내는 API가 아니다.
export const homeViewLabels = {
  normal: "정상",
  loading: "불러오는 중",
  empty: "데이터 없음",
  error: "조회 실패",
  expired: "로그인 만료",
  forbidden: "권한 없음",
} as const;
export type HomeView = keyof typeof homeViewLabels;
