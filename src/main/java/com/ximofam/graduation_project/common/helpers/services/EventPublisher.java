package com.ximofam.graduation_project.common.helpers.services;

import com.ximofam.graduation_project.configs.properties.RabbitMQProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbit;
    private final RabbitMQProperties rabbitMQProperties;

    public void publish(String routingKey, Object payload) {
        rabbit.convertAndSend(rabbitMQProperties.getExchange().getMain(), routingKey, payload);
    }
}
