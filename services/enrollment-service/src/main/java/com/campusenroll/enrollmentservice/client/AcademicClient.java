package com.campusenroll.enrollmentservice.client;

import java.util.List;

import com.campusenroll.enrollmentservice.config.InternalClientProperties;
import com.campusenroll.enrollmentservice.support.EnrollmentBusinessException;
import com.campusenroll.enrollmentservice.support.EnrollmentDependencyException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AcademicClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AcademicClient(
            InternalClientProperties properties,
            ObjectMapper objectMapper,
            ClientHttpRequestFactory requestFactory) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.courseBaseUrl())
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    public EnrollmentCandidate findCandidate(long courseId) {
        try {
            RemoteApiResponse<List<RemoteOffering>> response = restClient.get()
                    .uri("/api/v1/courses/{courseId}/offerings", courseId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            List<RemoteOffering> offerings = response == null || response.data() == null
                    ? List.of()
                    : response.data();
            RemoteOffering candidate = offerings.stream()
                    .filter(offering -> "OPEN".equals(offering.status()))
                    .filter(offering -> offering.remainingCount() > 0)
                    .findFirst()
                    .orElseThrow(() -> offerings.stream().anyMatch(offering -> "OPEN".equals(offering.status()))
                            ? new EnrollmentBusinessException(40911, "All open course offerings are full")
                            : new EnrollmentBusinessException(40912, "Course is not open for enrollment"));
            return findDetail(courseId, candidate);
        } catch (RestClientResponseException exception) {
            throw translate(exception, "Course Service rejected the catalog request");
        } catch (RestClientException exception) {
            throw new EnrollmentDependencyException("Course Service is unavailable", exception);
        }
    }

    public EnrollmentCandidate findOffering(long courseId, long offeringId) {
        try {
            RemoteOffering placeholder = new RemoteOffering(
                    offeringId, courseId, 0, 0, 0, 0, "OPEN");
            return findDetail(courseId, placeholder);
        } catch (RestClientResponseException exception) {
            throw translate(exception, "Course Service rejected the offering request");
        } catch (RestClientException exception) {
            throw new EnrollmentDependencyException("Course Service is unavailable", exception);
        }
    }

    public void reserve(long offeringId) {
        mutateCapacity(org.springframework.http.HttpMethod.POST, offeringId, null);
    }

    public void reserve(long offeringId, String enrollmentRequestId) {
        mutateCapacity(org.springframework.http.HttpMethod.POST, offeringId, enrollmentRequestId);
    }

    public void release(long offeringId) {
        mutateCapacity(org.springframework.http.HttpMethod.DELETE, offeringId, null);
    }

    public void release(long offeringId, String enrollmentRequestId) {
        mutateCapacity(org.springframework.http.HttpMethod.DELETE, offeringId, enrollmentRequestId);
    }

    private EnrollmentCandidate findDetail(long courseId, RemoteOffering candidate) {
        RemoteApiResponse<RemoteOfferingDetail> detailResponse = restClient.get()
                .uri("/api/v1/course-offerings/{offeringId}", candidate.id())
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        if (detailResponse == null || detailResponse.data() == null
                || detailResponse.data().offering() == null) {
            throw new EnrollmentDependencyException("Course Service returned an empty offering detail");
        }
        RemoteOfferingDetail detail = detailResponse.data();
        if (detail.offering().courseId() != courseId) {
            throw new EnrollmentDependencyException("Course Service returned an inconsistent offering");
        }
        List<CourseSchedule> schedules = detail.schedules() == null
                ? List.of()
                : detail.schedules().stream()
                        .map(schedule -> new CourseSchedule(
                                schedule.dayOfWeek(),
                                schedule.startSection(),
                                schedule.endSection(),
                                schedule.startWeek(),
                                schedule.endWeek()))
                        .toList();
        return new EnrollmentCandidate(
                courseId,
                detail.offering().id(),
                detail.offering().semesterId(),
                detail.offering().remainingCount(),
                schedules);
    }

    private void mutateCapacity(
            org.springframework.http.HttpMethod method,
            long offeringId,
            String enrollmentRequestId) {
        try {
            RestClient.RequestBodySpec request = restClient.method(method)
                    .uri("/internal/v1/course-offerings/{offeringId}/capacity-reservations", offeringId);
            if (enrollmentRequestId != null && !enrollmentRequestId.isBlank()) {
                request.header("X-Enrollment-Request-Id", enrollmentRequestId);
            }
            request.retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw translate(exception, "Course Service rejected the capacity mutation");
        } catch (RestClientException exception) {
            throw new EnrollmentDependencyException("Course Service is unavailable", exception);
        }
    }

    private RuntimeException translate(RestClientResponseException exception, String fallbackMessage) {
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            int code = body.path("code").asInt(50300);
            String message = body.path("message").asText(fallbackMessage);
            if (code == 40400 || code >= 40900 && code < 41000) {
                return new EnrollmentBusinessException(code, message);
            }
        } catch (Exception ignored) {
            // Use the dependency-safe fallback below.
        }
        return new EnrollmentDependencyException(fallbackMessage, exception);
    }

    private record RemoteOffering(
            long id,
            long courseId,
            long semesterId,
            int capacity,
            int selectedCount,
            int remainingCount,
            String status) {
    }

    private record RemoteOfferingDetail(
            RemoteOffering offering,
            List<RemoteSchedule> schedules) {
    }

    private record RemoteSchedule(
            long id,
            int dayOfWeek,
            int startSection,
            int endSection,
            String location,
            int startWeek,
            int endWeek) {
    }
}
