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
