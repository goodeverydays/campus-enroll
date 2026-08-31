package com.campusenroll.courseservice.service;

import com.campusenroll.courseservice.api.CourseCapacityResponse;
import com.campusenroll.courseservice.api.CourseDetailResponse;
import com.campusenroll.courseservice.api.CourseSummaryResponse;
import com.campusenroll.courseservice.api.PageResponse;
import com.campusenroll.courseservice.repository.CoursePage;
import com.campusenroll.courseservice.repository.CourseRepository;
import com.campusenroll.courseservice.support.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CourseQueryService {

    private final CourseRepository courseRepository;

    public CourseQueryService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    public PageResponse<CourseSummaryResponse> findCourses(
            String keyword,
            Long semesterId,
            int page,
            int size) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        CoursePage result = courseRepository.findAll(normalizedKeyword, semesterId, page, size);
        return PageResponse.of(
                result.items().stream().map(CourseSummaryResponse::from).toList(),
                page,
                size,
                result.totalElements());
    }

    public CourseDetailResponse findCourse(long courseId) {
        return courseRepository.findById(courseId)
                .map(CourseDetailResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
    }

    public CourseCapacityResponse findCapacity(long courseId, long semesterId) {
        return courseRepository.findCapacity(courseId, semesterId)
                .map(CourseCapacityResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + courseId));
    }
}
