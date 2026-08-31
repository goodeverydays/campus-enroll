CREATE DATABASE IF NOT EXISTS campus_auth
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS campus_student
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS campus_course
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS campus_enrollment
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON campus_auth.* TO 'campus_app'@'%';
GRANT ALL PRIVILEGES ON campus_student.* TO 'campus_app'@'%';
GRANT ALL PRIVILEGES ON campus_course.* TO 'campus_app'@'%';
GRANT ALL PRIVILEGES ON campus_enrollment.* TO 'campus_app'@'%';
FLUSH PRIVILEGES;

USE campus_auth;

CREATE TABLE legacy_identity (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  legacy_system VARCHAR(64) NOT NULL,
  legacy_user_id VARCHAR(128) NOT NULL,
  student_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_legacy_identity_system_user (legacy_system, legacy_user_id),
  UNIQUE KEY uk_legacy_identity_student (student_id)
) ENGINE=InnoDB COMMENT='Mapping between the legacy identity and CampusEnroll student';

CREATE TABLE sso_ticket (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ticket_hash CHAR(64) NOT NULL,
  legacy_identity_id BIGINT UNSIGNED NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sso_ticket_hash (ticket_hash),
  KEY idx_sso_ticket_expires_at (expires_at),
  CONSTRAINT fk_sso_ticket_legacy_identity
    FOREIGN KEY (legacy_identity_id) REFERENCES legacy_identity (id)
) ENGINE=InnoDB COMMENT='Single-use SSO tickets stored as hashes';

USE campus_student;

CREATE TABLE department (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_department_code (code)
) ENGINE=InnoDB COMMENT='University department';

CREATE TABLE major (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  department_id BIGINT UNSIGNED NOT NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_major_code (code),
  KEY idx_major_department (department_id),
  CONSTRAINT fk_major_department
    FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE=InnoDB COMMENT='Academic major';

CREATE TABLE student (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  legacy_student_id VARCHAR(128) NULL,
  student_no VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  department_id BIGINT UNSIGNED NOT NULL,
  major_id BIGINT UNSIGNED NOT NULL,
  grade_year SMALLINT UNSIGNED NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_no (student_no),
  UNIQUE KEY uk_student_legacy_id (legacy_student_id),
  KEY idx_student_department_major (department_id, major_id),
  CONSTRAINT fk_student_department
    FOREIGN KEY (department_id) REFERENCES department (id),
  CONSTRAINT fk_student_major
    FOREIGN KEY (major_id) REFERENCES major (id),
  CONSTRAINT chk_student_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'GRADUATED', 'WITHDRAWN'))
) ENGINE=InnoDB COMMENT='Student profile synchronized from the legacy system';

USE campus_course;

CREATE TABLE semester (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  starts_on DATE NOT NULL,
  ends_on DATE NOT NULL,
  enrollment_starts_at DATETIME(3) NOT NULL,
  enrollment_ends_at DATETIME(3) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_semester_code (code),
  CONSTRAINT chk_semester_dates CHECK (starts_on <= ends_on),
  CONSTRAINT chk_semester_enrollment_window
    CHECK (enrollment_starts_at < enrollment_ends_at),
  CONSTRAINT chk_semester_status
    CHECK (status IN ('PLANNED', 'ENROLLMENT_OPEN', 'IN_PROGRESS', 'CLOSED'))
) ENGINE=InnoDB COMMENT='Academic semester and enrollment window';

CREATE TABLE teacher (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  teacher_no VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  department_id BIGINT UNSIGNED NULL COMMENT 'External student-domain department ID',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_teacher_no (teacher_no)
) ENGINE=InnoDB COMMENT='Teacher directory';

CREATE TABLE course (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(160) NOT NULL,
  credits DECIMAL(4,1) NOT NULL,
  total_hours SMALLINT UNSIGNED NOT NULL,
  department_id BIGINT UNSIGNED NULL COMMENT 'External student-domain department ID',
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_code (code),
  CONSTRAINT chk_course_credits CHECK (credits > 0),
  CONSTRAINT chk_course_hours CHECK (total_hours > 0),
  CONSTRAINT chk_course_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB COMMENT='Course catalog entry';

CREATE TABLE course_offering (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  course_id BIGINT UNSIGNED NOT NULL,
  semester_id BIGINT UNSIGNED NOT NULL,
  teacher_id BIGINT UNSIGNED NOT NULL,
  section_no VARCHAR(32) NOT NULL,
  capacity INT UNSIGNED NOT NULL,
  selected_count INT UNSIGNED NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
  version BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_offering_semester_course_section
    (semester_id, course_id, section_no),
  KEY idx_offering_course (course_id),
  KEY idx_offering_teacher (teacher_id),
  CONSTRAINT fk_offering_course FOREIGN KEY (course_id) REFERENCES course (id),
  CONSTRAINT fk_offering_semester FOREIGN KEY (semester_id) REFERENCES semester (id),
  CONSTRAINT fk_offering_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id),
  CONSTRAINT chk_offering_capacity CHECK (capacity > 0),
  CONSTRAINT chk_offering_selected_count CHECK (selected_count <= capacity),
  CONSTRAINT chk_offering_status
    CHECK (status IN ('PLANNED', 'OPEN', 'CLOSED', 'CANCELLED'))
) ENGINE=InnoDB COMMENT='Course offering for one semester and section';

CREATE TABLE course_schedule (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  offering_id BIGINT UNSIGNED NOT NULL,
  day_of_week TINYINT UNSIGNED NOT NULL,
  start_section TINYINT UNSIGNED NOT NULL,
  end_section TINYINT UNSIGNED NOT NULL,
  location VARCHAR(128) NOT NULL,
  start_week TINYINT UNSIGNED NOT NULL,
  end_week TINYINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_schedule_offering (offering_id),
  CONSTRAINT fk_schedule_offering
    FOREIGN KEY (offering_id) REFERENCES course_offering (id),
  CONSTRAINT chk_schedule_day CHECK (day_of_week BETWEEN 1 AND 7),
  CONSTRAINT chk_schedule_sections
    CHECK (start_section >= 1 AND start_section <= end_section),
  CONSTRAINT chk_schedule_weeks
    CHECK (start_week >= 1 AND start_week <= end_week)
) ENGINE=InnoDB COMMENT='Weekly meeting slot used for conflict checks';

USE campus_enrollment;

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
