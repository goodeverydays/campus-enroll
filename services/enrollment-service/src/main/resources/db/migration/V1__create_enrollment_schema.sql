CREATE TABLE enrollment_request (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id CHAR(36) NOT NULL,
  student_id BIGINT UNSIGNED NOT NULL COMMENT 'External student-domain ID',
  course_id BIGINT UNSIGNED NOT NULL COMMENT 'External course-domain ID',
  offering_id BIGINT UNSIGNED NOT NULL COMMENT 'External course-domain offering ID',
  semester_id BIGINT UNSIGNED NOT NULL COMMENT 'External course-domain semester ID',
  action VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  failure_code VARCHAR(64) NULL,
  failure_message VARCHAR(255) NULL,
  requested_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at DATETIME(3) NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_enrollment_request_id (request_id),
  KEY idx_enrollment_request_student_time (student_id, requested_at),
  KEY idx_enrollment_request_status_time (status, requested_at),
  CONSTRAINT chk_enrollment_request_action CHECK (action IN ('ENROLL', 'DROP')),
  CONSTRAINT chk_enrollment_request_status
    CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
) ENGINE=InnoDB COMMENT='Immutable client request identity and processing status';

CREATE TABLE enrollment (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  student_id BIGINT UNSIGNED NOT NULL COMMENT 'External student-domain ID',
  course_id BIGINT UNSIGNED NOT NULL COMMENT 'External course-domain ID',
  offering_id BIGINT UNSIGNED NOT NULL COMMENT 'External course-domain offering ID',
  semester_id BIGINT UNSIGNED NOT NULL COMMENT 'External course-domain semester ID',
  status VARCHAR(16) NOT NULL DEFAULT 'ENROLLED',
  enrolled_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  dropped_at DATETIME(3) NULL,
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_enrollment_student_course_semester
    (student_id, course_id, semester_id),
  KEY idx_enrollment_student_status (student_id, status),
  KEY idx_enrollment_offering_status (offering_id, status),
  CONSTRAINT chk_enrollment_status CHECK (status IN ('ENROLLED', 'DROPPED'))
) ENGINE=InnoDB COMMENT='Final enrollment record and database idempotency boundary';
