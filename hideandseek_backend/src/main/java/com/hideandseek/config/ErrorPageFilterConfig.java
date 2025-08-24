package com.hideandseek.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Disable automatic registration of Spring Boot's ErrorPageFilter when running
 * inside an external servlet container (WildFly) to avoid duplicate filter
 * registration errors. This configuration is only active if
 * org.springframework.boot.web.servlet.error.ErrorPageFilter is present.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.boot.web.servlet.error.ErrorPageFilter")
public class ErrorPageFilterConfig {

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public FilterRegistrationBean<?> disableErrorPageFilter(ObjectProvider<Object> errorPageFilterProvider) {
        Object errorPageFilter = errorPageFilterProvider.getIfAvailable();
        if (errorPageFilter == null) {
            return null;
        }
        FilterRegistrationBean registration = new FilterRegistrationBean();
        try {
            java.lang.reflect.Method setFilter = FilterRegistrationBean.class.getMethod("setFilter", Object.class);
            setFilter.invoke(registration, errorPageFilter);
        } catch (Exception e) {
            // Fall back: ignore if we can't set the filter - worst case the bean won't be disabled
        }
        registration.setEnabled(false);
        return registration;
    }
}
