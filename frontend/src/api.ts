import type {
  ApiEnvelope,
  CourseDetail,
  CourseOffering,
  CourseOfferingDetail,
  CourseSummary,
  Enrollment,
  EnrollmentRequest,
  PageResponse,
  Semester,
  StudentProfile,
  TokenResponse,
} from './types';

const TOKEN_KEY = 'campus-enroll.access-token';

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: number,
    readonly requestId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setAccessToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body) {
    headers.set('Content-Type', 'application/json');
  }
  const token = getAccessToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(path, { ...init, headers });
  let envelope: ApiEnvelope<T> | null = null;
  try {
    envelope = (await response.json()) as ApiEnvelope<T>;
  } catch {
    if (response.status === 429) {
      throw new ApiError(
        '请求过于频繁，请稍后再试',
        429,
        42900,
        response.headers.get('X-Request-Id') || undefined,
      );
    }
    throw new ApiError('服务返回了无法解析的响应', response.status);
  }
  if (!response.ok || envelope.code !== 0) {
    if (response.status === 401) {
      setAccessToken(null);
    }
    throw new ApiError(
      envelope.message || '请求失败',
      response.status,
      envelope.code,
      envelope.requestId,
    );
  }
  return envelope.data;
}

function queryString(values: Record<string, string | number | null | undefined>): string {
  const query = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      query.set(key, String(value));
    }
  });
  const encoded = query.toString();
  return encoded ? `?${encoded}` : '';
}

export const api = {
  exchangeTicket(ticket: string): Promise<TokenResponse> {
    return request('/api/v1/auth/sso/exchange', {
      method: 'POST',
      body: JSON.stringify({ ticket }),
    });
  },

  getProfile(): Promise<StudentProfile> {
    return request('/api/v1/students/me');
  },

  getSemesters(status?: string): Promise<Semester[]> {
    return request(`/api/v1/semesters${queryString({ status })}`);
  },

  getCourses(options: { keyword?: string; semesterId?: number; page?: number; size?: number } = {}): Promise<PageResponse<CourseSummary>> {
    return request(`/api/v1/courses${queryString(options)}`);
  },

  getCourse(courseId: number): Promise<CourseDetail> {
    return request(`/api/v1/courses/${courseId}`);
  },

  getOfferings(courseId: number, semesterId?: number): Promise<CourseOffering[]> {
    return request(`/api/v1/courses/${courseId}/offerings${queryString({ semesterId })}`);
  },

  getOffering(offeringId: number): Promise<CourseOfferingDetail> {
    return request(`/api/v1/course-offerings/${offeringId}`);
  },

  getEnrollments(): Promise<Enrollment[]> {
    return request('/api/v1/enrollments');
  },

  enroll(courseId: number): Promise<EnrollmentRequest> {
    return request('/api/v1/enrollments', {
      method: 'POST',
      headers: { 'Idempotency-Key': `enroll-${crypto.randomUUID()}` },
      body: JSON.stringify({ courseId }),
    });
  },

  drop(courseId: number): Promise<EnrollmentRequest> {
    return request(`/api/v1/enrollments/${courseId}`, {
      method: 'DELETE',
      headers: { 'Idempotency-Key': `drop-${crypto.randomUUID()}` },
    });
  },

  getEnrollmentRequest(requestId: string): Promise<EnrollmentRequest> {
    return request(`/api/v1/enrollment-requests/${encodeURIComponent(requestId)}`);
  },
};

export async function waitForEnrollmentRequest(
  initial: EnrollmentRequest,
  intervalMilliseconds = 750,
  maxAttempts = 40,
): Promise<EnrollmentRequest> {
  let current = initial;
  for (let attempt = 0; attempt < maxAttempts && current.status === 'PENDING'; attempt += 1) {
    await new Promise((resolve) => window.setTimeout(resolve, intervalMilliseconds));
    current = await api.getEnrollmentRequest(current.requestId);
  }
  if (current.status === 'PENDING') {
    throw new ApiError('选课请求仍在处理中，请稍后到“我的课程”查看', 408);
  }
  return current;
}
