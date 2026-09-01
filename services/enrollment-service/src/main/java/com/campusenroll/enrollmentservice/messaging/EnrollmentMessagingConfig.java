package com.campusenroll.enrollmentservice.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EnrollmentMessagingProperties.class)
public class EnrollmentMessagingConfig {

    @Bean
    DirectExchange enrollmentExchange(EnrollmentMessagingProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    Queue enrollmentQueue(EnrollmentMessagingProperties properties) {
        return QueueBuilder.durable(properties.queue()).build();
    }

    @Bean
    Binding enrollmentBinding(
            Queue enrollmentQueue,
            DirectExchange enrollmentExchange,
            EnrollmentMessagingProperties properties) {
        return BindingBuilder.bind(enrollmentQueue)
                .to(enrollmentExchange)
                .with(properties.routingKey());
    }
}
