package com.campusenroll.enrollmentservice.messaging;

import com.campusenroll.enrollmentservice.support.EnrollmentDependencyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class RabbitEnrollmentPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final EnrollmentMessagingProperties properties;
    private final ObjectMapper objectMapper;
    private final EnrollmentPublisherMetrics metrics;

    public RabbitEnrollmentPublisher(
            RabbitTemplate rabbitTemplate,
            EnrollmentMessagingProperties properties,
            ObjectMapper objectMapper,
            EnrollmentPublisherMetrics metrics) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public void publish(EnrollmentTask task) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(task);
            var message = MessageBuilder.withBody(body)
                    .setMessageId(task.requestId())
                    .setContentType(MediaType.APPLICATION_JSON_VALUE)
                    .setContentEncoding(java.nio.charset.StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setHeader("x-enrollment-attempt", 1)
                    .build();
            CorrelationData correlation = new CorrelationData(task.requestId());
            rabbitTemplate.send(properties.exchange(), properties.routingKey(), message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                metrics.record("nack");
                throw new EnrollmentDependencyException(
                        "RabbitMQ rejected the enrollment message: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                metrics.record("returned");
                throw new EnrollmentDependencyException(
                        "RabbitMQ returned the unroutable enrollment message");
            }
            metrics.record("confirmed");
        } catch (JsonProcessingException exception) {
            metrics.record("serialization_error");
            throw new EnrollmentDependencyException(
                    "Enrollment task could not be serialized", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            metrics.record("interrupted");
            throw new EnrollmentDependencyException(
                    "RabbitMQ enrollment confirmation was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            metrics.record("confirm_failure");
            throw new EnrollmentDependencyException(
                    "RabbitMQ enrollment confirmation was not received", exception);
        } catch (AmqpException exception) {
            metrics.record("broker_error");
            throw new EnrollmentDependencyException(
                    "RabbitMQ enrollment queue is unavailable", exception);
        }
    }
}
