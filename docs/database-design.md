# Database design

One MySQL instance hosts four logical databases during local development. Tables
are owned by one service and must not be read directly by another service.

| Database | Owner | Main tables |
| --- | --- | --- |
| `campus_auth` | auth-service | `legacy_identity`, `sso_ticket` |
| `campus_student` | student-service | `department`, `major`, `student` |
| `campus_course` | course-service | `semester`, `teacher`, `course`, `course_offering`, `course_schedule` |
| `campus_enrollment` | enrollment-service / enrollment-worker | `enrollment_request`, `enrollment` |

Cross-domain references such as `student_id`, `course_id`, and `semester_id` are
stored as IDs without cross-database foreign keys. Their validity is checked
through service contracts. This preserves service ownership while keeping the
Phase 1 deployment small.

The `enrollment` table enforces the business uniqueness rule on
`(student_id, course_id, semester_id)`. Redis reservations and message-processing
metadata are intentionally absent until Phases 4-6.
