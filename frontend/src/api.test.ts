import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, setAccessToken, waitForEnrollmentRequest } from './api';

function envelope<T>(data: T, code = 0, message = 'success') {
  return { code, message, data, requestId: 'request-test', timestamp: Date.now() };
}

describe('API client', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('attaches the stored bearer token and unwraps the response envelope', async () => {
    setAccessToken('jwt-value');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(envelope({ id: 42 })), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));

    await expect(api.getProfile()).resolves.toMatchObject({ id: 42 });
    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers);
    expect(headers.get('Authorization')).toBe('Bearer jwt-value');
  });

  it('keeps the backend request id on a typed API error', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(envelope(null, 40901, '课程已选')), {
      status: 409,
      headers: { 'Content-Type': 'application/json' },
    }));

    const failure = await api.enroll(1).catch((error) => error);
    expect(failure).toBeInstanceOf(ApiError);
    expect(failure).toMatchObject({ message: '课程已选', status: 409, code: 40901, requestId: 'request-test' });
  });

  it('polls an accepted enrollment request until it reaches success', async () => {
    const pending = { requestId: 'b9a81b72-b8db-44df-a372-91f47b10731d', courseId: 1, offeringId: 2, semesterId: 3, action: 'ENROLL' as const, status: 'PENDING' as const, failureCode: null, failureMessage: null, requestedAt: '2026-09-07T00:00:00', completedAt: null };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(envelope({ ...pending, status: 'SUCCESS', completedAt: '2026-09-07T00:00:01' })), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));

    await expect(waitForEnrollmentRequest(pending, 0, 2)).resolves.toMatchObject({ status: 'SUCCESS' });
  });

  it('turns an empty gateway rate-limit response into a useful error', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, {
      status: 429,
      headers: { 'X-Request-Id': 'limited-request' },
    }));

    const failure = await api.enroll(1).catch((error) => error);
    expect(failure).toMatchObject({
      message: '请求过于频繁，请稍后再试',
      status: 429,
      code: 42900,
      requestId: 'limited-request',
    });
  });
});
