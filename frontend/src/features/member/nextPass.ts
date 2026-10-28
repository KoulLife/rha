import type { Pass } from "../../api/generated/api";

// getMyPasses는 예약 시 서버가 이용권을 고르는 순서(만료일 오름차순, 같으면 id 오름차순)로 응답한다.
// 그 순서에서 지금 예약하면 차감될 이용권을, 없으면 곧 시작할 이용권을 고른다.
export function nextPass<T extends Pick<Pass, "status" | "remainingCount">>(passes: T[]) {
  return (
    passes.find((pass) => pass.status === "ACTIVE" && pass.remainingCount > 0) ??
    passes.find((pass) => pass.status === "UPCOMING")
  );
}
