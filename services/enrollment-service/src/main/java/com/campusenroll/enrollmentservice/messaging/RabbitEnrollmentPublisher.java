package com.campusenroll.enrollmentservice.messaging;

import com.campusenroll.enrollmentservice.support.EnrollmentDependencyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
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
                    .setContentType(MediaType.APPLICATION_JSON_VALUE)
                    .setContentEncoding(java.nio.charset.StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();
            rabbitTemplate.send(properties.exchange(), properties.routingKey(), message);
        } catch (JsonProcessingException exception) {
            throw new EnrollmentDependencyException(
                    "Enrollment task could not be serialized", exception);
        } catch (AmqpException exception) {
            throw new EnrollmentDependencyException(
                    "RabbitMQ enrollment queue is unavailable", exception);
        }
    }
}
