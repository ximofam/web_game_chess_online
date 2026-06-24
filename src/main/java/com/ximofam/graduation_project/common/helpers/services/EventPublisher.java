package com.ximofam.graduation_project.common.helpers.services;

import com.ximofam.graduation_project.configs.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final RabbitTemplate rabbit;

    public void publish(String routingKey, Object payload) {
        rabbit.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, payload);
    }
}