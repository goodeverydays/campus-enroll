package com.campusenroll.studentservice.api;

import com.campusenroll.studentservice.service.StudentQueryService;
import com.campusenroll.studentservice.support.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/v1/students")
public class InternalStudentController {

    private final StudentQueryService studentQueryService;

    public InternalStudentController(StudentQueryService studentQueryService) {
        this.studentQueryService = studentQueryService;
    }

    @GetMapping("/{studentId}")
    public ApiResponse<StudentProfileResponse> findStudent(
            @PathVariable @Positive long studentId,
            HttpServletRequest request) {
        return ApiResponse.success(studentQueryService.findStudent(studentId), RequestIds.from(request));
    }

    @GetMapping("/{studentId}/enrollment-eligibility")
    public ApiResponse<EnrollmentEligibilityResponse> checkEnrollmentEligibility(
            @PathVariable @Positive long studentId,
            HttpServletRequest request) {
        return ApiResponse.success(
                studentQueryService.checkEnrollmentEligibility(studentId),
                RequestIds.from(request));
    }
}
