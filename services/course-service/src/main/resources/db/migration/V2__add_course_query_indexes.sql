CREATE INDEX idx_course_status_code
  ON course (status, code);

CREATE INDEX idx_offering_semester_status_course
  ON course_offering (semester_id, status, course_id);
