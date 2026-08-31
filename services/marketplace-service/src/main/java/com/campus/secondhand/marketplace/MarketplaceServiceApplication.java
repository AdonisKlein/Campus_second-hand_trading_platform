package com.campus.secondhand.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
@EnableConfigurationProperties(MarketplaceProperties.class)
@EnableScheduling
public class MarketplaceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketplaceServiceApplication.class, args);
    }

    @Bean
    WebClient.Builder webClientBuilder() {
        return WebClient.builder().filter(CorrelationIdFilter.propagate());
    }
}
