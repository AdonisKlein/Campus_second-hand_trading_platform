package com.campus.secondhand.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService accounts;
    private final UserRepository users;

    public AccountController(AccountService accounts, UserRepository users) {
        this.accounts = accounts;
        this.users = users;
    }

    @PostMapping("/auth/verification/{purpose}")
    public ResponseEntity<ApiResponse<String>> sendVerificationCode(
            @PathVariable String purpose, @Valid @RequestBody EmailRequest request) {
        String email = normalizeEmail(request.email());
        String verificationPurpose = switch (purpose) {
            case "register" -> "REGISTER";
            case "reset-password" -> "RESET_PASSWORD";
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        };
        if ("RESET_PASSWORD".equals(verificationPurpose) && !users.existsByEmail(email)) {
            return ResponseEntity.accepted()
                    .body(ApiResponse.ok("如果该邮箱已注册，我们已发送验证码"));
        }
        accounts.sendCode(email, verificationPurpose);
        String message = "REGISTER".equals(verificationPurpose)
                ? "如果该邮箱可以使用，我们已发送验证码"
                : "如果该邮箱已注册，我们已发送验证码";
        return ResponseEntity.accepted().body(ApiResponse.ok(message));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<UserView>> register(@Valid @RequestBody RegisterRequest request) {
        User user = accounts.register(normalizeEmail(request.email()), request.username(), request.nickname(),
                request.password(), request.code());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(UserView.from(user)));
    }

    @PostMapping("/auth/password/reset")
    public ApiResponse<String> resetPassword(@Valid @RequestBody ResetRequest request) {
        accounts.reset(normalizeEmail(request.email()), request.code(), request.newPassword());
        return ApiResponse.ok("密码重置成功");
    }

    @GetMapping("/users/me")
    public ApiResponse<UserView> me(Authentication authentication) {
        return ApiResponse.ok(UserView.from(currentUser(authentication)));
    }

    @PutMapping("/users/me")
    public ApiResponse<UserView> updateProfile(Authentication authentication,
                                               @Valid @RequestBody UpdateRequest request) {
        User user = currentUser(authentication);
        user.setNickname(request.nickname());
        user.setPhone(request.phone());
        if (request.campusRegion() != null && !request.campusRegion().isBlank()) {
            user.setCampusRegion(request.campusRegion());
        }
        return ApiResponse.ok(UserView.from(users.save(user)));
    }

    @GetMapping("/admin/users")
    public ApiResponse<List<UserView>> listStudents() {
        return ApiResponse.ok(users.findAll().stream()
                .filter(user -> !"ADMIN".equals(user.getRole()))
                .map(UserView::from)
                .toList());
    }

    @PutMapping("/admin/users/{id}/status")
    public ApiResponse<UserView> updateStatus(@PathVariable Long id,
                                              @Valid @RequestBody StatusRequest request) {
        User user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if ("ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能修改管理员账号");
        }
        user.setStatus(request.status());
        user.setAuthVersion(user.getAuthVersion() + 1);
        return ApiResponse.ok(UserView.from(users.save(user)));
    }

    private User currentUser(Authentication authentication) {
        try {
            return users.findById(Long.valueOf(authentication.getName()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效"));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record EmailRequest(@NotBlank @Email String email) {}

    public record RegisterRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,12}$") String password,
            @Size(max = 80) String nickname,
            @NotBlank @Email String email,
            @NotBlank String code) {}

    public record ResetRequest(
            @NotBlank @Email String email,
            @NotBlank String code,
            @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,12}$") String newPassword) {}

    public record UpdateRequest(
            @Size(max = 80) String nickname,
            @Size(max = 30) String phone,
            @Pattern(regexp = "^(学院路校区|沙河校区|大运村|其他校内区域)$") String campusRegion) {}

    public record StatusRequest(@NotBlank @Pattern(regexp = "^(ACTIVE|DISABLED)$") String status) {}

    public record UserView(Long id, String username, String nickname, String phone, String email,
                           String role, String status, String campusRegion, Integer creditScore,
                           LocalDateTime lastActiveAt) {
        static UserView from(User user) {
            return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getPhone(),
                    user.getEmail(), user.getRole(), user.getStatus(), user.getCampusRegion(),
                    user.getCreditScore(), user.getLastActiveAt());
        }
    }
}
