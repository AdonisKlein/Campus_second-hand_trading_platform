package com.campus.secondhand.user;

import com.campus.secondhand.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.campus.secondhand.security.ClientIpResolver;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository users;
    private final VerificationService verification;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authenticator;
    private final VerificationRateLimiter rateLimiter;
    private final RegistrationService registration;
    private final ClientIpResolver clientIps;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public AuthController(UserRepository users, VerificationService verification, PasswordEncoder passwords,
                          AuthenticationManager authenticator, VerificationRateLimiter rateLimiter,
                          RegistrationService registration, ClientIpResolver clientIps) {
        this.users = users;
        this.verification = verification;
        this.passwords = passwords;
        this.authenticator = authenticator;
        this.rateLimiter = rateLimiter;
        this.registration = registration;
        this.clientIps = clientIps;
    }

    @GetMapping("/csrf")
    public ApiResponse<String> csrf(CsrfToken token) { return ApiResponse.ok(token.getToken()); }

    @PostMapping("/verification/register")
    public ResponseEntity<ApiResponse<String>> sendRegisterCode(@Valid @RequestBody EmailRequest request,
                                                                 HttpServletRequest servletRequest) {
        String email = normalize(request.email());
        rateLimiter.check(email, VerificationPurpose.REGISTER, clientIps.resolve(servletRequest));
        verification.sendCode(email, VerificationPurpose.REGISTER);
        return ResponseEntity.accepted().body(ApiResponse.ok("如果该邮箱可以使用，我们已发送验证码"));
    }

    @PostMapping("/verification/reset-password")
    public ResponseEntity<ApiResponse<String>> sendResetCode(@Valid @RequestBody EmailRequest request,
                                                              HttpServletRequest servletRequest) {
        String email = normalize(request.email());
        rateLimiter.check(email, VerificationPurpose.RESET_PASSWORD, clientIps.resolve(servletRequest));
        if (users.existsByEmail(email)) verification.sendCode(email, VerificationPurpose.RESET_PASSWORD);
        return ResponseEntity.accepted().body(ApiResponse.ok("如果该邮箱已注册，我们已发送验证码"));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserController.UserView>> register(@Valid @RequestBody RegisterRequest request) {
        String email = normalize(request.email());
        try {
            User user = registration.register(email, request.username(), request.nickname(), request.password(), request.code());
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(UserController.UserView.from(user)));
        } catch (InvalidVerificationCodeException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("验证码错误、已过期或尝试次数过多"));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("邮箱或用户名已被使用"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserController.UserView>> login(@Valid @RequestBody LoginRequest request,
                                                                       HttpServletRequest servletRequest,
                                                                       HttpServletResponse servletResponse) {
        try {
            Authentication auth = authenticator.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(normalize(request.email()), request.password()));
            servletRequest.getSession(true);
            servletRequest.changeSessionId();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, servletRequest, servletResponse);
            User current = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            current.setLastActiveAt(LocalDateTime.now());
            current = users.save(current);
            servletRequest.getSession(false).setAttribute("AUTH_VERSION", current.getAuthVersion());
            return ResponseEntity.ok(ApiResponse.ok(UserController.UserView.from(current)));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("邮箱或密码错误"));
        }
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        return ApiResponse.ok("已退出登录");
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<String>> reset(@Valid @RequestBody ResetPasswordRequest request) {
        String email = normalize(request.email());
        if (!verification.verifyCode(email, VerificationPurpose.RESET_PASSWORD, request.code())) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("验证码错误、已过期或尝试次数过多"));
        }
        return users.findByEmailIgnoreCase(email).map(user -> {
            user.setPasswordHash(passwords.encode(request.newPassword()));
            user.setAuthVersion(user.getAuthVersion() + 1);
            users.save(user);
            return ResponseEntity.ok(ApiResponse.ok("密码重置成功"));
        }).orElseGet(() -> ResponseEntity.badRequest().body(ApiResponse.fail("无法重置密码")));
    }

    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    public record EmailRequest(@NotBlank @Email String email) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RegisterRequest(@NotBlank String username,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,12}$") String password,
        String nickname, @NotBlank @Email String email, @NotBlank String code) {}
    public record ResetPasswordRequest(@NotBlank @Email String email, @NotBlank String code,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,12}$") String newPassword) {}
}
