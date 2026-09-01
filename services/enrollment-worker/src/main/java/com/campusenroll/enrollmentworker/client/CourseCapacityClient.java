package com.campusenroll.enrollmentworker.client;

import com.campusenroll.enrollmentworker.config.WorkerClientProperties;
import com.campusenroll.enrollmentworker.support.WorkerBusinessException;
import com.campusenroll.enrollmentworker.support.WorkerDependencyException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CourseCapacityClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CourseCapacityClient(
            WorkerClientProperties properties,
            ObjectMapper objectMapper,
            ClientHttpRequestFactory workerClientRequestFactory) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.courseBaseUrl())
                .requestFactory(workerClientRequestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    public void reserve(long offeringId) {
        mutate(HttpMethod.POST, offeringId);
    }

    public void release(long offeringId) {
        mutate(HttpMethod.DELETE, offeringId);
    }

    private void mutate(HttpMethod method, long offeringId) {
        try {
            restClient.method(method)
                    .uri("/internal/v1/course-offerings/{offeringId}/capacity-reservations", offeringId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw new WorkerDependencyException("Course Service is unavailable", exception);
        }
    }

    private RuntimeException translate(RestClientResponseException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            int code = body.path("code").asInt(50300);
            String message = body.path("message").asText("Course Service rejected the capacity mutation");
            if (code == 40400 || code >= 40900 && code < 41000) {
                return new WorkerBusinessException(code, message);
            }
        } catch (Exception ignored) {
            // Use the dependency-safe fallback below.
        }
        return new WorkerDependencyException("Course Service rejected the capacity mutation", exception);
    }
}
