package com.campusenroll.courseservice.repository;

import java.util.List;
import java.util.Optional;

import com.campusenroll.courseservice.domain.CourseOffering;
import com.campusenroll.courseservice.domain.CourseSchedule;
import com.campusenroll.courseservice.domain.Semester;
import com.campusenroll.courseservice.domain.Teacher;

public interface AcademicCatalogRepository {

    List<Semester> findSemesters(String status);

    Optional<Teacher> findTeacher(long teacherId);

    List<CourseOffering> findOfferings(long courseId, Long semesterId);

    Optional<CourseOffering> findOffering(long offeringId);

    List<CourseSchedule> findSchedules(long offeringId);

    boolean reserveCapacity(long offeringId);

    boolean releaseCapacity(long offeringId);
}
