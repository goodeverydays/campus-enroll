package com.campusenroll.enrollmentworker.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.campusenroll.enrollmentworker.support.WorkerDependencyException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ReliableRabbitPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ReliableRabbitPublisher publisher =
            new ReliableRabbitPublisher(rabbitTemplate, properties());

    @Test
    void TestBrokerAckCompletesReliabilityTransfer() {
        completeNextConfirm(true, null);

        publisher.send("retry.exchange", "retry.key", message());
    }

    @Test
    void TestBrokerNackFailsReliabilityTransfer() {
        completeNextConfirm(false, "rejected");

        assertThatThrownBy(() -> publisher.send("retry.exchange", "retry.key", message()))
                .isInstanceOf(WorkerDependencyException.class)
                .hasMessageContaining("RabbitMQ rejected");
    }

    private void completeNextConfirm(boolean acknowledged, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(acknowledged, reason));
            return null;
        }).when(rabbitTemplate).send(
                eq("retry.exchange"),
                eq("retry.key"),
                any(Message.class),
                any(CorrelationData.class));
    }

    private static Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("request-1");
        return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
    }

    private static EnrollmentMessagingProperties properties() {
        return new EnrollmentMessagingProperties(
                "main.exchange", "main.queue", "main.key",
                "retry.exchange", "retry.queue", "retry.key",
                "dead.exchange", "dead.queue", "dead.key",
                Duration.ofSeconds(2), 3, Duration.ofSeconds(1));
    }
}
