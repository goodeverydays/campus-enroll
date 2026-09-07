package com.campusenroll.courseservice.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.campusenroll.courseservice.service.CourseQueryService;
import com.campusenroll.courseservice.support.ApiExceptionHandler;
import com.campusenroll.courseservice.support.RequestIdFilter;
import com.campusenroll.courseservice.support.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

    @Mock
    private CourseQueryService courseQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CourseController(courseQueryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void TestFindCoursesValidRequestReturnsStandardEnvelope() throws Exception {
        when(courseQueryService.findCourses(null, null, 0, 20))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/courses").header("X-Request-Id", "test-request-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "test-request-1"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").value("test-request-1"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void TestFindCourseMissingReturnsStandardNotFoundEnvelope() throws Exception {
        when(courseQueryService.findCourse(99L))
                .thenThrow(new ResourceNotFoundException("Course not found: 99"));

        mockMvc.perform(get("/api/v1/courses/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("Course not found: 99"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void TestFindCoursesMalformedPageSizeReturnsStandardBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/courses").queryParam("size", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("Invalid request parameters"));
    }
}
