package com.campus.secondhand.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiGatewayApplicationTest {
    @Test
    void applicationNameIsStable() {
        assertThat(ApiGatewayApplication.class.getSimpleName()).isEqualTo("ApiGatewayApplication");
    }
}
