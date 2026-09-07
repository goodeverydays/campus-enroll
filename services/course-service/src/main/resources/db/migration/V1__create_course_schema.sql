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
