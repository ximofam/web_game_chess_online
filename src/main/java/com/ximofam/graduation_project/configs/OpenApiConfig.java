package com.ximofam.graduation_project.configs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Chess Online")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Ximofam")
                                .email("vienpham177@gmail.com")));
    }
}