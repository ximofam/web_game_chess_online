package com.ximofam.graduation_project;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    // @ServiceConnection wires spring.datasource.* automatically
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16")
                        .asCompatibleSubstituteFor("postgres"));
    }

    // No redis testcontainers module in deps — use GenericContainer + DynamicPropertyRegistrar
    @Bean
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
    }

    @Bean
    DynamicPropertyRegistrar redisProperties(GenericContainer<?> redisContainer) {
        // ponytail: overrides spring.data.redis.url so Lettuce connects to the container.
        // The main app.yaml default (redis://:123456@localhost:6379) is superseded by this registrar.
        return registry -> registry.add("spring.data.redis.url",
                () -> "redis://" + redisContainer.getHost() + ":" + redisContainer.getMappedPort(6379));
    }

    // @ServiceConnection wires spring.rabbitmq.* automatically
    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitMQContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));
    }
}
