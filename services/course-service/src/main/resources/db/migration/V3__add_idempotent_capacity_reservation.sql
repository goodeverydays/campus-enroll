CREATE TABLE course_capacity_reservation (
  request_id CHAR(36) NOT NULL,
  offering_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'RELEASED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (request_id),
  KEY idx_capacity_reservation_offering_status (offering_id, status),
  CONSTRAINT fk_capacity_reservation_offering
    FOREIGN KEY (offering_id) REFERENCES course_offering (id),
  CONSTRAINT chk_capacity_reservation_status
    CHECK (status IN ('RESERVED', 'RELEASED'))
) ENGINE=InnoDB COMMENT='Idempotency record for worker capacity mutations';
