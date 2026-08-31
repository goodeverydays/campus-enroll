package com.campusenroll.courseservice.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.campusenroll.courseservice.service.AcademicCatalogQueryService;
import com.campusenroll.courseservice.support.ApiExceptionHandler;
import com.campusenroll.courseservice.support.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AcademicCatalogControllerTest {

    @Mock
    private AcademicCatalogQueryService academicCatalogQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AcademicCatalogController(academicCatalogQueryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void TestFindSemestersValidRequestReturnsStandardEnvelope() throws Exception {
        var semester = new SemesterResponse(
                1L, "2026-FALL", "2026 Fall", LocalDate.parse("2026-09-01"),
                LocalDate.parse("2027-01-15"), LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-09-15T23:59:59"), "ENROLLMENT_OPEN");
        when(academicCatalogQueryService.findSemesters("ENROLLMENT_OPEN"))
                .thenReturn(List.of(semester));

        mockMvc.perform(get("/api/v1/semesters").queryParam("status", "ENROLLMENT_OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].code").value("2026-FALL"));
    }

    @Test
    void TestFindTeacherMalformedIdReturnsBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/teachers/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
