package com.campusenroll.enrollmentworker.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
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
            @Qualifier("enrollmentQueue") Queue enrollmentQueue,
            @Qualifier("enrollmentExchange") DirectExchange enrollmentExchange,
            EnrollmentMessagingProperties properties) {
        return BindingBuilder.bind(enrollmentQueue)
                .to(enrollmentExchange)
                .with(properties.routingKey());
    }

    @Bean
    DirectExchange enrollmentRetryExchange(EnrollmentMessagingProperties properties) {
        return new DirectExchange(properties.retryExchange(), true, false);
    }

    @Bean
    Queue enrollmentRetryQueue(EnrollmentMessagingProperties properties) {
        return QueueBuilder.durable(properties.retryQueue())
                .ttl(Math.toIntExact(properties.retryDelay().toMillis()))
                .deadLetterExchange(properties.exchange())
                .deadLetterRoutingKey(properties.routingKey())
                .build();
    }

    @Bean
    Binding enrollmentRetryBinding(
            @Qualifier("enrollmentRetryQueue") Queue retryQueue,
            @Qualifier("enrollmentRetryExchange") DirectExchange retryExchange,
            EnrollmentMessagingProperties properties) {
        return BindingBuilder.bind(retryQueue)
                .to(retryExchange)
                .with(properties.retryRoutingKey());
    }

    @Bean
    DirectExchange enrollmentDeadLetterExchange(EnrollmentMessagingProperties properties) {
        return new DirectExchange(properties.deadLetterExchange(), true, false);
    }

    @Bean
    Queue enrollmentDeadLetterQueue(EnrollmentMessagingProperties properties) {
        return QueueBuilder.durable(properties.deadLetterQueue()).build();
    }

    @Bean
    Binding enrollmentDeadLetterBinding(
            @Qualifier("enrollmentDeadLetterQueue") Queue deadLetterQueue,
            @Qualifier("enrollmentDeadLetterExchange") DirectExchange deadLetterExchange,
            EnrollmentMessagingProperties properties) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(properties.deadLetterRoutingKey());
    }
}
