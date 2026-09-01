package com.campusenroll.enrollmentservice.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.campusenroll.enrollmentservice.support.EnrollmentDependencyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.ArgumentCaptor;

class RabbitEnrollmentPublisherTest {

    private final RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
    private final EnrollmentMessagingProperties properties = new EnrollmentMessagingProperties(
            "campus.enrollment.exchange", "campus.enrollment.queue", "campus.enrollment.requested");
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final RabbitEnrollmentPublisher publisher =
            new RabbitEnrollmentPublisher(rabbitTemplate, properties, objectMapper);

    @Test
    void TestPublishEnrollmentTaskUsesConfiguredRoute() {
        EnrollmentTask task = task();
        publisher.publish(task);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                org.mockito.ArgumentMatchers.eq("campus.enrollment.exchange"),
                org.mockito.ArgumentMatchers.eq("campus.enrollment.requested"),
                messageCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(messageCaptor.getValue().getMessageProperties().getContentType())
                .isEqualTo("application/json");
        org.assertj.core.api.Assertions.assertThat(
                        new String(messageCaptor.getValue().getBody(), StandardCharsets.UTF_8))
                .contains("\"requestId\":\"request-1\"")
                .doesNotContain("__TypeId__");
    }

    @Test
    void TestSerializationFailureBecomesDependencyFailure() throws Exception {
        EnrollmentTask task = task();
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        doThrow(new JsonProcessingException("serialization failed") { })
                .when(failingObjectMapper)
                .writeValueAsBytes(task);
        RabbitEnrollmentPublisher failingPublisher =
                new RabbitEnrollmentPublisher(rabbitTemplate, properties, failingObjectMapper);

        assertThatThrownBy(() -> failingPublisher.publish(task))
                .isInstanceOf(EnrollmentDependencyException.class)
                .hasMessage("Enrollment task could not be serialized");
    }

    @Test
    void TestRabbitFailureBecomesDependencyFailure() {
        EnrollmentTask task = task();
        doThrow(new AmqpException("connection refused"))
                .when(rabbitTemplate)
                .send(
                        org.mockito.ArgumentMatchers.eq("campus.enrollment.exchange"),
                        org.mockito.ArgumentMatchers.eq("campus.enrollment.requested"),
                        org.mockito.ArgumentMatchers.any(Message.class));

        assertThatThrownBy(() -> publisher.publish(task))
                .isInstanceOf(EnrollmentDependencyException.class)
                .hasMessage("RabbitMQ enrollment queue is unavailable");
    }

    private static EnrollmentTask task() {
        return new EnrollmentTask("request-1", 1L, 20L, 10L, 30L, List.of(), Instant.EPOCH);
    }
}
