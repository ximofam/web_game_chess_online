package com.ximofam.graduation_project.configs;

import com.ximofam.graduation_project.configs.properties.RabbitMQProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private final RabbitMQProperties props;

    @Bean
    TopicExchange mainExchange() {
        return ExchangeBuilder
                .topicExchange(props.getExchange().getMain())
                .durable(true)
                .build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder
                .directExchange(props.getExchange().getDlx())
                .durable(true)
                .build();
    }

    // ==================== MAIN QUEUES ====================

    @Bean
    Queue postQueue() {
        return buildQueueWithDlx(props.getQueues().getPost());
    }

    @Bean
    Queue commentQueue() {
        return buildQueueWithDlx(props.getQueues().getComment());
    }

    @Bean
    Queue notificationQueue() {
        return buildQueueWithDlx(props.getQueues().getNotification());
    }

    // ==================== DEAD LETTER QUEUES ====================

    @Bean
    Queue postDeadQueue() {
        return buildDeadQueue(props.getQueues().getPost());
    }

    @Bean
    Queue commentDeadQueue() {
        return buildDeadQueue(props.getQueues().getComment());
    }

    @Bean
    Queue notificationDeadQueue() {
        return buildDeadQueue(props.getQueues().getNotification());
    }

    // ==================== MAIN BINDINGS ====================

    @Bean
    Binding postBinding(Queue postQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(postQueue).to(mainExchange)
                .with(props.getQueues().getPost().getRoutingKey());
    }

    @Bean
    Binding commentBinding(Queue commentQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(commentQueue).to(mainExchange)
                .with(props.getQueues().getComment().getRoutingKey());
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(notificationQueue).to(mainExchange)
                .with(props.getQueues().getNotification().getRoutingKey());
    }

    // ==================== DLQ BINDINGS ====================

    @Bean
    Binding postDeadBinding(Queue postDeadQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(postDeadQueue).to(deadLetterExchange)
                .with(props.getQueues().getPost().getDlqRoutingKey());
    }

    @Bean
    Binding commentDeadBinding(Queue commentDeadQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(commentDeadQueue).to(deadLetterExchange)
                .with(props.getQueues().getComment().getDlqRoutingKey());
    }

    @Bean
    Binding notificationDeadBinding(Queue notificationDeadQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationDeadQueue).to(deadLetterExchange)
                .with(props.getQueues().getNotification().getDlqRoutingKey());
    }

    // ==================== INFRASTRUCTURE ====================

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                  MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    @Bean
    StatelessRetryOperationsInterceptor retryInterceptor() {
        RabbitMQProperties.Retry retry = props.getRetry();
        return RetryInterceptorBuilder.stateless()
                .maxRetries(retry.getMaxAttempts())
                .backOffOptions(
                        retry.getInitialInterval(),
                        retry.getMultiplier(),
                        retry.getMaxInterval()
                )
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    private Queue buildQueueWithDlx(RabbitMQProperties.QueueConfig config) {
        return QueueBuilder.durable(config.getName())
                .withArgument("x-dead-letter-exchange", props.getExchange().getDlx())
                .withArgument("x-dead-letter-routing-key", config.getDlqRoutingKey())
                .build();
    }

    private Queue buildDeadQueue(RabbitMQProperties.QueueConfig config) {
        return QueueBuilder.durable(config.getDlq()).build();
    }
}
