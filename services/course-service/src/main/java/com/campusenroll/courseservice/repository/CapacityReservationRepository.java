package com.campusenroll.courseservice.repository;

import java.util.Optional;

import com.campusenroll.courseservice.domain.CapacityReservation;

public interface CapacityReservationRepository {

    void ensure(String requestId, long offeringId);

    Optional<CapacityReservation> lock(String requestId);

    void markReserved(String requestId);

    void markReleased(String requestId);
}
