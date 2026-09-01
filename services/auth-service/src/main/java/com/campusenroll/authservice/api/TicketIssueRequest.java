package com.campusenroll.authservice.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TicketIssueRequest(
        @NotBlank @Size(max = 64) String legacySystem,
        @NotBlank @Size(max = 128) String legacyUserId,
        @Positive long studentId) {
}
