package com.campusenroll.enrollmentservice.api;

import java.util.List;

import com.campusenroll.enrollmentservice.service.EnrollmentApplicationService;
import com.campusenroll.enrollmentservice.support.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class EnrollmentController {

    private static final String IDEMPOTENCY_PATTERN = "[A-Za-z0-9._:-]{1,64}";
    private static final String REQUEST_ID_PATTERN = "[0-9a-fA-F-]{36}";

    private final EnrollmentApplicationService enrollmentService;

    public EnrollmentController(EnrollmentApplicationService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping("/enrollments")
    public ApiResponse<EnrollmentRequestResponse> enroll(
            @RequestHeader("X-Student-Id") @Positive long studentId,
            @RequestHeader("Idempotency-Key")
            @Size(max = 64) @Pattern(regexp = IDEMPOTENCY_PATTERN) String idempotencyKey,
            @Valid @RequestBody CreateEnrollmentRequest enrollmentRequest,
            HttpServletRequest request) {
        return ApiResponse.success(
                enrollmentService.enroll(studentId, idempotencyKey, enrollmentRequest.courseId()),
                RequestIds.from(request));
    }

    @DeleteMapping("/enrollments/{courseId}")
    public ApiResponse<EnrollmentRequestResponse> drop(
            @RequestHeader("X-Student-Id") @Positive long studentId,
            @RequestHeader("Idempotency-Key")
            @Size(max = 64) @Pattern(regexp = IDEMPOTENCY_PATTERN) String idempotencyKey,
            @PathVariable @Positive long courseId,
            HttpServletRequest request) {
        return ApiResponse.success(
                enrollmentService.drop(studentId, idempotencyKey, courseId),
                RequestIds.from(request));
    }

    @GetMapping("/enrollments")
    public ApiResponse<List<EnrollmentResponse>> findEnrollments(
            @RequestHeader("X-Student-Id") @Positive long studentId,
            HttpServletRequest request) {
        return ApiResponse.success(enrollmentService.findEnrollments(studentId), RequestIds.from(request));
    }

    @GetMapping("/enrollment-requests/{requestId}")
    public ApiResponse<EnrollmentRequestResponse> findRequest(
            @RequestHeader("X-Student-Id") @Positive long studentId,
            @PathVariable @Pattern(regexp = REQUEST_ID_PATTERN) String requestId,
            HttpServletRequest request) {
        return ApiResponse.success(
                enrollmentService.findRequest(studentId, requestId),
                RequestIds.from(request));
    }
}
