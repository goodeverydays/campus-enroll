package com.campusenroll.enrollmentservice.client;

import com.campusenroll.enrollmentservice.config.InternalClientProperties;
import com.campusenroll.enrollmentservice.support.EnrollmentBusinessException;
import com.campusenroll.enrollmentservice.support.EnrollmentDependencyException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class StudentEligibilityClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public StudentEligibilityClient(
            InternalClientProperties properties,
            ObjectMapper objectMapper,
            ClientHttpRequestFactory requestFactory) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.studentBaseUrl())
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
    }

    public void requireEligible(long studentId) {
        try {
            RemoteApiResponse<StudentEligibility> response = restClient.get()
                    .uri("/internal/v1/students/{studentId}/enrollment-eligibility", studentId)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() { });
            if (response == null || response.data() == null) {
                throw new EnrollmentDependencyException("Student Service returned an empty response");
            }
            if (!response.data().eligible()) {
                throw new EnrollmentBusinessException(
                        40916,
                        "Student is not eligible for enrollment: " + response.data().reasonCode());
            }
        } catch (RestClientResponseException exception) {
            throw translate(exception, "Student Service rejected the eligibility request");
        } catch (RestClientException exception) {
            throw new EnrollmentDependencyException("Student Service is unavailable", exception);
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

    private record StudentEligibility(long studentId, boolean eligible, String reasonCode) {
    }
}
