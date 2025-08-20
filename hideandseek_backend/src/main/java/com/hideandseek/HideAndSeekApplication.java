package com.hideandseek;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {ErrorMvcAutoConfiguration.class})
@EnableScheduling
public class HideAndSeekApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(HideAndSeekApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        // Configure for WAR deployment to WildFly
        // Exclude error handling to prevent filter registration conflicts
        return application.sources(HideAndSeekApplication.class)
                .properties("spring.profiles.active=wildfly");
    }
}
