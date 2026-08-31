package com.campusenroll.courseservice.api;

import java.util.List;

import com.campusenroll.courseservice.service.AcademicCatalogQueryService;
import com.campusenroll.courseservice.support.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class AcademicCatalogController {

    private final AcademicCatalogQueryService academicCatalogQueryService;

    public AcademicCatalogController(AcademicCatalogQueryService academicCatalogQueryService) {
        this.academicCatalogQueryService = academicCatalogQueryService;
    }

    @GetMapping("/api/v1/semesters")
    public ApiResponse<List<SemesterResponse>> findSemesters(
            @RequestParam(required = false)
            @Pattern(regexp = "PLANNED|ENROLLMENT_OPEN|IN_PROGRESS|CLOSED") String status,
            HttpServletRequest request) {
        return ApiResponse.success(academicCatalogQueryService.findSemesters(status), RequestIds.from(request));
    }

    @GetMapping("/api/v1/teachers/{teacherId}")
    public ApiResponse<TeacherResponse> findTeacher(
            @PathVariable @Positive long teacherId,
            HttpServletRequest request) {
        return ApiResponse.success(academicCatalogQueryService.findTeacher(teacherId), RequestIds.from(request));
    }

    @GetMapping("/api/v1/courses/{courseId}/offerings")
    public ApiResponse<List<CourseOfferingResponse>> findOfferings(
            @PathVariable @Positive long courseId,
            @RequestParam(required = false) @Positive Long semesterId,
            HttpServletRequest request) {
        return ApiResponse.success(
                academicCatalogQueryService.findOfferings(courseId, semesterId),
                RequestIds.from(request));
    }

    @GetMapping("/api/v1/course-offerings/{offeringId}")
    public ApiResponse<CourseOfferingDetailResponse> findOffering(
            @PathVariable @Positive long offeringId,
            HttpServletRequest request) {
        return ApiResponse.success(academicCatalogQueryService.findOffering(offeringId), RequestIds.from(request));
    }
}
