package com.campusenroll.enrollmentworker.messaging;

import com.campusenroll.enrollmentworker.service.EnrollmentWorkerService;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentTaskListener {

    private final EnrollmentWorkerService workerService;
    private final ObjectMapper objectMapper;

    public EnrollmentTaskListener(EnrollmentWorkerService workerService, ObjectMapper objectMapper) {
        this.workerService = workerService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${campus.messaging.queue}")
    public void consume(Message message) {
        try {
            workerService.process(objectMapper.readValue(message.getBody(), EnrollmentTask.class));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Enrollment task JSON is invalid", exception);
        }
    }
}
