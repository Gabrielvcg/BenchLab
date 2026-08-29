package com.vacaro.benchlab.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfiguration {

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    DirectExchange benchLabExchange(BenchLabProperties properties) {
        return new DirectExchange(properties.getQueue().getExchange(), true, false);
    }

    @Bean
    Queue runRequestedQueue(BenchLabProperties properties) {
        return new Queue(properties.getQueue().getRunRequestedQueue(), true);
    }

    @Bean
    Binding runRequestedBinding(BenchLabProperties properties, Queue runRequestedQueue, DirectExchange benchLabExchange) {
        return BindingBuilder.bind(runRequestedQueue).to(benchLabExchange).with(properties.getQueue().getRunRequestedRoutingKey());
    }
}
