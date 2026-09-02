package com.campusenroll.courseservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.campusenroll.courseservice.domain.CourseOffering;
import com.campusenroll.courseservice.domain.CapacityReservation;
import com.campusenroll.courseservice.repository.AcademicCatalogRepository;
import com.campusenroll.courseservice.repository.CapacityReservationRepository;
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

    @Mock
    private CapacityReservationRepository reservationRepository;

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

    @Test
    void TestRepeatedRequestDoesNotReserveCapacityTwice() {
        when(reservationRepository.lock("request-1"))
                .thenReturn(Optional.of(new CapacityReservation("request-1", 10L, "RESERVED")));
        when(repository.findOffering(10L)).thenReturn(Optional.of(offering("OPEN", 3, 10)));

        var response = service.reserve(10L, "request-1");

        assertThat(response.selectedCount()).isEqualTo(3);
        verify(reservationRepository).ensure("request-1", 10L);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).reserveCapacity(10L);
    }

    @Test
    void TestReleasedRequestCanReserveCapacityAgain() {
        when(reservationRepository.lock("request-1"))
                .thenReturn(Optional.of(new CapacityReservation("request-1", 10L, "RELEASED")));
        when(repository.reserveCapacity(10L)).thenReturn(true);
        when(repository.findOffering(10L)).thenReturn(Optional.of(offering("OPEN", 3, 10)));

        service.reserve(10L, "request-1");

        verify(repository).reserveCapacity(10L);
        verify(reservationRepository).markReserved("request-1");
    }

    @Test
    void TestRepeatedReleaseDoesNotReleaseCapacityTwice() {
        when(reservationRepository.lock("request-1"))
                .thenReturn(Optional.of(new CapacityReservation("request-1", 10L, "RELEASED")));
        when(repository.findOffering(10L)).thenReturn(Optional.of(offering("OPEN", 2, 10)));

        service.release(10L, "request-1");

        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).releaseCapacity(10L);
    }

    private static CourseOffering offering(String status, int selectedCount, int capacity) {
        return new CourseOffering(
                10L, 20L, "CS101", "Algorithms", 30L, "2026 Fall",
                40L, "Teacher", "01", capacity, selectedCount, status);
    }
}
