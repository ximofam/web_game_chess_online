package com.ximofam.graduation_project.configs.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "app.rabbitmq")
@Validated
public class RabbitMQProperties {
    @Valid
    private Exchange exchange = new Exchange();

    @Valid
    private Queues queues = new Queues();

    private Retry retry = new Retry();

    @Setter
    @Getter
    public static class Exchange {
        @NotBlank
        private String main;
        @NotBlank
        private String dlx;
    }


    @Setter
    @Getter
    public static class Queues {
        @Valid
        private QueueConfig post = new QueueConfig();
        @Valid
        private QueueConfig comment = new QueueConfig();
        @Valid
        private QueueConfig notification = new QueueConfig();

    }

    @Setter
    @Getter
    public static class QueueConfig {
        @NotBlank
        private String name;
        @NotBlank
        private String dlq;
        @NotBlank
        private String routingKey;
        @NotBlank
        private String dlqRoutingKey;

    }

    @Setter
    @Getter
    public static class Retry {
        private int maxAttempts = 3;
        private long initialInterval = 1000L;
        private double multiplier = 2.0;
        private long maxInterval = 10000L;

    }
}
