package com.campus.secondhand.account;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class InfrastructureConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
