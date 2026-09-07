package com.campusenroll.courseservice.repository;

import java.util.Optional;

import com.campusenroll.courseservice.domain.Course;
import com.campusenroll.courseservice.domain.CourseCapacity;

public interface CourseRepository {

    CoursePage findAll(String keyword, Long semesterId, int page, int size);

    Optional<Course> findById(long courseId);

    Optional<CourseCapacity> findCapacity(long courseId, long semesterId);
}
