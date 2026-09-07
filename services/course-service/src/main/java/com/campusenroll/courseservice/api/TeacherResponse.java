package com.campusenroll.courseservice.api;

import com.campusenroll.courseservice.domain.Teacher;

public record TeacherResponse(
        long id,
        String teacherNo,
        String name,
        Long departmentId) {

    public static TeacherResponse from(Teacher teacher) {
        return new TeacherResponse(teacher.id(), teacher.teacherNo(), teacher.name(), teacher.departmentId());
    }
}
