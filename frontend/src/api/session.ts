// Access tokens stay in memory; the server owns the HttpOnly refresh cookie.
let accessToken: string | null = null;
let generation = 0;
let refresh: (() => Promise<string>) | undefined;
let pending: Promise<string> | undefined;
let onExpired: (() => void) | undefined;

export const getAccessToken = () => accessToken;
export const getSessionVersion = () => generation;
export function setAccessToken(token: string | null) {
  generation++;
  accessToken = token;
  pending = undefined;
}
export function configureSession(refreshToken: () => Promise<string>, expired: () => void) {
  refresh = refreshToken;
  onExpired = expired;
  return () => { refresh = undefined; onExpired = undefined; };
}
export function renewSession(): Promise<string> {
  if (pending) return pending;
  const version = generation;
  const operation = Promise.resolve().then(() => {
    if (!refresh) throw new Error('로그인이 필요합니다.');
    return refresh();
  }).then(token => {
    if (version !== generation) throw new Error('로그인 상태가 변경되었습니다.');
    accessToken = token;
    return token;
  }).catch(error => {
    if (version === generation) { accessToken = null; onExpired?.(); }
    throw error;
  }).finally(() => { if (pending === operation) pending = undefined; });
  pending = operation;
  return operation;
}
export function expireSession() {
  setAccessToken(null);
  onExpired?.();
}
