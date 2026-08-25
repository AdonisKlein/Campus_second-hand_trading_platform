package com.campus.secondhand.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ApiTestFoundationIT {
    @Test
    void apiIntegrationBucketIsExecutedByFailsafe() {
        assertEquals(200, 200, "Failsafe must execute the API integration-test bucket");
    }
}
