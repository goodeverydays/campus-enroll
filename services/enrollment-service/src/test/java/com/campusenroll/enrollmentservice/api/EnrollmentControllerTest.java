package com.campusenroll.enrollmentservice.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.campusenroll.enrollmentservice.service.EnrollmentApplicationService;
import com.campusenroll.enrollmentservice.support.ApiExceptionHandler;
import com.campusenroll.enrollmentservice.support.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class EnrollmentControllerTest {

    @Mock
    private EnrollmentApplicationService enrollmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EnrollmentController(enrollmentService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void TestEnrollTrustedStudentReturnsSynchronousResult() throws Exception {
        when(enrollmentService.enroll(42L, "enroll-42", 100L))
                .thenReturn(requestResponse());

        mockMvc.perform(post("/api/v1/enrollments")
                        .header("X-Student-Id", "42")
                        .header("Idempotency-Key", "enroll-42")
                        .header("X-Request-Id", "phase3-controller-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":100}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "phase3-controller-test"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.courseId").value(100));
        verify(enrollmentService).enroll(42L, "enroll-42", 100L);
    }

    @Test
    void TestEnrollMissingIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/enrollments")
                        .header("X-Student-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void TestFindEnrollmentsReturnsOnlyServiceResult() throws Exception {
        when(enrollmentService.findEnrollments(42L)).thenReturn(List.of(new EnrollmentResponse(
                1L, 100L, 200L, 300L, "ENROLLED",
                LocalDateTime.of(2026, 9, 1, 10, 0), null)));

        mockMvc.perform(get("/api/v1/enrollments").header("X-Student-Id", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].courseId").value(100))
                .andExpect(jsonPath("$.data[0].status").value("ENROLLED"));
    }

    private static EnrollmentRequestResponse requestResponse() {
        return new EnrollmentRequestResponse(
                "29a348db-752b-44ba-8e64-5caeb5280619",
                100L,
                200L,
                300L,
                "ENROLL",
                "SUCCESS",
                null,
                null,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                LocalDateTime.of(2026, 9, 1, 10, 0, 1));
    }
}
