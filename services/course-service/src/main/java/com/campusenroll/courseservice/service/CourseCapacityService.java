package com.campusenroll.courseservice.service;

import com.campusenroll.courseservice.api.CapacityMutationResponse;
import com.campusenroll.courseservice.domain.CapacityReservation;
import com.campusenroll.courseservice.domain.CourseOffering;
import com.campusenroll.courseservice.repository.AcademicCatalogRepository;
import com.campusenroll.courseservice.repository.CapacityReservationRepository;
import com.campusenroll.courseservice.support.CourseCapacityException;
import com.campusenroll.courseservice.support.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseCapacityService {

    private final AcademicCatalogRepository repository;
    private final CapacityReservationRepository reservationRepository;

    public CourseCapacityService(
            AcademicCatalogRepository repository,
            CapacityReservationRepository reservationRepository) {
        this.repository = repository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public CapacityMutationResponse reserve(long offeringId) {
        if (!repository.reserveCapacity(offeringId)) {
            CourseOffering offering = requireOffering(offeringId);
            if (!"OPEN".equals(offering.status())) {
                throw new CourseCapacityException(40912, "Course offering is not open for enrollment");
            }
            if (offering.selectedCount() >= offering.capacity()) {
                throw new CourseCapacityException(40911, "Course offering capacity is full");
            }
            throw new CourseCapacityException(40912, "Course offering is not open for enrollment");
        }
        return response(requireOffering(offeringId));
    }

    @Transactional
    public CapacityMutationResponse reserve(long offeringId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return reserve(offeringId);
        }
        reservationRepository.ensure(requestId, offeringId);
        CapacityReservation reservation = reservationRepository.lock(requestId)
                .orElseThrow(() -> new IllegalStateException("Capacity reservation record disappeared"));
        validateOffering(requestId, offeringId, reservation);
        if ("RESERVED".equals(reservation.status())) {
            return response(requireOffering(offeringId));
        }
        CapacityMutationResponse response = reserve(offeringId);
        reservationRepository.markReserved(requestId);
        return response;
    }

    @Transactional
    public CapacityMutationResponse release(long offeringId) {
        if (!repository.releaseCapacity(offeringId)) {
            requireOffering(offeringId);
            throw new CourseCapacityException(40917, "Course offering has no reserved capacity to release");
        }
        return response(requireOffering(offeringId));
    }

    @Transactional
    public CapacityMutationResponse release(long offeringId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return release(offeringId);
        }
        CapacityReservation reservation = reservationRepository.lock(requestId).orElse(null);
        if (reservation == null) {
            return response(requireOffering(offeringId));
        }
        validateOffering(requestId, offeringId, reservation);
        if ("RELEASED".equals(reservation.status())) {
            return response(requireOffering(offeringId));
        }
        CapacityMutationResponse response = release(offeringId);
        reservationRepository.markReleased(requestId);
        return response;
    }

    private static void validateOffering(
            String requestId,
            long offeringId,
            CapacityReservation reservation) {
        if (reservation.offeringId() != offeringId) {
            throw new CourseCapacityException(
                    40918,
                    "Enrollment request was already used for another course offering: " + requestId);
        }
    }

    private CourseOffering requireOffering(long offeringId) {
        return repository.findOffering(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course offering not found: " + offeringId));
    }

    private static CapacityMutationResponse response(CourseOffering offering) {
        return new CapacityMutationResponse(
                offering.id(),
                offering.selectedCount(),
                offering.capacity() - offering.selectedCount());
    }
}
