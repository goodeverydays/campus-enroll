package com.campusenroll.enrollmentworker.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.campusenroll.enrollmentworker.service.EnrollmentWorkerService;
import com.campusenroll.enrollmentworker.support.WorkerDependencyException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class EnrollmentTaskListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final EnrollmentWorkerService workerService = mock(EnrollmentWorkerService.class);
    private final ReliableRabbitPublisher rabbitPublisher = mock(ReliableRabbitPublisher.class);
    private final Channel channel = mock(Channel.class);
    private final EnrollmentMessagingProperties properties = properties();
    private EnrollmentTaskListener listener;

    @BeforeEach
    void setUp() {
        listener = new EnrollmentTaskListener(
                workerService, objectMapper, rabbitPublisher, properties);
    }

    @Test
    void TestSuccessfulJsonMessageIsAcknowledgedAfterProcessing() throws Exception {
        EnrollmentTask expected = task();
        Message message = message(expected, 1);

        listener.consume(message, channel);

        verify(workerService).process(expected);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void TestTransientFailureRoutesConfirmedRetryBeforeAcknowledging() throws Exception {
        Message message = message(task(), 1);
        doThrow(new WorkerDependencyException("course unavailable"))
                .when(workerService).process(task());

        listener.consume(message, channel);

        ArgumentCaptor<Message> routed = ArgumentCaptor.forClass(Message.class);
        verify(rabbitPublisher).send(
                eq("campus.enrollment.retry.exchange"),
                eq("campus.enrollment.retry"),
                routed.capture());
        assertThat(routed.getValue().getMessageProperties().getHeader("x-enrollment-attempt"))
                .isEqualTo(2);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void TestExhaustedFailureRoutesDeadLetterAndFinalizesRequest() throws Exception {
        Message message = message(task(), 3);
        doThrow(new IllegalStateException("database unavailable"))
                .when(workerService).process(task());

        listener.consume(message, channel);

        verify(rabbitPublisher).send(
                eq("campus.enrollment.dlx"),
                eq("campus.enrollment.dead"),
                any(Message.class));
        verify(workerService).failAfterRetries(
                task(),
                "Enrollment worker retry attempts exhausted",
                3,
                "IllegalStateException");
        verify(channel).basicAck(7L, false);
    }

    @Test
    void TestRetryPublishFailureRequeuesOriginalMessage() throws Exception {
        Message message = message(task(), 1);
        doThrow(new IllegalStateException("database unavailable"))
                .when(workerService).process(task());
        doThrow(new WorkerDependencyException("rabbit unavailable"))
                .when(rabbitPublisher).send(
                        eq("campus.enrollment.retry.exchange"),
                        eq("campus.enrollment.retry"),
                        any(Message.class));

        listener.consume(message, channel);

        verify(channel).basicNack(7L, false, true);
    }

    @Test
    void TestInvalidJsonRoutesDirectlyToDeadLetterQueue() throws Exception {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(7L);
        messageProperties.setMessageId("invalid-message");
        Message message = new Message("{".getBytes(StandardCharsets.UTF_8), messageProperties);

        listener.consume(message, channel);

        verify(rabbitPublisher).send(
                eq("campus.enrollment.dlx"),
                eq("campus.enrollment.dead"),
                any(Message.class));
        verify(channel).basicAck(7L, false);
    }

    private Message message(EnrollmentTask task, int attempt) throws Exception {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(7L);
        messageProperties.setMessageId(task.requestId());
        messageProperties.setHeader("x-enrollment-attempt", attempt);
        return new Message(objectMapper.writeValueAsBytes(task), messageProperties);
    }

    private static EnrollmentTask task() {
        return new EnrollmentTask(
                "request-1",
                1L,
                20L,
                10L,
                30L,
                List.of(new EnrollmentTaskSchedule(1, 1, 2, 1, 16)),
                Instant.EPOCH);
    }

    private static EnrollmentMessagingProperties properties() {
        return new EnrollmentMessagingProperties(
                "campus.enrollment.exchange",
                "campus.enrollment.queue",
                "campus.enrollment.requested",
                "campus.enrollment.retry.exchange",
                "campus.enrollment.retry.queue",
                "campus.enrollment.retry",
                "campus.enrollment.dlx",
                "campus.enrollment.dlq",
                "campus.enrollment.dead",
                Duration.ofSeconds(2),
                3,
                Duration.ofSeconds(1));
    }
}
