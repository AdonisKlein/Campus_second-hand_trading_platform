package com.campus.secondhand.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRouteConfigurationTest {
    @Autowired
    private RouteLocator routes;

    @Test
    void allBusinessServicesHaveAnEffectiveGatewayRoute() {
        Set<String> routeIds = routes.getRoutes().map(route -> route.getId()).collectList().blockOptional()
                .orElseThrow()
                .stream()
                .collect(java.util.stream.Collectors.toSet());

        assertThat(routeIds).containsExactlyInAnyOrder("account", "marketplace", "trading", "governance");
    }
}
