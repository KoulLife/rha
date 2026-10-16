import type { ErrorResponse } from "./generated/api";
import { expireSession, getAccessToken, getSessionVersion, renewSession } from "./session";

export class ApiError extends Error {
  constructor(public problem: Pick<ErrorResponse, "status" | "detail"> & Partial<ErrorResponse>) {
    super(problem.detail);
  }
}

function invalidResponse(status: number): ApiError {
  const detail = "서버 응답을 처리하지 못했습니다. API 서버 연결을 확인하고 다시 시도해 주세요.";
  return new ApiError({ status, detail });
}

// Only the generated API client calls this transport. Screens use generated hooks.
export async function apiRequest<T>(url: string, options: RequestInit): Promise<T> {
  const token = getAccessToken();
  const sessionVersion = getSessionVersion();
  const send = (accessToken: string | null) => {
    const headers = new Headers(options.headers);
    if (options.body != null) headers.set("Content-Type", "application/json");
    if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
    return fetch(`${(import.meta.env.VITE_API_BASE_URL ?? "/v1").replace(/\/$/, "")}${url}`, {
      ...options,
      credentials: "include",
      headers,
    });
  };
  let response = await send(token);
  // Public auth failures must not trigger refresh (e.g. a wrong password).
  if (response.status === 401 && token && (!url.startsWith("/auth/") || url === "/auth/logout")) {
    const assertSameSession = () => {
      if (sessionVersion !== getSessionVersion())
        throw new ApiError({
          status: 401,
          detail: "로그인 상태가 변경되었습니다. 다시 시도해 주세요.",
        });
    };
    assertSameSession();
    const renewed = getAccessToken() !== token ? getAccessToken() : await renewSession();
    assertSameSession();
    if (renewed) response = await send(renewed);
    if (response.status === 401 && sessionVersion === getSessionVersion()) expireSession();
  }
  if (
    !/(?:\/json|\+json)(?:;|$)/i.test(response.headers.get("content-type") ?? "") &&
    response.status !== 204
  ) {
    throw invalidResponse(response.status);
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({
      detail: "요청을 처리하지 못했습니다. 다시 시도해 주세요.",
      status: response.status,
    }));
    throw new ApiError(problem);
  }
  if (response.status === 204) return undefined as T;
  try {
    return (await response.json()) as T;
  } catch {
    throw invalidResponse(response.status);
  }
}
