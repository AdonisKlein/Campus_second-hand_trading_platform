package com.campus.secondhand.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
class InternalAccountController {
    private final AccountService accounts;
    private final UserRepository users;

    InternalAccountController(AccountService accounts, UserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    @PostMapping("/auth/authenticate")
    ApiResponse<AuthenticatedAccount> authenticate(@Valid @RequestBody LoginRequest request) {
        User user = accounts.authenticate(request.email().trim().toLowerCase(Locale.ROOT), request.password());
        return ApiResponse.ok(AuthenticatedAccount.from(user));
    }

    @GetMapping("/users/{id}/security-state")
    ResponseEntity<ApiResponse<SecurityState>> securityState(@PathVariable Long id) {
        return users.findById(id)
                .map(user -> ResponseEntity.ok(ApiResponse.ok(SecurityState.from(user))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.fail("用户不存在")));
    }
}

record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

record AuthenticatedAccount(Long userId, String email, String username, String nickname, String phone,
                            String role, String status, String campusRegion, Integer creditScore,
                            LocalDateTime lastActiveAt, Integer authVersion) {
    static AuthenticatedAccount from(User user) {
        return new AuthenticatedAccount(user.getId(), user.getEmail(), user.getUsername(), user.getNickname(),
                user.getPhone(), user.getRole(), user.getStatus(), user.getCampusRegion(), user.getCreditScore(),
                user.getLastActiveAt(), user.getAuthVersion());
    }
}

record SecurityState(Long userId, String status, String role, Integer authVersion) {
    static SecurityState from(User user) {
        return new SecurityState(user.getId(), user.getStatus(), user.getRole(), user.getAuthVersion());
    }
}
