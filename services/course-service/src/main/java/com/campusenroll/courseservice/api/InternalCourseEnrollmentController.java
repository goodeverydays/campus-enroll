package com.campusenroll.courseservice.api;

import com.campusenroll.courseservice.service.CourseCapacityService;
import com.campusenroll.courseservice.support.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/v1/course-offerings/{offeringId}/capacity-reservations")
public class InternalCourseEnrollmentController {

    private final CourseCapacityService courseCapacityService;

    public InternalCourseEnrollmentController(CourseCapacityService courseCapacityService) {
        this.courseCapacityService = courseCapacityService;
    }

    @PostMapping
    public ApiResponse<CapacityMutationResponse> reserve(
            @PathVariable @Positive long offeringId,
            @RequestHeader(value = "X-Enrollment-Request-Id", required = false) String enrollmentRequestId,
            HttpServletRequest request) {
        return ApiResponse.success(
                courseCapacityService.reserve(offeringId, enrollmentRequestId),
                RequestIds.from(request));
    }

    @DeleteMapping
    public ApiResponse<CapacityMutationResponse> release(
            @PathVariable @Positive long offeringId,
            @RequestHeader(value = "X-Enrollment-Request-Id", required = false) String enrollmentRequestId,
            HttpServletRequest request) {
        return ApiResponse.success(
                courseCapacityService.release(offeringId, enrollmentRequestId),
                RequestIds.from(request));
    }
}
