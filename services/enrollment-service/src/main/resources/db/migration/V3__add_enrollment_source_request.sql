ALTER TABLE enrollment
  ADD COLUMN source_request_id CHAR(36) NULL AFTER semester_id,
  ADD KEY idx_enrollment_source_request (source_request_id);
