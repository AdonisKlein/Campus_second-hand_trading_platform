package com.campus.secondhand.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class MarketplaceServiceApplicationTest {
    @Test void applicationNameIsStable() {
        assertThat(MarketplaceServiceApplication.class.getSimpleName()).isEqualTo("MarketplaceServiceApplication");
    }
}
