package com.campusenroll.enrollmentworker.messaging;

import com.campusenroll.enrollmentworker.service.EnrollmentWorkerService;
import java.io.IOException;

import com.rabbitmq.client.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentTaskListener {

    private final EnrollmentWorkerService workerService;
    private final ObjectMapper objectMapper;
    private final ReliableRabbitPublisher rabbitPublisher;
    private final EnrollmentMessagingProperties properties;

    public EnrollmentTaskListener(
            EnrollmentWorkerService workerService,
            ObjectMapper objectMapper,
            ReliableRabbitPublisher rabbitPublisher,
            EnrollmentMessagingProperties properties) {
        this.workerService = workerService;
        this.objectMapper = objectMapper;
        this.rabbitPublisher = rabbitPublisher;
        this.properties = properties;
    }

    @RabbitListener(queues = "${campus.messaging.queue}")
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        EnrollmentTask task;
        try {
            task = objectMapper.readValue(message.getBody(), EnrollmentTask.class);
        } catch (IOException exception) {
            routeOrRequeue(
                    message,
                    properties.deadLetterExchange(),
                    properties.deadLetterRoutingKey(),
                    attempt(message),
                    "INVALID_JSON",
                    deliveryTag,
                    channel);
            return;
        }

        try {
            workerService.process(task);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            int currentAttempt = attempt(message);
            if (currentAttempt < properties.maxAttempts()) {
                routeOrRequeue(
                        message,
                        properties.retryExchange(),
                        properties.retryRoutingKey(),
                        currentAttempt + 1,
                        exception.getClass().getSimpleName(),
                        deliveryTag,
                        channel);
                return;
            }
            try {
                rabbitPublisher.send(
                        properties.deadLetterExchange(),
                        properties.deadLetterRoutingKey(),
                        routedMessage(
                                message,
                                currentAttempt,
                                exception.getClass().getSimpleName()));
                workerService.failAfterRetries(
                        task,
                        "Enrollment worker retry attempts exhausted",
                        currentAttempt,
                        exception.getClass().getSimpleName());
                channel.basicAck(deliveryTag, false);
            } catch (RuntimeException finalizationFailure) {
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }

    private void routeOrRequeue(
            Message original,
            String exchange,
            String routingKey,
            int nextAttempt,
            String failureType,
            long deliveryTag,
            Channel channel) throws IOException {
        try {
            rabbitPublisher.send(
                    exchange,
                    routingKey,
                    routedMessage(original, nextAttempt, failureType));
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException publishFailure) {
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private static Message routedMessage(Message original, int attempt, String failureType) {
        return MessageBuilder.fromClonedMessage(original)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setHeader("x-enrollment-attempt", attempt)
                .setHeader("x-enrollment-failure", failureType)
                .build();
    }

    private static int attempt(Message message) {
        Object value = message.getMessageProperties().getHeader("x-enrollment-attempt");
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        return 1;
    }
}
