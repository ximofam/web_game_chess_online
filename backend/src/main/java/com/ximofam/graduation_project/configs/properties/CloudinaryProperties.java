package com.ximofam.graduation_project.configs.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.cloudinary")
@Getter
@Setter
public class CloudinaryProperties {
    private String cloudName;
    private String apiKey;
    private String apiSecret;
}