package com.campusenroll.courseservice.api;

public record CapacityMutationResponse(
        long offeringId,
        int selectedCount,
        int remainingCount) {
}
