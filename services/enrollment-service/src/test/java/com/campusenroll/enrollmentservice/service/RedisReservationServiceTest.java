package com.campusenroll.enrollmentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.List;

import com.campusenroll.enrollmentservice.client.EnrollmentCandidate;
import com.campusenroll.enrollmentservice.support.EnrollmentBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisReservationServiceTest {

    private StringRedisTemplate redisTemplate;
    private RedisReservationService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        service = new RedisReservationService(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void TestReserveAvailableSeatSucceeds() {
        doReturn(0L).when(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisReservationService.key(20L, 10L))),
                eq("1"), eq("7"), eq("request-1"));

        service.reserve(candidate(), 7L, "request-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void TestReserveDuplicateStudentReturnsConflict() {
        doReturn(-1L).when(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisReservationService.key(20L, 10L))),
                eq("1"), eq("7"), eq("request-1"));

        assertThatThrownBy(() -> service.reserve(candidate(), 7L, "request-1"))
                .isInstanceOfSatisfying(
                        EnrollmentBusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(40910));
    }

    @Test
    @SuppressWarnings("unchecked")
    void TestReserveFullOfferingReturnsConflict() {
        doReturn(-2L).when(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisReservationService.key(20L, 10L))),
                eq("1"), eq("7"), eq("request-1"));

        assertThatThrownBy(() -> service.reserve(candidate(), 7L, "request-1"))
                .isInstanceOfSatisfying(
                        EnrollmentBusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(40911));
    }

    @Test
    @SuppressWarnings("unchecked")
    void TestReleaseMissingReservationKeyReportsNoMutation() {
        doReturn(-1L).when(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisReservationService.key(20L, 10L))),
                eq("7"));

        assertThat(service.release(20L, 10L, 7L)).isFalse();
    }

    private static EnrollmentCandidate candidate() {
        return new EnrollmentCandidate(20L, 10L, 30L, 1, List.of());
    }
}
