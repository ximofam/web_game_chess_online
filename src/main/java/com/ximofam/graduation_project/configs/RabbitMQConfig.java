package com.ximofam.graduation_project.configs;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "app.exchange";

    public static final String POST_QUEUE = "post.queue";
    public static final String COMMENT_QUEUE = "comment.queue";
    public static final String NOTIFICATION_QUEUE = "notification.queue";

    public static final String POST_RK = "post.#";
    public static final String COMMENT_RK = "comment.#";
    public static final String NOTIFICATION_RK = "notification.#";

    @Bean
    TopicExchange exchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    Queue postQueue() {
        return QueueBuilder.durable(POST_QUEUE).build();
    }

    @Bean
    Queue commentQueue() {
        return QueueBuilder.durable(COMMENT_QUEUE).build();
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    Binding postBinding(TopicExchange exchange) {
        return BindingBuilder.bind(postQueue()).to(exchange).with(POST_RK);
    }

    @Bean
    Binding commentBinding(TopicExchange exchange) {
        return BindingBuilder.bind(commentQueue()).to(exchange).with(COMMENT_RK);
    }

    @Bean
    Binding notificationBinding(TopicExchange exchange) {
        return BindingBuilder.bind(notificationQueue()).to(exchange).with(NOTIFICATION_RK);
    }

    @Bean
    MessageConverter jsonConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}