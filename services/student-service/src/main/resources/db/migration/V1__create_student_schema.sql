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
