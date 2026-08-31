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

## Planned Phase 2/3 endpoints

```text
GET    /api/v1/courses
GET    /api/v1/courses/{courseId}
GET    /api/v1/courses/{courseId}/capacity
GET    /api/v1/students/me
POST   /api/v1/enrollments
DELETE /api/v1/enrollments/{courseId}
GET    /api/v1/enrollments
GET    /api/v1/enrollment-requests/{requestId}
```

The endpoints above are contracts only in Phase 1; no enrollment behavior is
implemented yet.
