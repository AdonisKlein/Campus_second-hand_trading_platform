package com.campus.secondhand.trading;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RabbitTopologyIT {
    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4.2-management-alpine");

    @DynamicPropertySource
    static void rabbit(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("campus.trading.messaging-enabled", () -> "true");
        registry.add("campus.trading.outbox-publish-ms", () -> "600000");
    }

    @Autowired RabbitTemplate rabbit;

    @Test
    void applicationDeclaresDurableCommandAndResultQueuesOnRealBroker() {
        assertThat(queueExists(TradingRabbitConfiguration.COMMAND_QUEUE)).isTrue();
        assertThat(queueExists(TradingRabbitConfiguration.RESULT_QUEUE)).isTrue();
    }

    private boolean queueExists(String queue) {
        return Boolean.TRUE.equals(rabbit.execute(channel -> channel.queueDeclarePassive(queue) != null));
    }
}
