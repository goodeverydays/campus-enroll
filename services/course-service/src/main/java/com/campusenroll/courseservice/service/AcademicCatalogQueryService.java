package com.campusenroll.courseservice.service;

import java.util.List;

import com.campusenroll.courseservice.api.CourseOfferingDetailResponse;
import com.campusenroll.courseservice.api.CourseOfferingResponse;
import com.campusenroll.courseservice.api.SemesterResponse;
import com.campusenroll.courseservice.api.TeacherResponse;
import com.campusenroll.courseservice.domain.CourseOffering;
import com.campusenroll.courseservice.repository.AcademicCatalogRepository;
import com.campusenroll.courseservice.repository.CourseRepository;
import com.campusenroll.courseservice.support.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AcademicCatalogQueryService {

    private final AcademicCatalogRepository academicCatalogRepository;
    private final CourseRepository courseRepository;

    public AcademicCatalogQueryService(
            AcademicCatalogRepository academicCatalogRepository,
            CourseRepository courseRepository) {
        this.academicCatalogRepository = academicCatalogRepository;
        this.courseRepository = courseRepository;
    }

    public List<SemesterResponse> findSemesters(String status) {
        String normalizedStatus = status == null || status.isBlank() ? null : status;
        return academicCatalogRepository.findSemesters(normalizedStatus).stream()
                .map(SemesterResponse::from)
                .toList();
    }

    public TeacherResponse findTeacher(long teacherId) {
        return academicCatalogRepository.findTeacher(teacherId)
                .map(TeacherResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));
    }

    public List<CourseOfferingResponse> findOfferings(long courseId, Long semesterId) {
        if (courseRepository.findById(courseId).isEmpty()) {
            throw new ResourceNotFoundException("Course not found: " + courseId);
        }
        return academicCatalogRepository.findOfferings(courseId, semesterId).stream()
                .map(CourseOfferingResponse::from)
                .toList();
    }

    public CourseOfferingDetailResponse findOffering(long offeringId) {
        CourseOffering offering = academicCatalogRepository.findOffering(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + offeringId));
        return CourseOfferingDetailResponse.from(
                offering,
                academicCatalogRepository.findSchedules(offeringId));
    }
}
