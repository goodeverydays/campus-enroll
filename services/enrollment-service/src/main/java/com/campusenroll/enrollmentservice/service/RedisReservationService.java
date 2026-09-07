package com.campusenroll.enrollmentservice.service;

import java.util.List;

import com.campusenroll.enrollmentservice.client.EnrollmentCandidate;
import com.campusenroll.enrollmentservice.support.EnrollmentBusinessException;
import com.campusenroll.enrollmentservice.support.EnrollmentDependencyException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisReservationService {

    private static final long DUPLICATE = -1L;
    private static final long FULL = -2L;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final DefaultRedisScript<Long> restoreScript;

    public RedisReservationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.reserveScript = script("redis/reserve-enrollment.lua");
        this.releaseScript = script("redis/release-enrollment.lua");
        this.restoreScript = script("redis/restore-enrollment.lua");
    }

    public void reserve(
            EnrollmentCandidate candidate,
            long studentId,
            String requestId) {
        Long result = execute(
                reserveScript,
                key(candidate.courseId(), candidate.offeringId()),
                Integer.toString(candidate.remainingCount()),
                Long.toString(studentId),
                requestId);
        if (result == DUPLICATE) {
            throw new EnrollmentBusinessException(40910, "Course is already reserved for this student");
        }
        if (result == FULL) {
            throw new EnrollmentBusinessException(40911, "All open course offerings are full");
        }
        if (result < 0) {
            throw new EnrollmentDependencyException("Redis returned an invalid reservation result");
        }
    }

    public boolean release(long courseId, long offeringId, long studentId) {
        Long result = execute(
                releaseScript,
                key(courseId, offeringId),
                Long.toString(studentId));
        return result >= 0;
    }

    public boolean restore(
            long courseId,
            long offeringId,
            long studentId,
            String requestId) {
        Long result = execute(
                restoreScript,
                key(courseId, offeringId),
                Long.toString(studentId),
                requestId);
        return result >= 0;
    }

    static String key(long courseId, long offeringId) {
        return "campus:enrollment:reservation:{" + courseId + "}:offering:" + offeringId;
    }

    private Long execute(DefaultRedisScript<Long> script, String key, String... arguments) {
        try {
            Long result = redisTemplate.execute(script, List.of(key), (Object[]) arguments);
            if (result == null) {
                throw new EnrollmentDependencyException("Redis returned an empty reservation result");
            }
            return result;
        } catch (DataAccessException exception) {
            throw new EnrollmentDependencyException("Redis reservation store is unavailable", exception);
        }
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
