package com.ingestion.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimitProperties uploadRateLimitProperties(Environment environment) {
        return bind(environment, "app.ratelimit.uploads");
    }

    @Bean
    public RateLimitProperties authRateLimitProperties(Environment environment) {
        return bind(environment, "app.ratelimit.auth");
    }

    private RateLimitProperties bind(Environment environment, String prefix) {
        return Binder.get(environment)
                .bind(prefix, RateLimitProperties.class)
                .orElseThrow(() -> new IllegalStateException("Missing required config: " + prefix + ".*"));
    }
}
