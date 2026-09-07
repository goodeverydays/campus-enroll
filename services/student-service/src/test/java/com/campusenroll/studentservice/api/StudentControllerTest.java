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
class StudentControllerTest {

    @Mock
    private StudentQueryService studentQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StudentController(studentQueryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void TestMeTrustedStudentHeaderReturnsMatchingProfile() throws Exception {
        when(studentQueryService.findStudent(42L)).thenReturn(new StudentProfileResponse(
                42L, "20260042", "Test Student", 2L, "Computer Science", 3L,
                "Software Engineering", 2026, "ACTIVE"));

        mockMvc.perform(get("/api/v1/students/me")
                        .header("X-Student-Id", "42")
                        .header("X-Request-Id", "student-me-test"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "student-me-test"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.studentNo").value("20260042"));
    }

    @Test
    void TestMeMissingTrustedStudentHeaderReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/students/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
