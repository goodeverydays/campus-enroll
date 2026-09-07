CREATE TABLE enrollment_dead_letter (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id CHAR(36) NOT NULL,
  attempt_count INT UNSIGNED NOT NULL,
  failure_type VARCHAR(128) NOT NULL,
  failed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_enrollment_dead_letter_request (request_id),
  KEY idx_enrollment_dead_letter_time (failed_at),
  CONSTRAINT fk_enrollment_dead_letter_request
    FOREIGN KEY (request_id) REFERENCES enrollment_request (request_id),
  CONSTRAINT chk_enrollment_dead_letter_attempts CHECK (attempt_count > 0)
) ENGINE=InnoDB COMMENT='Durable evidence for exhausted enrollment deliveries';
