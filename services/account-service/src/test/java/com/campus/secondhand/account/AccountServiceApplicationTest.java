package com.campus.secondhand.account;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class AccountServiceApplicationTest {
    @Test void applicationNameIsStable() {
        assertThat(AccountServiceApplication.class.getSimpleName()).isEqualTo("AccountServiceApplication");
    }
}
