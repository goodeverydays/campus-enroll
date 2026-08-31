package com.campusenroll.studentservice.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusenroll.studentservice.service.StudentQueryService;
import com.campusenroll.studentservice.support.ApiExceptionHandler;
import com.campusenroll.studentservice.support.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class InternalStudentControllerTest {

    @Mock
    private StudentQueryService studentQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalStudentController(studentQueryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void TestCheckEnrollmentEligibilityActiveStudentReturnsStandardEnvelope() throws Exception {
        when(studentQueryService.checkEnrollmentEligibility(1L))
                .thenReturn(new EnrollmentEligibilityResponse(1L, true, "ELIGIBLE"));

        mockMvc.perform(get("/internal/v1/students/1/enrollment-eligibility")
                        .header("X-Request-Id", "eligibility-test"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "eligibility-test"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.studentId").value(1))
                .andExpect(jsonPath("$.data.eligible").value(true))
                .andExpect(jsonPath("$.data.reasonCode").value("ELIGIBLE"));
    }

    @Test
    void TestFindStudentMalformedIdReturnsStandardBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/internal/v1/students/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
