# API conventions

## External boundary

- All external business APIs use the `/api/v1` prefix.
- Clients call `gateway-service`; internal services are not public entry points.
- The authenticated student identifier is read from a verified JWT. Gateway
  always removes a client-supplied `X-Student-Id` and injects its trusted value.
- Mutating requests should carry or receive a request ID for traceability and
  idempotency.

## Standard response

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "01H...",
  "timestamp": 1735689600000
}
```

`code = 0` means success. Non-zero codes are stable application error codes;
HTTP status codes still describe the transport-level result.

## Phase 2 and 2.5 implemented endpoints

```text
GET    /api/v1/courses
GET    /api/v1/courses/{courseId}
GET    /api/v1/courses/{courseId}/capacity?semesterId={semesterId}
GET    /api/v1/courses/{courseId}/offerings?semesterId={semesterId}
GET    /api/v1/course-offerings/{offeringId}
GET    /api/v1/semesters?status={status}
GET    /api/v1/teachers/{teacherId}
POST   /api/v1/auth/sso/exchange
GET    /api/v1/students/me
POST   /internal/v1/auth/sso-tickets
GET    /internal/v1/students/{studentId}
GET    /internal/v1/students/{studentId}/enrollment-eligibility
PUT    /internal/v1/students/legacy/{legacyStudentId}
```

`GET /api/v1/courses` accepts optional `keyword` and `semesterId` filters plus
zero-based `page` and `size` pagination. `size` is limited to 1-100. Course
capacity is aggregated across non-cancelled offerings for one required semester.
Offering detail includes its teacher and ordered weekly schedule. Semester status
may be `PLANNED`, `ENROLLMENT_OPEN`, `IN_PROGRESS`, or `CLOSED`.

The Auth Service internal endpoint requires `X-Legacy-System-Key` and accepts a
stable legacy-system name, legacy-user ID, and CampusEnroll student ID. It
returns a random one-time ticket with a 120-second default lifetime; only the
SHA-256 ticket hash is stored. Mapping one legacy identity to multiple students,
or one student to multiple legacy identities, returns `40901`.

Clients exchange the ticket once through `POST /api/v1/auth/sso/exchange` and
receive a 15-minute HS256 JWT by default. The token uses issuer
`https://campus-enroll.local`, audience `campus-enroll-api`, and carries
`student_id` as its trusted identity claim. The claim must be positive and match
the JWT subject. Reusing, expiring, or inventing a ticket returns
`40101`. Gateway validates signature, expiration, issuer, and audience before
routing authenticated APIs.

`GET /api/v1/students/me` is the first protected student API. Clients cannot
select a student ID: Gateway removes any incoming `X-Student-Id` and supplies
the claim value. Student internal query and synchronization contracts remain
direct service-to-service calls and are intentionally not routed through
Gateway.

The legacy synchronization endpoint is idempotent. Department, major, and
student data are updated in one transaction. `legacyStudentId` and `studentNo`
are both identity keys; when they resolve to different existing rows the service
returns `40900` instead of overwriting either student. The endpoint is internal
and must receive system-to-system authentication before production exposure.

## Planned Phase 3 enrollment endpoints

```text
POST   /api/v1/enrollments
DELETE /api/v1/enrollments/{courseId}
GET    /api/v1/enrollments
GET    /api/v1/enrollment-requests/{requestId}
```

Enrollment behavior is not implemented in Phase 2.

## Stable application errors

| Code | HTTP status | Meaning |
| --- | --- | --- |
| `40000` | 400 | Invalid path, query, or request parameter |
| `40100` | 401 | Gateway authentication is missing or JWT validation failed |
| `40101` | 401 | SSO ticket is invalid, expired, or already consumed |
| `40102` | 401 | Internal legacy-system key is invalid |
| `40400` | 404 | Requested student or course does not exist |
| `40900` | 409 | Legacy student identity keys conflict |
| `40901` | 409 | SSO legacy identity and student mapping conflict |
| `50000` | 500 | Unexpected internal failure; details stay in server logs |

Every response returns `X-Request-Id` as both a header and body field. A safe
incoming request ID is preserved; otherwise the service creates a UUID.
