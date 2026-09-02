package com.campusenroll.courseservice.domain;

public record CapacityReservation(
        String requestId,
        long offeringId,
        String status) {
}
