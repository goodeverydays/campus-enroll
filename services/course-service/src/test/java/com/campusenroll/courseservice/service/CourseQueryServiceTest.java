package com.campusenroll.courseservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.campusenroll.courseservice.domain.Course;
import com.campusenroll.courseservice.domain.CourseCapacity;
import com.campusenroll.courseservice.repository.CoursePage;
import com.campusenroll.courseservice.repository.CourseRepository;
import com.campusenroll.courseservice.support.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseQueryServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseQueryService courseQueryService;

    @Test
    void TestFindCoursesBlankKeywordReturnsMappedPage() {
        Course course = course(7L);
        when(courseRepository.findAll(null, 3L, 1, 10))
                .thenReturn(new CoursePage(List.of(course), 11));

        var response = courseQueryService.findCourses("   ", 3L, 1, 10);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(7L);
            assertThat(item.code()).isEqualTo("CS101");
        });
        assertThat(response.totalElements()).isEqualTo(11);
        assertThat(response.totalPages()).isEqualTo(2);
        verify(courseRepository).findAll(null, 3L, 1, 10);
    }

    @Test
    void TestFindCourseMissingThrowsResourceNotFound() {
        when(courseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseQueryService.findCourse(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Course not found: 99");
    }

    @Test
    void TestFindCapacityExistingReturnsRemainingCount() {
        when(courseRepository.findCapacity(7L, 3L))
                .thenReturn(Optional.of(new CourseCapacity(7L, 3L, 200, 125)));

        var response = courseQueryService.findCapacity(7L, 3L);

        assertThat(response.remainingCount()).isEqualTo(75);
    }

    private static Course course(long id) {
        return new Course(id, "CS101", "Distributed Systems", new BigDecimal("3.0"), 48, 2L, "ACTIVE");
    }
}
