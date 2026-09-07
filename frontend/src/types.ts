export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
  requestId: string;
  timestamp: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CourseSummary {
  id: number;
  code: string;
  name: string;
  credits: number;
  totalHours: number;
  status: string;
}

export interface CourseDetail extends CourseSummary {
  departmentId: number | null;
}

export interface Semester {
  id: number;
  code: string;
  name: string;
  startsOn: string;
  endsOn: string;
  enrollmentStartsAt: string;
  enrollmentEndsAt: string;
  status: string;
}

export interface CourseOffering {
  id: number;
  courseId: number;
  courseCode: string;
  courseName: string;
  semesterId: number;
  semesterName: string;
  teacherId: number;
  teacherName: string;
  sectionNo: string;
  capacity: number;
  selectedCount: number;
  remainingCount: number;
  status: string;
}

export interface CourseSchedule {
  id: number;
  dayOfWeek: number;
  startSection: number;
  endSection: number;
  location: string;
  startWeek: number;
  endWeek: number;
}

export interface CourseOfferingDetail {
  offering: CourseOffering;
  schedules: CourseSchedule[];
}

export interface StudentProfile {
  id: number;
  studentNo: string;
  name: string;
  departmentId: number;
  departmentName: string;
  majorId: number;
  majorName: string;
  gradeYear: number;
  status: string;
}

export type EnrollmentRequestStatus = 'PENDING' | 'SUCCESS' | 'FAILED';

export interface EnrollmentRequest {
  requestId: string;
  courseId: number;
  offeringId: number;
  semesterId: number;
  action: 'ENROLL' | 'DROP';
  status: EnrollmentRequestStatus;
  failureCode: string | null;
  failureMessage: string | null;
  requestedAt: string;
  completedAt: string | null;
}

export interface Enrollment {
  id: number;
  courseId: number;
  offeringId: number;
  semesterId: number;
  status: string;
  enrolledAt: string;
  droppedAt: string | null;
}

export interface TokenResponse {
  tokenType: string;
  accessToken: string;
  expiresIn: number;
}
