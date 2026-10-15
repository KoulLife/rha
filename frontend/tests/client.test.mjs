import { test, after, afterEach, mock } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';

// Use the project's Vite transform so import.meta.env and TypeScript behave as in the app.
const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-tests', server: { middlewareMode: true }, appType: 'custom' });
const { apiRequest, ApiError } = await server.ssrLoadModule('/src/api/client.ts');
const session = await server.ssrLoadModule('/src/api/session.ts');
let cleanup = () => {};
afterEach(() => { cleanup(); session.setAccessToken(null); mock.restoreAll(); });
after(() => server.close());
const json = (status, body) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' } });

test('업무 요청에 인증 헤더와 쿠키를 전달하고 동시 401은 한 번 갱신한다', async () => {
  session.setAccessToken('old');
  let refreshes = 0;
  cleanup = session.configureSession(async () => { refreshes++; return 'new'; }, () => assert.fail('expired'));
  const fetchMock = mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/v1/members');
    assert.equal(options.credentials, 'include');
    return options.headers.get('Authorization') === 'Bearer new' ? json(200, { content: [] }) : json(401, { status: 401, detail: 'expired' });
  });
  const results = await Promise.all([apiRequest('/members', { method: 'GET' }), apiRequest('/members', { method: 'GET' })]);
  assert.deepEqual(results, [{ content: [] }, { content: [] }]);
  assert.equal(refreshes, 1);
  assert.equal(fetchMock.mock.callCount(), 4);
});

test('로그인 실패와 권한 거부는 재시도하지 않는다', async () => {
  session.setAccessToken('old');
  cleanup = session.configureSession(async () => assert.fail('unexpected refresh'), () => assert.fail('unexpected expiry'));
  const fetchMock = mock.method(globalThis, 'fetch', async url => json(url.endsWith('/login') ? 401 : 403, { status: url.endsWith('/login') ? 401 : 403, detail: 'denied' }));
  await assert.rejects(apiRequest('/auth/login', { method: 'POST' }), ApiError);
  await assert.rejects(apiRequest('/members', { method: 'GET' }), ApiError);
  assert.equal(fetchMock.mock.callCount(), 2);
});

test('갱신 후에도 401이면 반복하지 않고 세션을 종료한다', async () => {
  session.setAccessToken('old');
  let expired = 0;
  cleanup = session.configureSession(async () => 'new', () => expired++);
  const fetchMock = mock.method(globalThis, 'fetch', async () => json(401, { status: 401, detail: 'denied' }));
  await assert.rejects(apiRequest('/members', { method: 'GET' }), ApiError);
  assert.equal(fetchMock.mock.callCount(), 2);
  assert.equal(expired, 1);
  assert.equal(session.getAccessToken(), null);
});

test('이전 계정의 실패한 쓰기 요청을 새 계정으로 재전송하지 않는다', async () => {
  session.setAccessToken('old-account');
  let resolve;
  const fetchMock = mock.method(globalThis, 'fetch', () => new Promise(done => { resolve = done; }));
  const request = apiRequest('/rooms', { method: 'POST', body: '{"name":"test"}' });
  session.setAccessToken('new-account');
  resolve(json(401, { status: 401, detail: 'expired' }));
  await assert.rejects(request, ApiError);
  assert.equal(fetchMock.mock.callCount(), 1);
  assert.equal(session.getAccessToken(), 'new-account');
});

test('204 응답과 잘못된 서버 응답을 구분한다', async () => {
  const fetchMock = mock.method(globalThis, 'fetch', async () => new Response(null, { status: 204 }));
  assert.equal(await apiRequest('/auth/logout', { method: 'POST' }), undefined);
  fetchMock.mock.mockImplementation(async () => new Response('<html>proxy error</html>', { headers: { 'Content-Type': 'text/html' } }));
  await assert.rejects(apiRequest('/members', { method: 'GET' }), ApiError);
});
