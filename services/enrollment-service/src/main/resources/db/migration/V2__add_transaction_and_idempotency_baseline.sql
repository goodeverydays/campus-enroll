ALTER TABLE enrollment_request
  ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER request_id;

UPDATE enrollment_request
SET idempotency_key = CONCAT('legacy-', request_id)
WHERE idempotency_key IS NULL;

ALTER TABLE enrollment_request
  MODIFY COLUMN idempotency_key VARCHAR(64) NOT NULL,
  ADD UNIQUE KEY uk_enrollment_request_student_key (student_id, idempotency_key);

CREATE TABLE student_enrollment_lock (
  student_id BIGINT UNSIGNED NOT NULL COMMENT 'External student-domain ID',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (student_id)
) ENGINE=InnoDB COMMENT='Serializes enrollment mutations for one student';

CREATE TABLE enrollment_schedule (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  enrollment_id BIGINT UNSIGNED NOT NULL,
  day_of_week TINYINT UNSIGNED NOT NULL,
  start_section TINYINT UNSIGNED NOT NULL,
  end_section TINYINT UNSIGNED NOT NULL,
  start_week TINYINT UNSIGNED NOT NULL,
  end_week TINYINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  KEY idx_enrollment_schedule_enrollment (enrollment_id),
  KEY idx_enrollment_schedule_conflict
    (day_of_week, start_section, end_section, start_week, end_week),
  CONSTRAINT fk_enrollment_schedule_enrollment
    FOREIGN KEY (enrollment_id) REFERENCES enrollment (id),
  CONSTRAINT chk_enrollment_schedule_day CHECK (day_of_week BETWEEN 1 AND 7),
  CONSTRAINT chk_enrollment_schedule_sections
    CHECK (start_section >= 1 AND start_section <= end_section),
  CONSTRAINT chk_enrollment_schedule_weeks
    CHECK (start_week >= 1 AND start_week <= end_week)
) ENGINE=InnoDB COMMENT='Enrollment-owned schedule snapshot for conflict checks';
