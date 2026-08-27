package com.campus.secondhand.gateway;

import reactor.core.publisher.Mono;

public interface AccountClient {
    Mono<AuthenticatedAccount> authenticate(LoginCredentials credentials);

    Mono<AccountSecurityState> securityState(Long userId);
}
