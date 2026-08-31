package com.campus.secondhand.trading;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class TradingServiceApplicationTest {
    @Test void applicationNameIsStable() {
        assertThat(TradingServiceApplication.class.getSimpleName()).isEqualTo("TradingServiceApplication");
    }
}
