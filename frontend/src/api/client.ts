import type { ErrorResponse } from './generated/api';

export class ApiError extends Error {
  constructor(public problem: Pick<ErrorResponse, 'status' | 'detail'> & Partial<ErrorResponse>) { super(problem.detail); }
}

function invalidResponse(status: number): ApiError {
  const detail = import.meta.env.DEV
    ? 'API 연결에 실패했습니다. 화면을 새로고침해 목 데이터를 다시 연결해 주세요.'
    : '서버 응답을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
  return new ApiError({ status, detail });
}

// Only the generated API client calls this transport. Screens use generated hooks.
export async function apiRequest<T>(url: string, options: RequestInit): Promise<T> {
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? '/v1'}${url}`, {
    ...options, credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...options.headers },
  });
  if (!/(?:\/json|\+json)(?:;|$)/i.test(response.headers.get('content-type') ?? '') && response.status !== 204) {
    throw invalidResponse(response.status);
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({ detail: '요청을 처리하지 못했습니다. 다시 시도해 주세요.', status: response.status }));
    throw new ApiError(problem);
  }
  if (response.status === 204) return undefined as T;
  try {
    return await response.json() as T;
  } catch {
    throw invalidResponse(response.status);
  }
}
