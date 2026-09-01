package com.campusenroll.courseservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.campusenroll.courseservice.domain.CourseOffering;
import com.campusenroll.courseservice.repository.AcademicCatalogRepository;
import com.campusenroll.courseservice.support.CourseCapacityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseCapacityServiceTest {

    @Mock
    private AcademicCatalogRepository repository;

    @InjectMocks
    private CourseCapacityService service;

    @Test
    void TestReserveAvailableOfferingReturnsUpdatedCapacity() {
        when(repository.reserveCapacity(10L)).thenReturn(true);
        when(repository.findOffering(10L)).thenReturn(Optional.of(offering("OPEN", 3, 10)));

        var response = service.reserve(10L);

        assertThat(response.selectedCount()).isEqualTo(3);
        assertThat(response.remainingCount()).isEqualTo(7);
    }

    @Test
    void TestReserveFullOfferingReturnsStableConflict() {
        when(repository.reserveCapacity(10L)).thenReturn(false);
        when(repository.findOffering(10L)).thenReturn(Optional.of(offering("OPEN", 10, 10)));

        assertThatThrownBy(() -> service.reserve(10L))
                .isInstanceOfSatisfying(CourseCapacityException.class,
                        exception -> assertThat(exception.code()).isEqualTo(40911));
    }

    @Test
    void TestReserveClosedOfferingReturnsStableConflict() {
        when(repository.reserveCapacity(10L)).thenReturn(false);
        when(repository.findOffering(10L)).thenReturn(Optional.of(offering("CLOSED", 10, 10)));

        assertThatThrownBy(() -> service.reserve(10L))
                .isInstanceOfSatisfying(CourseCapacityException.class,
                        exception -> assertThat(exception.code()).isEqualTo(40912));
    }

    @Test
    void TestReleaseReservedCapacityReturnsUpdatedCount() {
        when(repository.releaseCapacity(10L)).thenReturn(true);
        when(repository.findOffering(10L)).thenReturn(Optional.of(offering("OPEN", 2, 10)));

        var response = service.release(10L);

        assertThat(response.selectedCount()).isEqualTo(2);
        verify(repository).releaseCapacity(10L);
    }

    private static CourseOffering offering(String status, int selectedCount, int capacity) {
        return new CourseOffering(
                10L, 20L, "CS101", "Algorithms", 30L, "2026 Fall",
                40L, "Teacher", "01", capacity, selectedCount, status);
    }
}
