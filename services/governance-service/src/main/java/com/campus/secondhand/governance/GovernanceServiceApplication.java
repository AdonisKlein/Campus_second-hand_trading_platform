package com.campus.secondhand.governance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GovernanceProperties.class)
public class GovernanceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GovernanceServiceApplication.class, args);
    }

    @Bean Clock clock() { return Clock.systemDefaultZone(); }
    @Bean WebClient.Builder webClientBuilder() { return WebClient.builder(); }
}
