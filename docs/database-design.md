# Database design

One MySQL instance hosts four logical databases during local development. Tables
are owned by one service and must not be read directly by another service.

| Database | Owner | Main tables |
| --- | --- | --- |
| `campus_auth` | auth-service | `legacy_identity`, `sso_ticket` |
| `campus_student` | student-service | `department`, `major`, `student` |
| `campus_course` | course-service | `semester`, `teacher`, `course`, `course_offering`, `course_schedule` |
| `campus_enrollment` | enrollment-service / enrollment-worker | `enrollment_request`, `enrollment`, `student_enrollment_lock`, `enrollment_schedule` |

The Compose bootstrap script creates databases and grants only. Schema ownership
is enforced by Flyway migrations stored with the owning service:

| Service | Migration |
| --- | --- |
| auth-service | `services/auth-service/src/main/resources/db/migration` |
| student-service | `services/student-service/src/main/resources/db/migration` |
| course-service | `services/course-service/src/main/resources/db/migration` |
| enrollment-service | `services/enrollment-service/src/main/resources/db/migration` |

Applied migrations are immutable. Every schema change must be introduced by a
new versioned migration. The enrollment worker uses the enrollment database but
does not run a second migration set.

Cross-domain references such as `student_id`, `course_id`, and `semester_id` are
stored as IDs without cross-database foreign keys. Their validity is checked
through service contracts. This preserves service ownership while keeping the
Phase 1 deployment small.

The `enrollment` table enforces the business uniqueness rule on
`(student_id, course_id, semester_id)`. Phase 4 adds no relational table: Redis
reservation state is transient, while message-processing metadata remains absent
until Phases 5-6.

Phase 3 adds `V2__add_transaction_and_idempotency_baseline.sql`. It gives each
request a student-scoped `idempotency_key`, serializes mutations through one
`student_enrollment_lock` row, and stores an enrollment-owned schedule snapshot
for overlap checks. Dropped rows are reactivated instead of inserting a second
row, so the original uniqueness boundary remains intact.

Course capacity remains owned by Course Service. Its internal capacity endpoint
uses one conditional MySQL update to increment only an open, in-window offering
with remaining capacity, and a guarded decrement for drops. Enrollment Service
never reads or writes `campus_course` directly. Because Phase 3 deliberately uses
synchronous REST instead of distributed transactions, an HTTP timeout after a
remote capacity mutation has an ambiguous outcome. Publisher confirms, durable
processing, reconciliation, and compensation for that boundary belong to Phase 6.

## Phase 4 Redis reservation model

Each offering uses one Redis Hash key:

```text
campus:enrollment:reservation:{courseId}:offering:{offeringId}
  remaining             -> integer
  student:{studentId}   -> enrollment request ID
```

The braces provide an explicit Redis Cluster hash tag. Reserve, release, and
rollback-restore scripts touch exactly one key, so every state transition is
atomic and cluster-slot safe. When the key is absent, reserve initializes
`remaining` from Course Service's current MySQL-backed response. Redis is an
admission gate rather than the system of record: Course Service still owns final
capacity and Enrollment Service still owns enrollment truth.

Successful enrollments retain their student marker. Drops remove it. A failed
downstream step triggers best-effort reverse-order compensation. Network timeout
ambiguity, reconciliation, and durable repair records are intentionally deferred
to Phase 6; RabbitMQ is not used in Phase 4.

For a successful MySQL enrollment created before Phase 4, the Redis Hash may
exist without that student's marker. The release script still increments an
existing Hash exactly once because the locked MySQL row proves the active
enrollment; when the whole key is absent it leaves Redis untouched so the next
reserve can initialize from current MySQL-backed capacity.
