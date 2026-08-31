package com.campusenroll.courseservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.campusenroll.courseservice.domain.CourseOffering;
import com.campusenroll.courseservice.domain.Course;
import com.campusenroll.courseservice.domain.CourseSchedule;
import com.campusenroll.courseservice.domain.Semester;
import com.campusenroll.courseservice.repository.AcademicCatalogRepository;
import com.campusenroll.courseservice.repository.CourseRepository;
import com.campusenroll.courseservice.support.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcademicCatalogQueryServiceTest {

    @Mock
    private AcademicCatalogRepository academicCatalogRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private AcademicCatalogQueryService academicCatalogQueryService;

    @Test
    void TestFindSemestersBlankStatusReturnsAllMappedSemesters() {
        var semester = new Semester(
                1L, "2026-FALL", "2026 Fall", LocalDate.parse("2026-09-01"),
                LocalDate.parse("2027-01-15"), LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-09-15T23:59:59"), "ENROLLMENT_OPEN");
        when(academicCatalogRepository.findSemesters(null)).thenReturn(List.of(semester));

        var response = academicCatalogQueryService.findSemesters("   ");

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("2026-FALL");
            assertThat(item.status()).isEqualTo("ENROLLMENT_OPEN");
        });
    }

    @Test
    void TestFindOfferingExistingReturnsSchedulesAndRemainingCapacity() {
        CourseOffering offering = offering();
        CourseSchedule schedule = new CourseSchedule(8L, 1, 1, 2, "A101", 1, 16);
        when(academicCatalogRepository.findOffering(5L)).thenReturn(Optional.of(offering));
        when(academicCatalogRepository.findSchedules(5L)).thenReturn(List.of(schedule));

        var response = academicCatalogQueryService.findOffering(5L);

        assertThat(response.offering().remainingCount()).isEqualTo(23);
        assertThat(response.schedules()).singleElement().extracting("location").isEqualTo("A101");
    }

    @Test
    void TestFindOfferingMissingThrowsResourceNotFound() {
        when(academicCatalogRepository.findOffering(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> academicCatalogQueryService.findOffering(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Course offering not found: 99");
    }

    @Test
    void TestFindOfferingsExistingCourseReturnsMappedOfferings() {
        when(courseRepository.findById(2L)).thenReturn(Optional.of(course()));
        when(academicCatalogRepository.findOfferings(2L, 1L)).thenReturn(List.of(offering()));

        var response = academicCatalogQueryService.findOfferings(2L, 1L);

        assertThat(response).singleElement().extracting("remainingCount").isEqualTo(23);
    }

    @Test
    void TestFindOfferingsMissingCourseThrowsResourceNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> academicCatalogQueryService.findOfferings(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Course not found: 99");
    }

    private static CourseOffering offering() {
        return new CourseOffering(
                5L, 2L, "CS101", "Distributed Systems", 1L, "2026 Fall", 4L,
                "Test Teacher", "01", 30, 7, "OPEN");
    }

    private static Course course() {
        return new Course(2L, "CS101", "Distributed Systems", new BigDecimal("3.0"), 48, 1L, "ACTIVE");
    }
}
