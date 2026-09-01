package com.campusenroll.enrollmentworker.messaging;

import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import com.campusenroll.enrollmentworker.service.EnrollmentWorkerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class EnrollmentTaskListenerTest {

    @Test
    void TestJsonMessageUsesLocalContractWithoutJavaTypeHeader() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EnrollmentWorkerService workerService = org.mockito.Mockito.mock(EnrollmentWorkerService.class);
        EnrollmentTaskListener listener = new EnrollmentTaskListener(workerService, objectMapper);
        EnrollmentTask expected = new EnrollmentTask(
                "request-1",
                1L,
                20L,
                10L,
                30L,
                List.of(new EnrollmentTaskSchedule(1, 1, 2, 1, 16)),
                Instant.EPOCH);
        Message message = new Message(
                objectMapper.writeValueAsString(expected).getBytes(StandardCharsets.UTF_8),
                new MessageProperties());

        listener.consume(message);

        verify(workerService).process(expected);
    }
}
