package com.campusenroll.courseservice.api;

import com.campusenroll.courseservice.service.CourseQueryService;
import com.campusenroll.courseservice.support.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseQueryService courseQueryService;

    public CourseController(CourseQueryService courseQueryService) {
        this.courseQueryService = courseQueryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<CourseSummaryResponse>> findCourses(
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Positive Long semesterId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                courseQueryService.findCourses(keyword, semesterId, page, size),
                RequestIds.from(request));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailResponse> findCourse(
            @PathVariable @Positive long courseId,
            HttpServletRequest request) {
        return ApiResponse.success(courseQueryService.findCourse(courseId), RequestIds.from(request));
    }

    @GetMapping("/{courseId}/capacity")
    public ApiResponse<CourseCapacityResponse> findCapacity(
            @PathVariable @Positive long courseId,
            @RequestParam @Positive long semesterId,
            HttpServletRequest request) {
        return ApiResponse.success(
                courseQueryService.findCapacity(courseId, semesterId),
                RequestIds.from(request));
    }
}
