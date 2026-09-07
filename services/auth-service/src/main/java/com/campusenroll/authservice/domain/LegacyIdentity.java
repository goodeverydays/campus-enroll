package com.campusenroll.authservice.domain;

public record LegacyIdentity(long id, String legacySystem, String legacyUserId, long studentId) {
}
