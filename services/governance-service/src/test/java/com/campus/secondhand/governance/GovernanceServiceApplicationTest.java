package com.campus.secondhand.governance;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class GovernanceServiceApplicationTest {
    @Test void applicationNameIsStable() {
        assertThat(GovernanceServiceApplication.class.getSimpleName()).isEqualTo("GovernanceServiceApplication");
    }
}
