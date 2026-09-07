package com.campusenroll.enrollmentworker.service;

import java.util.List;

import com.campusenroll.enrollmentworker.support.WorkerDependencyException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisReservationCompensator {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> releaseScript;

    public RedisReservationCompensator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setLocation(new ClassPathResource("redis/release-enrollment.lua"));
        this.releaseScript.setResultType(Long.class);
    }

    public boolean release(long courseId, long offeringId, long studentId) {
        try {
            Long result = redisTemplate.execute(
                    releaseScript,
                    List.of(key(courseId, offeringId)),
                    Long.toString(studentId));
            if (result == null) {
                throw new WorkerDependencyException("Redis returned an empty compensation result");
            }
            return result >= 0;
        } catch (DataAccessException exception) {
            throw new WorkerDependencyException("Redis reservation store is unavailable", exception);
        }
    }

    static String key(long courseId, long offeringId) {
        return "campus:enrollment:reservation:{" + courseId + "}:offering:" + offeringId;
    }
}
