package com.ximofam.graduation_project.configs.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.thread-pool")
@Getter
@Setter
public class ThreadPoolProperties {

    private Async async = new Async();
    private Scheduler scheduler = new Scheduler();

    @Getter
    @Setter
    public static class Async {
        private int coreSize = 5;
        private int maxSize = 20;
        private int queueCapacity = 100;
        private int awaitTerminationSeconds = 30;
    }

    @Getter
    @Setter
    public static class Scheduler {
        private int poolSize = 5;
        private int awaitTerminationSeconds = 30;
    }
}