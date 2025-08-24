package com.hideandseek.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // Map specific admin pages located under src/main/resources/webapp
    registry.addResourceHandler("/admin-login.html")
        .addResourceLocations("classpath:/webapp/");
    registry.addResourceHandler("/admin-dashboard.html")
        .addResourceLocations("classpath:/webapp/");
    registry.addResourceHandler("/admin-game.html")
        .addResourceLocations("classpath:/webapp/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
    // Map /admin to admin login
    registry.addViewController("/admin").setViewName("forward:/admin-login.html");
    }
}
