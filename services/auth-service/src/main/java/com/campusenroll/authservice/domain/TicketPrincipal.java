package com.campusenroll.authservice.domain;

public record TicketPrincipal(String legacySystem, String legacyUserId, long studentId) {
}
