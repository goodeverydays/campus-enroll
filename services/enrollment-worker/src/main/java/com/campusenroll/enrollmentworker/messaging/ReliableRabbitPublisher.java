package com.campusenroll.enrollmentworker.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

import com.campusenroll.enrollmentworker.support.WorkerDependencyException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReliableRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final EnrollmentMessagingProperties properties;

    public ReliableRabbitPublisher(
            RabbitTemplate rabbitTemplate,
            EnrollmentMessagingProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void send(String exchange, String routingKey, Message message) {
        String correlationId = message.getMessageProperties().getMessageId()
                + ":" + UUID.randomUUID();
        CorrelationData correlation = new CorrelationData(correlationId);
        try {
            rabbitTemplate.send(exchange, routingKey, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new WorkerDependencyException(
                        "RabbitMQ rejected a reliability message: " + confirm.reason());
            }
            if (correlation.getReturned() != null) {
                throw new WorkerDependencyException("RabbitMQ returned an unroutable reliability message");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WorkerDependencyException("RabbitMQ confirmation was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new WorkerDependencyException("RabbitMQ confirmation was not received", exception);
        } catch (AmqpException exception) {
            throw new WorkerDependencyException("RabbitMQ reliability route is unavailable", exception);
        }
    }
}
