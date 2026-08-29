package com.vacaro.benchlab.service.messaging;

import com.vacaro.benchlab.config.BenchLabProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RunEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RunEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final BenchLabProperties properties;

    public RunEventPublisher(RabbitTemplate rabbitTemplate, BenchLabProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(RunRequestedEvent event) {
        rabbitTemplate.convertAndSend(properties.getQueue().getExchange(), properties.getQueue().getRunRequestedRoutingKey(), event);
        log.info("Job encolado para ejecución. jobId={}, traceId={}", event.jobId(), event.traceId());
    }
}
