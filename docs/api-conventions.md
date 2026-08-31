# API conventions

## External boundary

- All external business APIs use the `/api/v1` prefix.
- Clients call `gateway-service`; internal services are not public entry points.
- The authenticated student identifier will be read from the JWT, never trusted
  from an enrollment request body.
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

## Phase 2 implemented endpoints

```text
GET    /api/v1/courses
GET    /api/v1/courses/{courseId}
GET    /api/v1/courses/{courseId}/capacity?semesterId={semesterId}
GET    /internal/v1/students/{studentId}
GET    /internal/v1/students/{studentId}/enrollment-eligibility
```

`GET /api/v1/courses` accepts optional `keyword` and `semesterId` filters plus
zero-based `page` and `size` pagination. `size` is limited to 1-100. Course
capacity is aggregated across non-cancelled offerings for one required semester.

The student endpoints are internal service contracts and are intentionally not
routed through Gateway. The public `GET /api/v1/students/me` contract remains
deferred until Auth Service can supply a verified JWT identity; clients must
never choose their own student ID.

## Planned Phase 3 endpoints

```text
GET    /api/v1/students/me
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
| `40400` | 404 | Requested student or course does not exist |
| `50000` | 500 | Unexpected internal failure; details stay in server logs |

Every response returns `X-Request-Id` as both a header and body field. A safe
incoming request ID is preserved; otherwise the service creates a UUID.
