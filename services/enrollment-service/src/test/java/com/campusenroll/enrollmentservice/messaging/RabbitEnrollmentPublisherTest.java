package com.campusenroll.enrollmentservice.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.campusenroll.enrollmentservice.support.EnrollmentDependencyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.ArgumentCaptor;

class RabbitEnrollmentPublisherTest {

    private final RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
    private final EnrollmentMessagingProperties properties = new EnrollmentMessagingProperties(
            "campus.enrollment.exchange",
            "campus.enrollment.queue",
            "campus.enrollment.requested",
            Duration.ofSeconds(1));
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final EnrollmentPublisherMetrics metrics = new EnrollmentPublisherMetrics(meterRegistry);
    private final RabbitEnrollmentPublisher publisher =
            new RabbitEnrollmentPublisher(rabbitTemplate, properties, objectMapper, metrics);

    @Test
    void TestPublishEnrollmentTaskUsesConfiguredRoute() {
        EnrollmentTask task = task();
        confirmNextPublish(true, null);
        publisher.publish(task);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).send(
                org.mockito.ArgumentMatchers.eq("campus.enrollment.exchange"),
                org.mockito.ArgumentMatchers.eq("campus.enrollment.requested"),
                messageCaptor.capture(),
                correlationCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(messageCaptor.getValue().getMessageProperties().getContentType())
                .isEqualTo("application/json");
        org.assertj.core.api.Assertions.assertThat(
                        new String(messageCaptor.getValue().getBody(), StandardCharsets.UTF_8))
                .contains("\"requestId\":\"request-1\"")
                .doesNotContain("__TypeId__");
        org.assertj.core.api.Assertions.assertThat(messageCaptor.getValue().getMessageProperties().getMessageId())
                .isEqualTo("request-1");
        org.assertj.core.api.Assertions.assertThat(correlationCaptor.getValue().getId())
                .isEqualTo("request-1");
        org.assertj.core.api.Assertions.assertThat(counter("confirmed")).isEqualTo(1.0);
    }

    @Test
    void TestSerializationFailureBecomesDependencyFailure() throws Exception {
        EnrollmentTask task = task();
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        doThrow(new JsonProcessingException("serialization failed") { })
                .when(failingObjectMapper)
                .writeValueAsBytes(task);
        RabbitEnrollmentPublisher failingPublisher =
                new RabbitEnrollmentPublisher(rabbitTemplate, properties, failingObjectMapper, metrics);

        assertThatThrownBy(() -> failingPublisher.publish(task))
                .isInstanceOf(EnrollmentDependencyException.class)
                .hasMessage("Enrollment task could not be serialized");
        org.assertj.core.api.Assertions.assertThat(counter("serialization_error")).isEqualTo(1.0);
    }

    @Test
    void TestRabbitFailureBecomesDependencyFailure() {
        EnrollmentTask task = task();
        doThrow(new AmqpException("connection refused"))
                .when(rabbitTemplate)
                .send(
                        org.mockito.ArgumentMatchers.eq("campus.enrollment.exchange"),
                        org.mockito.ArgumentMatchers.eq("campus.enrollment.requested"),
                        org.mockito.ArgumentMatchers.any(Message.class),
                        org.mockito.ArgumentMatchers.any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(task))
                .isInstanceOf(EnrollmentDependencyException.class)
                .hasMessage("RabbitMQ enrollment queue is unavailable");
        org.assertj.core.api.Assertions.assertThat(counter("broker_error")).isEqualTo(1.0);
    }

    @Test
    void TestBrokerNackBecomesDependencyFailure() {
        confirmNextPublish(false, "exchange rejected message");

        assertThatThrownBy(() -> publisher.publish(task()))
                .isInstanceOf(EnrollmentDependencyException.class)
                .hasMessageContaining("RabbitMQ rejected the enrollment message");
        org.assertj.core.api.Assertions.assertThat(counter("nack")).isEqualTo(1.0);
    }

    private void confirmNextPublish(boolean acknowledged, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(acknowledged, reason));
            return null;
        }).when(rabbitTemplate).send(
                org.mockito.ArgumentMatchers.eq("campus.enrollment.exchange"),
                org.mockito.ArgumentMatchers.eq("campus.enrollment.requested"),
                org.mockito.ArgumentMatchers.any(Message.class),
                org.mockito.ArgumentMatchers.any(CorrelationData.class));
    }

    private static EnrollmentTask task() {
        return new EnrollmentTask("request-1", 1L, 20L, 10L, 30L, List.of(), Instant.EPOCH);
    }

    private double counter(String outcome) {
        return meterRegistry.get(EnrollmentPublisherMetrics.METRIC_NAME)
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}
