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
