package com.campus.secondhand.mysql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MysqlTestFoundationIT {
    @Test
    void mysqlIntegrationBucketIsExecutedByFailsafe() {
        assertTrue("mysql".startsWith("my"));
    }
}
