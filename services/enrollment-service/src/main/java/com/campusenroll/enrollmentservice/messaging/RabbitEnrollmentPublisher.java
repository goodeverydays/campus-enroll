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

    public RabbitEnrollmentPublisher(
            RabbitTemplate rabbitTemplate,
            EnrollmentMessagingProperties properties,
            ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
            if (!confirm.ack()) {
                throw new EnrollmentDependencyException(
                        "RabbitMQ rejected the enrollment message: " + confirm.reason());
            }
            if (correlation.getReturned() != null) {
                throw new EnrollmentDependencyException(
                        "RabbitMQ returned the unroutable enrollment message");
            }
        } catch (JsonProcessingException exception) {
            throw new EnrollmentDependencyException(
                    "Enrollment task could not be serialized", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EnrollmentDependencyException(
                    "RabbitMQ enrollment confirmation was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new EnrollmentDependencyException(
                    "RabbitMQ enrollment confirmation was not received", exception);
        } catch (AmqpException exception) {
            throw new EnrollmentDependencyException(
                    "RabbitMQ enrollment queue is unavailable", exception);
        }
    }
}
