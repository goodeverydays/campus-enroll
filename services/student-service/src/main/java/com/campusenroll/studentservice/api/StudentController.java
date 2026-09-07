package com.campusenroll.studentservice.api;

import com.campusenroll.studentservice.service.StudentQueryService;
import com.campusenroll.studentservice.support.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentQueryService studentQueryService;

    public StudentController(StudentQueryService studentQueryService) {
        this.studentQueryService = studentQueryService;
    }

    @GetMapping("/me")
    public ApiResponse<StudentProfileResponse> me(
            @RequestHeader("X-Student-Id") @Positive long studentId,
            HttpServletRequest request) {
        return ApiResponse.success(studentQueryService.findStudent(studentId), RequestIds.from(request));
    }
}
